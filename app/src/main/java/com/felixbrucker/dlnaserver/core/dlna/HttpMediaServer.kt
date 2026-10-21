package com.felixbrucker.dlnaserver.core.dlna

import android.util.Log
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.ConnectedClientInfo
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class HttpMediaServer @Inject constructor(
    private val sharedDirectoryRepository: SharedDirectoryRepository
) {
    private val port: Int = 8192
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val clientAccessMap = ConcurrentHashMap<String, ConnectedClientInfo>()
    private val _connectedClients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClientInfo>> = _connectedClients.asStateFlow()

    @Volatile
    private var isRunning = false

    private fun getSharedDirectories(): List<SharedDirectory> {
        return runBlocking(Dispatchers.IO) {
            sharedDirectoryRepository.getEnabledDirectoriesSync()
        }
    }

    fun start(ipAddress: String, scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.reuseAddress = true

                launch {
                    while (isRunning) {
                        val now = System.currentTimeMillis()
                        clientAccessMap.entries.removeIf { now - it.value.lastAccessTimestamp > 300_000 }
                        _connectedClients.value = clientAccessMap.values.toList()
                        kotlinx.coroutines.delay(10.seconds)
                    }
                }

                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(socket, ipAddress)
                    }
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        clientAccessMap.clear()
        _connectedClients.value = emptyList()
    }

    private fun handleClient(socket: Socket, hostIp: String) {
        try {
            val clientIp = socket.inetAddress?.hostAddress ?: return

            val input = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uri = parts[1]

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            var line: String?

            while (input.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.trim().isEmpty()) break
                val colonIdx = currentLine.indexOf(":")
                if (colonIdx != -1) {
                    val key = currentLine.substring(0, colonIdx).trim().lowercase()
                    val value = currentLine.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            val userAgent = headers["user-agent"] ?: "Unknown"

            var body = ""
            if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = input.read(charBuffer, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                body = String(charBuffer, 0, readTotal)
            }

            val baseUrl = "http://$hostIp:$port"

            when {
                uri.startsWith("/description.xml") -> {
                    updateClientActivity(clientIp, userAgent, "Browsing Description")
                    val xml = ContentDirectoryXml.generateDeviceDescription()
                    sendXmlResponse(outputStream, xml)
                }
                uri.startsWith("/scpd/ContentDirectory.xml") -> {
                    updateClientActivity(clientIp, userAgent, "Browsing ContentDirectory SCPD")
                    sendXmlResponse(outputStream, ContentDirectoryXml.getContentDirectoryScpd())
                }
                uri.startsWith("/scpd/ConnectionManager.xml") -> {
                    updateClientActivity(clientIp, userAgent, "Browsing ConnectionManager SCPD")
                    sendXmlResponse(outputStream, ContentDirectoryXml.getConnectionManagerScpd())
                }
                uri.startsWith("/ctl/ContentDirectory") -> {
                    val objectId = extractTagValue(body, "ObjectID") ?: "0"
                    val folderName = if (objectId == "0") {
                        "Root"
                    } else if (objectId.startsWith("f_")) {
                        val path = ContentDirectoryXml.decodePath(objectId.removePrefix("f_"))
                        File(path).name.ifEmpty { path }
                    } else {
                        "Folder"
                    }
                    updateClientActivity(clientIp, userAgent, "Browsing: $folderName")
                    val responseXml = handleContentDirectoryControl(body, baseUrl)
                    sendXmlResponse(outputStream, responseXml)
                }
                uri.startsWith("/ctl/ConnectionManager") -> {
                    updateClientActivity(clientIp, userAgent, "Connection Manager")
                    sendXmlResponse(outputStream, handleConnectionManagerControl())
                }
                uri.startsWith("/media") -> {
                    val pathParam = uri.substringAfter("path=", "").substringBefore("&")
                    if (pathParam.isNotEmpty()) {
                        val filePath = ContentDirectoryXml.decodePath(pathParam)
                        val mediaFile = File(filePath)
                        updateClientActivity(clientIp, userAgent, "Streaming: ${mediaFile.name}")
                        serveMediaFile(
                            outputStream = outputStream,
                            file = mediaFile,
                            headers = headers,
                            isHeadOnly = method == "HEAD",
                            clientIp = clientIp,
                            userAgent = userAgent
                        )
                    } else {
                        sendNotFound(outputStream)
                    }
                }
                else -> {
                    sendNotFound(outputStream)
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun updateClientActivity(clientIp: String, userAgent: String, activity: String) {
        clientAccessMap[clientIp] = ConnectedClientInfo(
            ipAddress = clientIp,
            userAgent = userAgent,
            currentActivity = activity,
            lastAccessTimestamp = System.currentTimeMillis()
        )
        _connectedClients.value = clientAccessMap.values.toList()
    }

    private fun handleContentDirectoryControl(body: String, baseUrl: String): String {
        val objectId = extractTagValue(body, "ObjectID") ?: "0"
        val browseFlag = extractTagValue(body, "BrowseFlag") ?: "BrowseDirectChildren"
        val startingIndex = extractTagValue(body, "StartingIndex")?.toIntOrNull() ?: 0
        val requestedCount = extractTagValue(body, "RequestedCount")?.toIntOrNull() ?: 0

        val sharedDirs = getSharedDirectories()
        return ContentDirectoryXml.buildBrowseResponse(
            objectID = objectId,
            browseFlag = browseFlag,
            startingIndex = startingIndex,
            requestedCount = requestedCount,
            sharedDirectories = sharedDirs,
            baseUrl = baseUrl
        )
    }

    private fun handleConnectionManagerControl(): String {
        val protocols = "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                "http-get:*:video/x-matroska:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                "http-get:*:video/mpeg:*," +
                "http-get:*:video/avi:*," +
                "http-get:*:video/quicktime:*," +
                "http-get:*:video/webm:*," +
                "http-get:*:audio/mpeg:*," +
                "http-get:*:audio/flac:*," +
                "http-get:*:image/jpeg:*," +
                "http-get:*:image/png:*," +
                "http-get:*:*:*"
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetProtocolInfoResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">
      <Source>$protocols</Source>
      <Sink></Sink>
    </u:GetProtocolInfoResponse>
  </s:Body>
</s:Envelope>"""
    }

    private fun serveMediaFile(
        outputStream: OutputStream,
        file: File,
        headers: Map<String, String>,
        isHeadOnly: Boolean = false,
        clientIp: String,
        userAgent: String
    ) {
        if (!file.exists() || !file.isFile) {
            sendNotFound(outputStream)
            return
        }

        val sharedDirs = getSharedDirectories()
        val targetCanonical = file.canonicalPath
        val isAllowed = sharedDirs.any { shared ->
            val sharedCanonical = File(shared.path).canonicalPath
            targetCanonical == sharedCanonical || targetCanonical.startsWith(sharedCanonical + File.separator)
        }

        if (!isAllowed) {
            sendForbidden(outputStream)
            return
        }

        serveRawFile(
            outputStream = outputStream,
            file = file,
            headers = headers,
            isHeadOnly = isHeadOnly,
            clientIp = clientIp,
            userAgent = userAgent
        )
    }

    private fun serveRawFile(
        outputStream: OutputStream,
        file: File,
        headers: Map<String, String>,
        isHeadOnly: Boolean,
        clientIp: String,
        userAgent: String
    ) {
        val fileLength = file.length()
        val mimeType = MimeTypeUtils.getMimeType(file)
        val rangeHeader = headers["range"]

        var start: Long = 0
        var end: Long = fileLength - 1
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeValue = rangeHeader.removePrefix("bytes=").trim()
            if (rangeValue.startsWith("-")) {
                val suffixLength = rangeValue.substring(1).toLongOrNull() ?: 0L
                if (suffixLength in 1..fileLength) {
                    start = maxOf(0L, fileLength - suffixLength)
                    end = fileLength - 1
                    isPartial = true
                }
            } else {
                val dashIdx = rangeValue.indexOf("-")
                if (dashIdx != -1) {
                    val startStr = rangeValue.substring(0, dashIdx).trim()
                    val endStr = rangeValue.substring(dashIdx + 1).trim()
                    if (startStr.isNotEmpty()) {
                        start = startStr.toLongOrNull() ?: 0L
                    }
                    if (endStr.isNotEmpty()) {
                        end = endStr.toLongOrNull() ?: (fileLength - 1)
                    }
                    if (start <= end && start < fileLength) {
                        if (end >= fileLength) end = fileLength - 1
                        isPartial = true
                    }
                }
            }
        }

        val contentLength = end - start + 1
        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"

        val headerBuilder = StringBuilder()
        headerBuilder.append(statusLine)
        headerBuilder.append("Content-Type: $mimeType\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Connection: keep-alive\r\n")
        headerBuilder.append("transferMode.dlna.org: Streaming\r\n")
        headerBuilder.append("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")

        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $start-$end/$fileLength\r\n")
        }
        headerBuilder.append("\r\n")

        outputStream.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()

        if (isHeadOnly) {
            return
        }

        Log.d("HttpMediaServer", "range=$rangeHeader, file=${file.name}")

        FileInputStream(file).use { fileStream ->
            if (start > 0) {
                fileStream.skip(start)
            }
            val buffer = ByteArray(64 * 1024)
            var bytesToRead = contentLength
            var lastTimeUpdate = System.currentTimeMillis()
            while (bytesToRead > 0) {
                val readLength = minOf(buffer.size.toLong(), bytesToRead).toInt()
                val read = fileStream.read(buffer, 0, readLength)
                if (read <= 0) break
                outputStream.write(buffer, 0, read)
                outputStream.flush()
                bytesToRead -= read

                val now = System.currentTimeMillis()
                if (now - lastTimeUpdate >= 5000) {
                    updateClientActivity(clientIp, userAgent, "Streaming: ${file.name}")
                    lastTimeUpdate = now
                }
            }
            updateClientActivity(clientIp, userAgent, "Streaming: ${file.name}")
        }
    }

    private fun sendXmlResponse(outputStream: OutputStream, xml: String) {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendNotFound(outputStream: OutputStream) {
        val header = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun sendForbidden(outputStream: OutputStream) {
        val header = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun extractTagValue(xml: String, tag: String): String? {
        val openTag = "<$tag"
        val closeTag = "</$tag>"
        val startIdx = xml.indexOf(openTag)
        if (startIdx == -1) return null
        val contentStart = xml.indexOf(">", startIdx)
        if (contentStart == -1) return null
        val endIdx = xml.indexOf(closeTag, contentStart)
        if (endIdx == -1) return null
        return xml.substring(contentStart + 1, endIdx).trim()
    }
}
