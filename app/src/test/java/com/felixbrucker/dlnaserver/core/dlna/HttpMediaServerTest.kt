package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
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
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

@OptIn(ExperimentalCoroutinesApi::class)
class HttpMediaServerTest {

    private lateinit var tempFile: File
    private lateinit var sharedDir: SharedDirectory
    private lateinit var server: HttpMediaServer
    private var activeTranscodeOption: TranscodeOption = TranscodeOption.NONE
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        tempFile = File.createTempFile("test_media", ".mp4").apply {
            writeBytes(ByteArray(1024 * 100) { 0x01.toByte() })
            deleteOnExit()
        }
        sharedDir = SharedDirectory(
            id = 1,
            path = tempFile.parentFile?.absolutePath ?: tempFile.absolutePath,
            displayName = "Test"
        )
        activeTranscodeOption = TranscodeOption.NONE
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.stop()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    private fun startServerForPort(port: Int): HttpMediaServer {
        val s = HttpMediaServer(
            port = port,
            getTranscodeOption = { activeTranscodeOption },
            getSharedDirectories = { listOf(sharedDir) }
        )
        s.start("127.0.0.1", testScope)
        return s
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
    fun testClientTrackedWhenRequestingMediaFile() = runTest {
        val port = 8195
        server = startServerForPort(port)
        val socket = createConnectedSocket(port)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        val encodedPath = ContentDirectoryXml.encodePath(tempFile.absolutePath)

        writer.write("GET /media?path=$encodedPath HTTP/1.1\r\n")
        writer.write("Host: 127.0.0.1:$port\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        val statusLine = reader.readLine()
        val buffer = CharArray(1024)
        while (reader.read(buffer) != -1) {
        }
        socket.close()

        assertTrue(statusLine.contains("200 OK"))
        assertEquals(1, server.connectedClients.value.size)
    }

    @Test
    fun testTranscodedMediaFileResponse() = runTest {
        val port = 8196
        activeTranscodeOption = TranscodeOption.X264
        server = startServerForPort(port)
        val socket = createConnectedSocket(port)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        val encodedPath = ContentDirectoryXml.encodePath(tempFile.absolutePath)

        writer.write("GET /media?path=$encodedPath HTTP/1.1\r\n")
        writer.write("Host: 127.0.0.1:$port\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        val statusLine = reader.readLine() ?: ""
        var contentType = ""
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: break
            if (currentLine.isEmpty()) break
            if (currentLine.lowercase().startsWith("content-type:")) {
                contentType = currentLine
            }
        }
        socket.close()

        assertTrue(statusLine.contains("200 OK"))
        assertTrue(contentType.contains("video/x-matroska"))
    }

    @Test
    fun testTranscodedMediaFileWithSeekHeader() = runTest {
        val port = 8197
        activeTranscodeOption = TranscodeOption.X264
        server = startServerForPort(port)
        val socket = createConnectedSocket(port)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        val encodedPath = ContentDirectoryXml.encodePath(tempFile.absolutePath)

        writer.write("GET /media?path=$encodedPath HTTP/1.1\r\n")
        writer.write("Host: 127.0.0.1:$port\r\n")
        writer.write("TimeSeekRange.dlna.org: npt=00:01:30\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        val statusLine = reader.readLine() ?: ""
        val buffer = CharArray(1024)
        while (reader.read(buffer) != -1) {
        }
        socket.close()

        assertTrue(statusLine.contains("200 OK"))
    }
}
