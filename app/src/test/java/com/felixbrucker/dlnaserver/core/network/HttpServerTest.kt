package com.felixbrucker.dlnaserver.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

@OptIn(ExperimentalCoroutinesApi::class)
class HttpServerTest {

    private lateinit var server: HttpServer
    private var testPort: Int = 0
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val ephemeralSocket = ServerSocket(0)
        testPort = ephemeralSocket.localPort
        ephemeralSocket.close()
        server = HttpServer()
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.stop()
        }
    }

    private fun createConnectedSocket(port: Int): Socket {
        var socket: Socket? = null
        var attempts = 0
        while (attempts < 30) {
            try {
                socket = Socket("127.0.0.1", port)
                break
            } catch (_: Exception) {
                Thread.sleep(50)
                attempts++
            }
        }
        return socket ?: Socket("127.0.0.1", port)
    }

    @Test
    fun testHttpGetRouteReturns200AndText() = runTest {
        server.get("/test") { call ->
            call.respondText("hello world", contentType = "text/plain")
        }
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        writer.write("GET /test HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nConnection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertTrue(sb.toString().contains("hello world"))
    }

    @Test
    fun testHttpPostRouteParsesBodyAndReturnsResponse() = runTest {
        server.post("/echo") { call ->
            call.respondText("Received: ${call.request.body}", contentType = "text/plain")
        }
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        val bodyText = "Sample body"
        writer.write("POST /echo HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: ${bodyText.length}\r\nConnection: close\r\n\r\n$bodyText")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertTrue(sb.toString().contains("Received: Sample body"))
    }

    @Test
    fun testHttpHeadRouteReturnsHeadersWithoutBody() = runTest {
        server.head("/head") { call ->
            call.respondStatus(200)
        }
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        writer.write("HEAD /head HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nConnection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        val headers = mutableListOf<String>()
        while (true) {
            val l = reader.readLine() ?: break
            if (l.isEmpty()) break
            headers.add(l)
        }
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertTrue(headers.any { it.startsWith("Content-Length: 0") })
    }

    @Test
    fun testHttpQueryParamsParsed() = runTest {
        server.get("/search") { call ->
            val q = call.request.queryParameters["q"] ?: ""
            call.respondText("Query: $q", contentType = "text/plain")
        }
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        writer.write("GET /search?q=kotlin%20server HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nConnection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertTrue(sb.toString().contains("Query: kotlin server"))
    }

    @Test
    fun testHttp404ForUnmatchedRoute() = runTest {
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        writer.write("GET /notfound HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nConnection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("404 Not Found"))
    }

    @Test
    fun testHttpStreamResponse() = runTest {
        server.get("/stream") { call ->
            val data = "streamed data".toByteArray(Charsets.UTF_8)
            call.respondStream(contentType = "application/octet-stream", contentLength = data.size.toLong()) { out ->
                out.write(data)
            }
        }
        server.start("127.0.0.1", testPort, testScope)
        val socket = createConnectedSocket(testPort)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        writer.write("GET /stream HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nConnection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertTrue(sb.toString().contains("streamed data"))
    }
}
