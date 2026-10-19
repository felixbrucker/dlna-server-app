package com.felixbrucker.dlnaserver.core.transcoder

import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VideoTranscoderTest {

    private lateinit var tempFile: File
    private lateinit var videoTranscoder: VideoTranscoder

    @Before
    fun setUp() {
        tempFile = File.createTempFile("transcode_test", ".mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            deleteOnExit()
        }
        videoTranscoder = VideoTranscoder()
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testStreamRawFileStreamsData() {
        val outputStream = ByteArrayOutputStream()

        videoTranscoder.streamRawFile(tempFile, outputStream)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), outputStream.toByteArray())
    }

    @Test
    fun testTranscodeToStreamNonExistentFileProducesEmptyStream() = runTest {
        val missingFile = File("non_existent_file.mp4")
        val outputStream = ByteArrayOutputStream()

        videoTranscoder.transcodeToStream(
            inputFile = missingFile,
            option = TranscodeOption.X264,
            outputStream = outputStream,
            onStart = {},
            onProgress = {}
        )

        assertEquals(0, outputStream.toByteArray().size)
    }
}
