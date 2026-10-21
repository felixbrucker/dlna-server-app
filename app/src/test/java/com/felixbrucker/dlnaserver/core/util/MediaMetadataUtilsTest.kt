package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MediaMetadataUtilsTest {

    @Test
    fun testExtractTrackMetadataWithNonExistentFileReturnsEmptyMetadata() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val result = MediaMetadataUtils.extractVideoMetadata(nonExistentFile)

        assertEquals(VideoMetadata(), result)
    }

    @Test
    fun testExtractMetadataWithNonExistentFileReturnsEmptyMetadata() {
        val nonExistentFile = File("/tmp/non_existent_video_file.mp4")

        val result = MediaMetadataUtils.extractGenericMetadata(nonExistentFile)

        assertEquals(GenericMediaMetadata(), result)
    }
}
