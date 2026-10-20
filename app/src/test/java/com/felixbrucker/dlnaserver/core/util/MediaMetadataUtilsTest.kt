package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MediaMetadataUtilsTest {

    @Test
    fun testExtractTrackCodecsWithNonExistentFileReturnsEmptyCodecs() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val result = MediaMetadataUtils.extractTrackCodecs(nonExistentFile)

        assertEquals(MediaTrackCodecs(), result)
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
}
