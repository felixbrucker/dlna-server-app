package com.felixbrucker.dlnaserver.core.util

import android.webkit.MimeTypeMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class MimeTypeUtilsTest {

    private lateinit var mimeTypeMapMock: MimeTypeMap

    @Before
    fun setUp() {
        mockkStatic(MimeTypeMap::class)
        mimeTypeMapMock = mockk()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMapMock
        every { mimeTypeMapMock.getMimeTypeFromExtension(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(MimeTypeMap::class)
    }

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
    fun testGetMimeTypeFromMimeTypeMap() {
        val file = File("/tmp/sample.custom")
        every { mimeTypeMapMock.getMimeTypeFromExtension("custom") } returns "application/x-custom"

        val mimeType = MimeTypeUtils.getMimeType(file)

        assertEquals("application/x-custom", mimeType)
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

        assertEquals("object.item.videoItem", upnpClass)
    }
}
