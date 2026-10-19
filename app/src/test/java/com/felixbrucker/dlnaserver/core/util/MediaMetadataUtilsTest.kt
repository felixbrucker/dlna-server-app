package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MediaMetadataUtilsTest {

    @Test
    fun testIs10BitWithNonExistentFileReturnsFalse() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val result = MediaMetadataUtils.is10Bit(nonExistentFile)

        assertFalse(result)
    }

    @Test
    fun testExtractMetadataWithNonExistentFileReturnsEmptyMetadata() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val result = MediaMetadataUtils.extractMetadata(nonExistentFile)

        assertEquals(ExtractedMediaMetadata(), result)
    }
}
