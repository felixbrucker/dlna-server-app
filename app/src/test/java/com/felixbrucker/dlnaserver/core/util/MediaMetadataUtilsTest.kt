package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun testGetVideoDimensionsWithNonExistentFileReturnsNull() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val dims = MediaMetadataUtils.getVideoDimensions(nonExistentFile)

        assertNull(dims)
    }

    @Test
    fun testGetVideoCodecNameWithNonExistentFileReturnsNull() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val codec = MediaMetadataUtils.getVideoCodecName(nonExistentFile)

        assertNull(codec)
    }

    @Test
    fun testGetAudioCodecNameWithNonExistentFileReturnsNull() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val codec = MediaMetadataUtils.getAudioCodecName(nonExistentFile)

        assertNull(codec)
    }

    @Test
    fun testGetSubtitleCodecNameWithNonExistentFileReturnsNull() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val codec = MediaMetadataUtils.getSubtitleCodecName(nonExistentFile)

        assertNull(codec)
    }
}
