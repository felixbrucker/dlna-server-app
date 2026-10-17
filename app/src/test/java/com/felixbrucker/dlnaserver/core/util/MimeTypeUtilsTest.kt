package com.felixbrucker.dlnaserver.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MimeTypeUtilsTest {

    @Test
    fun testGetMimeTypeForVideoFile() {
        val file = File("/tmp/sample_video.mp4")

        val mimeType = MimeTypeUtils.getMimeType(file)

        assertEquals("video/mp4", mimeType)
    }

    @Test
    fun testGetMimeTypeForAudioFile() {
        val file = File("/tmp/sample_audio.mp3")

        val mimeType = MimeTypeUtils.getMimeType(file)

        assertEquals("audio/mpeg", mimeType)
    }

    @Test
    fun testGetMimeTypeForImageFile() {
        val file = File("/tmp/sample_image.png")

        val mimeType = MimeTypeUtils.getMimeType(file)

        assertEquals("image/png", mimeType)
    }

    @Test
    fun testGetUpnpClassForDirectory() {
        val file = File("/tmp")

        val upnpClass = MimeTypeUtils.getUpnpClass(file)

        assertEquals("object.container.storageFolder", upnpClass)
    }

    @Test
    fun testGetUpnpClassForVideo() {
        val file = File("/tmp/video.mkv")

        val upnpClass = MimeTypeUtils.getUpnpClass(file)

        assertEquals("object.item.videoItem.movie", upnpClass)
    }
}
