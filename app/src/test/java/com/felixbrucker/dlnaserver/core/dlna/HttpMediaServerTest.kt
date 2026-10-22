package com.felixbrucker.dlnaserver.core.dlna

import android.webkit.MimeTypeMap
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    private lateinit var sharedDirectoryRepositoryMock: SharedDirectoryRepository
    private lateinit var server: HttpMediaServer
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMapMock = mockk<MimeTypeMap>()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMapMock
        every { mimeTypeMapMock.getMimeTypeFromExtension(any()) } returns null
        tempFile = File.createTempFile("test_media", ".mp4").apply {
            writeBytes(ByteArray(1024 * 100) { 0x01.toByte() })
            deleteOnExit()
        }
        sharedDir = SharedDirectory(
            id = 1,
            path = tempFile.parentFile?.absolutePath ?: tempFile.absolutePath,
            displayName = "Test"
        )
        sharedDirectoryRepositoryMock = mockk(relaxed = true)
        coEvery { sharedDirectoryRepositoryMock.getEnabledDirectoriesSync() } returns listOf(sharedDir)
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.stop()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }
        unmockkStatic(MimeTypeMap::class)
    }

    private fun startServer(): HttpMediaServer {
        val s = HttpMediaServer(sharedDirectoryRepositoryMock)
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
        val port = 8192
        server = startServer()
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

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
        assertEquals(1, server.connectedClients.value.size)
    }

    @Test
    fun testClientDisconnectDuringMediaStreamingHandledGracefully() = runTest {
        val port = 8192
        server = startServer()
        val socket = createConnectedSocket(port)
        val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
        val encodedPath = ContentDirectoryXml.encodePath(tempFile.absolutePath)
        writer.write("GET /media?path=$encodedPath HTTP/1.1\r\n")
        writer.write("Host: 127.0.0.1:$port\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

        val statusLine = reader.readLine()
        socket.close()

        assertTrue(statusLine != null && statusLine.contains("200 OK"))
    }
}
