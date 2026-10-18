package com.felixbrucker.dlnaserver.core.transcoder

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

class FFmpegBinaryManagerTest {

    private lateinit var tempBinaryFile: File

    @Before
    fun setUp() {
        tempBinaryFile = File.createTempFile("ffmpeg_mock", ".bin").apply {
            writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
            setExecutable(true, false)
            deleteOnExit()
        }
    }

    @After
    fun tearDown() {
        if (tempBinaryFile.exists()) {
            tempBinaryFile.delete()
        }
    }

    @Test
    fun testSetCachedExecutableAndRetrieve() {
        FFmpegBinaryManager.setCachedExecutable(tempBinaryFile)

        val retrieved = FFmpegBinaryManager.getExecutable(null)

        assertNotNull(retrieved)
        assertEquals(tempBinaryFile.absolutePath, retrieved?.absolutePath)
    }
}
