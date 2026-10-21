package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.ConnectedClientInfo
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private var ktorEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var cleanUpJob: Job? = null

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

        Timber.i("Starting Ktor HttpMediaServer on %s:%d", ipAddress, port)

        cleanUpJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                val now = System.currentTimeMillis()
                clientAccessMap.entries.removeIf { now - it.value.lastAccessTimestamp > 300_000 }
                _connectedClients.value = clientAccessMap.values.toList()
                delay(10.seconds)
            }
        }

        try {
            ktorEngine = embeddedServer(CIO, host = ipAddress, port = port) {
                configureRouting(ipAddress)
            }.start(wait = false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Ktor HttpMediaServer")
            stop()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        Timber.i("Stopping Ktor HttpMediaServer")

        try {
            ktorEngine?.stop(1000, 2000)
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Ktor engine")
        }
        ktorEngine = null
        cleanUpJob?.cancel()
        cleanUpJob = null
        clientAccessMap.clear()
        _connectedClients.value = emptyList()
    }

    private fun Application.configureRouting(hostIp: String) {
        routing {
            get("/description.xml") {
                val clientIp = call.request.origin.remoteHost
                val userAgent = call.request.header("User-Agent") ?: "Unknown"
                updateClientActivity(clientIp, userAgent, "Browsing Description")
                val xml = ContentDirectoryXml.generateDeviceDescription()
                call.respondText(xml, ContentType.Text.Xml)
            }

            get("/scpd/ContentDirectory.xml") {
                val clientIp = call.request.origin.remoteHost
                val userAgent = call.request.header("User-Agent") ?: "Unknown"
                updateClientActivity(clientIp, userAgent, "Browsing ContentDirectory SCPD")
                call.respondText(ContentDirectoryXml.getContentDirectoryScpd(), ContentType.Text.Xml)
            }

            get("/scpd/ConnectionManager.xml") {
                val clientIp = call.request.origin.remoteHost
                val userAgent = call.request.header("User-Agent") ?: "Unknown"
                updateClientActivity(clientIp, userAgent, "Browsing ConnectionManager SCPD")
                call.respondText(ContentDirectoryXml.getConnectionManagerScpd(), ContentType.Text.Xml)
            }

            post("/ctl/ContentDirectory") {
                val clientIp = call.request.origin.remoteHost
                val userAgent = call.request.header("User-Agent") ?: "Unknown"
                val body = call.receiveText()
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
                val baseUrl = "http://$hostIp:$port"
                val responseXml = handleContentDirectoryControl(body, baseUrl)
                call.respondText(responseXml, ContentType.Text.Xml)
            }

            post("/ctl/ConnectionManager") {
                val clientIp = call.request.origin.remoteHost
                val userAgent = call.request.header("User-Agent") ?: "Unknown"
                updateClientActivity(clientIp, userAgent, "Connection Manager")
                call.respondText(handleConnectionManagerControl(), ContentType.Text.Xml)
            }

            get("/media") {
                handleMediaRequest(this.call, isHeadOnly = false)
            }

            head("/media") {
                handleMediaRequest(this.call, isHeadOnly = true)
            }
        }
    }

    private suspend fun handleMediaRequest(call: io.ktor.server.application.ApplicationCall, isHeadOnly: Boolean) {
        val clientIp = call.request.origin.remoteHost
        val userAgent = call.request.header("User-Agent") ?: "Unknown"
        val pathParam = call.request.queryParameters["path"]

        if (pathParam.isNullOrEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val filePath = ContentDirectoryXml.decodePath(pathParam)
        val file = File(filePath)

        if (!file.exists() || !file.isFile) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val sharedDirs = getSharedDirectories()
        val targetCanonical = file.canonicalPath
        val isAllowed = sharedDirs.any { shared ->
            val sharedCanonical = File(shared.path).canonicalPath
            targetCanonical == sharedCanonical || targetCanonical.startsWith(sharedCanonical + File.separator)
        }

        if (!isAllowed) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        updateClientActivity(clientIp, userAgent, "Streaming: ${file.name}")
        serveRawFile(call, file, isHeadOnly, clientIp, userAgent)
    }

    private suspend fun serveRawFile(
        call: io.ktor.server.application.ApplicationCall,
        file: File,
        isHeadOnly: Boolean,
        clientIp: String,
        userAgent: String
    ) {
        val fileLength = file.length()
        val mimeType = MimeTypeUtils.getMimeType(file)
        val rangeHeader = call.request.header("Range")

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
        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK

        call.response.header("Accept-Ranges", "bytes")
        call.response.header("Connection", "keep-alive")
        call.response.header("transferMode.dlna.org", "Streaming")
        call.response.header("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")

        if (isPartial) {
            call.response.header("Content-Range", "bytes $start-$end/$fileLength")
        }

        if (isHeadOnly) {
            call.respond(status)
            return
        }

        val contentType = ContentType.parse(mimeType)

        call.respondBytesWriter(
            contentType = contentType,
            status = status,
            contentLength = contentLength
        ) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var bytesToRead = contentLength
                var lastTimeUpdate = System.currentTimeMillis()

                while (bytesToRead > 0) {
                    val readLength = minOf(buffer.size.toLong(), bytesToRead).toInt()
                    val read = raf.read(buffer, 0, readLength)
                    if (read <= 0) break
                    writeFully(buffer, 0, read)
                    bytesToRead -= read

                    val now = System.currentTimeMillis()
                    if (now - lastTimeUpdate >= 5000) {
                        updateClientActivity(clientIp, userAgent, "Streaming: ${file.name}")
                        lastTimeUpdate = now
                    }
                }
            }
            updateClientActivity(clientIp, userAgent, "Streaming: ${file.name}")
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
