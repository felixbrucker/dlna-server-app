package com.felixbrucker.dlnaserver.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

enum class HttpStatusCode(val code: Int, val description: String) {
    OK(200, "OK"),
    PARTIAL_CONTENT(206, "Partial Content"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found")
}

data class ContentType(
    val mimeType: String,
    val charset: String? = null
) {
    override fun toString(): String {
        return if (charset != null) "$mimeType; charset=$charset" else mimeType
    }

    object Text {
        val Xml = ContentType("text/xml", "utf-8")
    }

    companion object {
        fun parse(value: String): ContentType {
            val parts = value.split(";").map { it.trim() }
            val mimeType = parts.firstOrNull() ?: "application/octet-stream"
            var charset: String? = null
            for (i in 1 until parts.size) {
                val param = parts[i]
                if (param.lowercase().startsWith("charset=")) {
                    charset = param.substringAfter("=").trim().removeSurrounding("\"")
                }
            }
            return ContentType(mimeType, charset)
        }
    }
}

data class HttpRequest(
    val method: String,
    val path: String,
    val queryParameters: Map<String, String>,
    val headers: Map<String, String>,
    val clientIp: String,
    val body: String
) {
    fun getHeader(name: String): String? = headers[name.lowercase()]

    val userAgent: String get() = getHeader("User-Agent") ?: "Unknown"
}

class HttpResponse(
    private val outputStream: OutputStream
) {
    val headers = mutableMapOf<String, String>()

    fun header(name: String, value: String) {
        headers[name.lowercase()] = value
    }

    internal fun writeHeader(status: HttpStatusCode, extraHeaders: Map<String, String> = emptyMap()) {
        val merged = mutableMapOf<String, String>()
        for ((key, value) in extraHeaders) {
            merged[key.lowercase()] = value
        }
        for ((key, value) in headers) {
            merged[key.lowercase()] = value
        }

        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status.code).append(" ").append(status.description).append("\r\n")
        for ((key, value) in merged) {
            sb.append(key).append(": ").append(value).append("\r\n")
        }
        sb.append("\r\n")
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }
}

class HttpCall(
    val request: HttpRequest,
    private val outputStream: OutputStream
) {
    val response = HttpResponse(outputStream)

    suspend fun respond(status: HttpStatusCode) {
        withContext(Dispatchers.IO) {
            val extraHeaders = mapOf("content-length" to "0", "connection" to "close")
            response.writeHeader(status, extraHeaders)
            outputStream.flush()
        }
    }

    suspend fun respondText(
        text: String,
        contentType: ContentType = ContentType.Text.Xml,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        withContext(Dispatchers.IO) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val extraHeaders = mapOf(
                "content-type" to contentType.toString(),
                "content-length" to bytes.size.toString(),
                "connection" to "close"
            )
            response.writeHeader(status, extraHeaders)
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    suspend fun respondStream(
        contentType: ContentType,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentLength: Long,
        streamBlock: suspend (OutputStream) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val extraHeaders = mapOf(
                "content-type" to contentType.toString(),
                "content-length" to contentLength.toString()
            )
            response.writeHeader(status, extraHeaders)
            streamBlock(outputStream)
            outputStream.flush()
        }
    }
}

class HttpServer {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    @Volatile
    private var isRunning = false

    private val routes = ConcurrentHashMap<Pair<String, String>, suspend (HttpCall) -> Unit>()

    fun get(path: String, handler: suspend (HttpCall) -> Unit) {
        route("GET", path, handler)
    }

    fun post(path: String, handler: suspend (HttpCall) -> Unit) {
        route("POST", path, handler)
    }

    fun head(path: String, handler: suspend (HttpCall) -> Unit) {
        route("HEAD", path, handler)
    }

    fun route(method: String, path: String, handler: suspend (HttpCall) -> Unit) {
        routes[method.uppercase() to path] = handler
    }

    fun start(host: String, port: Int, scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        try {
            val address = InetAddress.getByName(host)
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(address, port))
            serverSocket = socket
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind HttpServer on %s:%d", host, port)
            stop()
            throw e
        }

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                    Timber.e(e, "Error accepting client connection")
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing ServerSocket")
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            socket.use { s ->
                val inputStream = s.getInputStream()
                val outputStream = s.getOutputStream()
                val clientIp = s.inetAddress?.hostAddress ?: "unknown"

                val request = parseRequest(inputStream, clientIp) ?: return
                val call = HttpCall(request, outputStream)

                val handler = routes[request.method to request.path]

                if (handler != null) {
                    handler(call)
                } else {
                    call.respond(HttpStatusCode.NOT_FOUND)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling client request")
        }
    }

    private fun parseRequest(inputStream: InputStream, clientIp: String): HttpRequest? {
        val reader = HttpInputStreamReader(inputStream)
        val requestLine = reader.readLine() ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null

        val method = parts[0].uppercase()
        val rawUri = parts[1]

        val rawPath = rawUri.substringBefore("?")
        val queryString = rawUri.substringAfter("?", missingDelimiterValue = "")

        val queryParams = mutableMapOf<String, String>()
        if (queryString.isNotEmpty()) {
            for (param in queryString.split("&")) {
                if (param.isEmpty()) continue
                val keyRaw = param.substringBefore("=")
                val valRaw = param.substringAfter("=", missingDelimiterValue = "")
                val key = try {
                    URLDecoder.decode(keyRaw, "UTF-8")
                } catch (_: Exception) {
                    keyRaw
                }
                val value = try {
                    URLDecoder.decode(valRaw, "UTF-8")
                } catch (_: Exception) {
                    valRaw
                }
                queryParams[key] = value
            }
        }

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val colonIdx = line.indexOf(":")
            if (colonIdx != -1) {
                val headerName = line.substring(0, colonIdx).trim().lowercase()
                val headerValue = line.substring(colonIdx + 1).trim()
                headers[headerName] = headerValue
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

        val body = if (contentLength > 0) {
            val bodyBytes = reader.readBytes(contentLength)
            String(bodyBytes, Charsets.UTF_8)
        } else {
            ""
        }

        return HttpRequest(
            method = method,
            path = rawPath,
            queryParameters = queryParams,
            headers = headers,
            clientIp = clientIp,
            body = body
        )
    }
}

private class HttpInputStreamReader(private val inputStream: InputStream) {
    fun readLine(): String? {
        val bytes = ByteArrayOutputStream()
        var count = 0
        while (true) {
            val b = inputStream.read()
            if (b == -1) break
            count++
            if (b == '\n'.code) break
            bytes.write(b)
        }
        if (count == 0) return null
        var line = bytes.toString(Charsets.UTF_8.name())
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length - 1)
        }
        return line
    }

    fun readBytes(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = inputStream.read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return if (totalRead == length) buffer else buffer.copyOf(totalRead)
    }
}
