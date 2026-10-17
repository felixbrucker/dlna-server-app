package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class HttpMediaServer(
    private val port: Int = 8192,
    private val getSharedDirectories: () -> List<SharedDirectory>
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val executor = Executors.newCachedThreadPool()

    private val clientAccessTimes = ConcurrentHashMap<String, Long>()
    private val _connectedClients = MutableStateFlow<Set<String>>(emptySet())
    val connectedClients: StateFlow<Set<String>> = _connectedClients.asStateFlow()

    @Volatile
    private var isRunning = false

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
                        clientAccessTimes.entries.removeIf { now - it.value > 300_000 }
                        _connectedClients.value = clientAccessTimes.keys.toSet()
                        kotlinx.coroutines.delay(10_000)
                    }
                }

                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }
                    executor.execute {
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
        clientAccessTimes.clear()
        _connectedClients.value = emptySet()
    }

    private fun handleClient(socket: Socket, hostIp: String) {
        try {
            val clientIp = socket.inetAddress?.hostAddress
            if (clientIp != null) {
                clientAccessTimes[clientIp] = System.currentTimeMillis()
                _connectedClients.value = clientAccessTimes.keys.toSet()
            }

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
                    val xml = ContentDirectoryXml.generateDeviceDescription(hostIp, port)
                    sendXmlResponse(outputStream, xml)
                }
                uri.startsWith("/scpd/ContentDirectory.xml") -> {
                    sendXmlResponse(outputStream, ContentDirectoryXml.getContentDirectoryScpd())
                }
                uri.startsWith("/scpd/ConnectionManager.xml") -> {
                    sendXmlResponse(outputStream, ContentDirectoryXml.getConnectionManagerScpd())
                }
                uri.startsWith("/ctl/ContentDirectory") -> {
                    val responseXml = handleContentDirectoryControl(body, baseUrl)
                    sendXmlResponse(outputStream, responseXml)
                }
                uri.startsWith("/ctl/ConnectionManager") -> {
                    sendXmlResponse(outputStream, handleConnectionManagerControl())
                }
                uri.startsWith("/media") -> {
                    val pathParam = uri.substringAfter("path=", "")
                    if (pathParam.isNotEmpty()) {
                        val filePath = ContentDirectoryXml.decodePath(pathParam)
                        serveMediaFile(socket, outputStream, File(filePath), headers, isHeadOnly = method == "HEAD")
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
        val protocols = "http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_MP_SD;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                "http-get:*:video/x-matroska:*," +
                "http-get:*:video/mpeg:*," +
                "http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                "http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                "http-get:*:image/png:DLNA.ORG_PN=PNG_LRG;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
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
        socket: Socket,
        outputStream: OutputStream,
        file: File,
        headers: Map<String, String>,
        isHeadOnly: Boolean = false
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

        val fileLength = file.length()
        val mimeType = MimeTypeUtils.getMimeType(file)
        val rangeHeader = headers["range"]

        var start: Long = 0
        var end: Long = fileLength - 1
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val ranges = rangeHeader.removePrefix("bytes=").split("-")
            try {
                if (ranges[0].isNotEmpty()) {
                    start = ranges[0].toLong()
                }
                if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                    end = ranges[1].toLong()
                }
                if (start <= end && start < fileLength) {
                    if (end >= fileLength) end = fileLength - 1
                    isPartial = true
                }
            } catch (_: Exception) {
                start = 0
                end = fileLength - 1
                isPartial = false
            }
        }

        val contentLength = end - start + 1
        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"

        val printWriter = PrintWriter(outputStream)
        printWriter.println(statusLine)
        printWriter.println("Content-Type: $mimeType")
        printWriter.println("Content-Length: $contentLength")
        printWriter.println("Accept-Ranges: bytes")
        printWriter.println("Connection: keep-alive")
        printWriter.println("transferMode.dlna.org: Streaming")
        printWriter.println("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")

        if (isPartial) {
            printWriter.println("Content-Range: bytes $start-$end/$fileLength")
        }
        printWriter.println()
        printWriter.flush()

        if (isHeadOnly) {
            return
        }

        FileInputStream(file).use { fileStream ->
            fileStream.skip(start)
            val buffer = ByteArray(64 * 1024)
            var bytesToRead = contentLength
            while (bytesToRead > 0) {
                val readLength = minOf(buffer.size.toLong(), bytesToRead).toInt()
                val read = fileStream.read(buffer, 0, readLength)
                if (read <= 0) break
                outputStream.write(buffer, 0, read)
                outputStream.flush()
                bytesToRead -= read
            }
        }
    }

    private fun sendXmlResponse(outputStream: OutputStream, xml: String) {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        val printWriter = PrintWriter(outputStream)
        printWriter.println("HTTP/1.1 200 OK")
        printWriter.println("Content-Type: text/xml; charset=\"utf-8\"")
        printWriter.println("Content-Length: ${bytes.size}")
        printWriter.println("Connection: close")
        printWriter.println()
        printWriter.flush()
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendNotFound(outputStream: OutputStream) {
        val printWriter = PrintWriter(outputStream)
        printWriter.println("HTTP/1.1 404 Not Found")
        printWriter.println("Content-Length: 0")
        printWriter.println("Connection: close")
        printWriter.println()
        printWriter.flush()
    }

    private fun sendForbidden(outputStream: OutputStream) {
        val printWriter = PrintWriter(outputStream)
        printWriter.println("HTTP/1.1 403 Forbidden")
        printWriter.println("Content-Length: 0")
        printWriter.println("Connection: close")
        printWriter.println()
        printWriter.flush()
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
