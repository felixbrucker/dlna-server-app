package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.ConnectedClientInfo
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.network.HttpCall
import com.felixbrucker.dlnaserver.core.network.HttpRequest
import com.felixbrucker.dlnaserver.core.network.HttpServer
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class HttpMediaServer @Inject constructor(
    private val sharedDirectoryRepository: SharedDirectoryRepository
) {
    private val port: Int = 8192
    private var httpServer: HttpServer? = null
    private var cleanUpJob: Job? = null

    private val clientAccessMap = ConcurrentHashMap<String, ConnectedClientInfo>()
    private val _connectedClients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClientInfo>> = _connectedClients.asStateFlow()

    @Volatile
    private var isRunning = false

    private suspend fun getSharedDirectories(): List<SharedDirectory> = withContext(Dispatchers.IO) {
        sharedDirectoryRepository.getEnabledDirectoriesSync()
    }

    fun start(ipAddress: String, scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        Timber.i("Starting HttpServer on %s:%d", ipAddress, port)

        try {
            val server = HttpServer()
            configureRouting(server, ipAddress)
            server.start(ipAddress, port, scope)
            httpServer = server
        } catch (e: Exception) {
            Timber.e(e, "Failed to start HttpServer")
            stop()
        }

        cleanUpJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                val now = System.currentTimeMillis()
                clientAccessMap.entries.removeIf { now - it.value.lastAccessTimestamp > 300_000 }
                _connectedClients.value = clientAccessMap.values.toList()
                delay(10.seconds)
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        Timber.i("Stopping HttpServer")

        try {
            httpServer?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping HttpServer")
        }
        httpServer = null
        cleanUpJob?.cancel()
        cleanUpJob = null
        clientAccessMap.clear()
        _connectedClients.value = emptyList()
    }

    private fun configureRouting(server: HttpServer, hostIp: String) {
        server.get("/description.xml") { call ->
            updateClientActivity(call.clientInfo, "Browsing Description")
            val xml = ContentDirectoryXml.generateDeviceDescription()
            call.respondText(xml, contentType = "text/xml")
        }

        server.get("/scpd/ContentDirectory.xml") { call ->
            updateClientActivity(call.clientInfo, "Browsing ContentDirectory SCPD")
            call.respondText(ContentDirectoryXml.getContentDirectoryScpd(), contentType = "text/xml")
        }

        server.get("/scpd/ConnectionManager.xml") { call ->
            updateClientActivity(call.clientInfo, "Browsing ConnectionManager SCPD")
            call.respondText(ContentDirectoryXml.getConnectionManagerScpd(), contentType = "text/xml")
        }

        server.post("/ctl/ContentDirectory") { call ->
            val body = call.request.body
            val objectId = extractTagValue(body, "ObjectID") ?: "0"
            val folderName = if (objectId == "0") {
                "Root"
            } else if (objectId.startsWith("f_")) {
                val path = ContentDirectoryXml.decodePath(objectId.removePrefix("f_"))
                File(path).name.ifEmpty { path }
            } else {
                "Folder"
            }
            updateClientActivity(call.clientInfo, "Browsing: $folderName")
            val baseUrl = "http://$hostIp:$port"
            val responseXml = handleContentDirectoryControl(body, baseUrl)
            call.respondText(responseXml, contentType = "text/xml")
        }

        server.post("/ctl/ConnectionManager") { call ->
            updateClientActivity(call.clientInfo, "Connection Manager")
            call.respondText(handleConnectionManagerControl(), contentType = "text/xml")
        }

        server.get("/media") { call ->
            handleMediaRequest(call, isHeadOnly = false)
        }

        server.head("/media") { call ->
            handleMediaRequest(call, isHeadOnly = true)
        }
    }

    private suspend fun handleMediaRequest(call: HttpCall, isHeadOnly: Boolean) {
        val pathParam = call.request.queryParameters["path"]

        if (pathParam.isNullOrEmpty()) {
            call.respondStatus(404)
            return
        }

        val filePath = ContentDirectoryXml.decodePath(pathParam)
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            call.respondStatus(404)
            return
        }

        val isAllowed = isAllowedToAccess(file)
        if (!isAllowed) {
            call.respondStatus(403)
            return
        }

        val fileLength = file.length()
        val effectiveRange = call.request.extractEffectiveRange(file)

        val responseHeaders = mutableMapOf<String, String>()
        responseHeaders["Accept-Ranges"] = "bytes"
        responseHeaders["Connection"] = "keep-alive"
        responseHeaders["transferMode.dlna.org"] = "Streaming"
        responseHeaders["contentFeatures.dlna.org"] = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

        if (effectiveRange.isPartial) {
            responseHeaders["Content-Range"] = "bytes ${effectiveRange.start}-${effectiveRange.end}/$fileLength"
        }

        val mediaRequestContext = MediaRequestContext(call, file, effectiveRange)
        val mimeType = MimeTypeUtils.getMimeType(file)

        if (isHeadOnly) {
            call.respondStatus(mediaRequestContext.status, headers = responseHeaders)
            return
        }

        serveRawFile(mediaRequestContext, mimeType, responseHeaders)
    }

    private suspend fun isAllowedToAccess(file: File): Boolean {
        val sharedDirs = getSharedDirectories()
        val targetCanonical = file.canonicalPath

        return sharedDirs.any { shared ->
            val sharedCanonical = File(shared.path).canonicalPath
            targetCanonical == sharedCanonical || targetCanonical.startsWith(sharedCanonical + File.separator)
        }
    }

    private suspend fun serveRawFile(
        mediaRequestContext: MediaRequestContext,
        mimeType: String,
        responseHeaders: Map<String, String>
    ) {
        val file = mediaRequestContext.file
        Timber.d(mediaRequestContext.logLine)
        updateClientActivity(mediaRequestContext.clientInfo, "Streaming: ${file.name}")

        mediaRequestContext.call.respondStream(
            contentType = mimeType,
            status = mediaRequestContext.status,
            contentLength = mediaRequestContext.effectiveRange.contentLength,
            headers = responseHeaders
        ) { outputStream ->
            withContext(Dispatchers.IO) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(mediaRequestContext.effectiveRange.start)
                    val buffer = ByteArray(64 * 1024)
                    var bytesToRead = mediaRequestContext.effectiveRange.contentLength
                    var lastTimeUpdate = System.currentTimeMillis()
                    var chunkCount = 0

                    while (bytesToRead > 0) {
                        val readLength = minOf(buffer.size.toLong(), bytesToRead).toInt()
                        val read = raf.read(buffer, 0, readLength)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                        bytesToRead -= read

                        chunkCount++
                        if (chunkCount % 16 == 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastTimeUpdate >= 5000) {
                                updateClientActivity(mediaRequestContext.clientInfo, "Streaming: ${file.name}")
                                lastTimeUpdate = now
                            }
                        }
                    }
                }
            }
            updateClientActivity(mediaRequestContext.clientInfo, "Streaming: ${file.name}")
        }
    }

    private fun updateClientActivity(clientInfo: ClientInfo, activity: String) {
        clientAccessMap[clientInfo.ipAddress] = ConnectedClientInfo(
            ipAddress = clientInfo.ipAddress,
            userAgent = clientInfo.userAgent,
            currentActivity = activity,
            lastAccessTimestamp = System.currentTimeMillis()
        )
        _connectedClients.value = clientAccessMap.values.toList()
    }

    private suspend fun handleContentDirectoryControl(body: String, baseUrl: String): String {
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

data class MediaRequestContext(
    val call: HttpCall,
    val file: File,
    val effectiveRange: EffectiveRange,
) {
    val clientInfo = call.clientInfo
    val status: Int = if (effectiveRange.isPartial) 206 else 200
    val logLine: String get() {
        val rangeHeader = call.request.getHeader("Range")
        val tags = listOf(
            "client_ip=${clientInfo.ipAddress}",
            "user_agent=${clientInfo.userAgent}",
            "range=$rangeHeader",
            "file=${file.name}"
        )

        return "Media request: ${tags.joinToString(", ")}"
    }
}

data class ClientInfo(
    val ipAddress: String,
    val userAgent: String
)

val HttpCall.clientInfo: ClientInfo
    get() = ClientInfo(request.clientIp, request.userAgent)

data class EffectiveRange(
    val start: Long,
    val end: Long,
    val isPartial: Boolean
) {
    val contentLength: Long = end - start + 1
}

fun HttpRequest.extractEffectiveRange(file: File): EffectiveRange {
    val fileLength = file.length()
    val rangeHeader = getHeader("Range")

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

    return EffectiveRange(
        start = start,
        end = end,
        isPartial = isPartial
    )
}
