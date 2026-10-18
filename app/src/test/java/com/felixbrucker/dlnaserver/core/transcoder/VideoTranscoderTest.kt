package com.felixbrucker.dlnaserver.core.transcoder

import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class VideoTranscoderTest {

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        tempFile = File.createTempFile("transcode_test", ".mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            deleteOnExit()
        }
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testTranscodeToStreamWithNoneStreamsRawFile() {
        val outputStream = ByteArrayOutputStream()

        VideoTranscoder.transcodeToStream(tempFile, TranscodeOption.NONE, outputStream)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), outputStream.toByteArray())
    }

    @Test
    fun testTranscodeToStreamNonExistentFileProducesEmptyStream() {
        val missingFile = File("non_existent_file.mp4")
        val outputStream = ByteArrayOutputStream()

        VideoTranscoder.transcodeToStream(missingFile, TranscodeOption.X264, outputStream)

        assertEquals(0, outputStream.toByteArray().size)
    }

    @Test
    fun testTranscodeToStreamWithOptionFallbackStreamsData() {
        val outputStream = ByteArrayOutputStream()

        VideoTranscoder.transcodeToStream(tempFile, TranscodeOption.X264, outputStream, hasHwSupport = false)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), outputStream.toByteArray())
    }
}
