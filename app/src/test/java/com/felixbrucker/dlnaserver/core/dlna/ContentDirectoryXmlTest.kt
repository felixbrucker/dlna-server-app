package com.felixbrucker.dlnaserver.core.dlna

import android.webkit.MimeTypeMap
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ContentDirectoryXmlTest {

    @Before
    fun setUp() {
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMapMock = mockk<MimeTypeMap>()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMapMock
        every { mimeTypeMapMock.getMimeTypeFromExtension(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(MimeTypeMap::class)
    }

    @Test
    fun testGenerateDeviceDescriptionContainsFriendlyNameAndServices() {
        val ip = "192.168.1.100"
        val port = 8192

        val xml = ContentDirectoryXml.generateDeviceDescription("Test DLNA")

        assertTrue(xml.contains("Test DLNA"))
        assertTrue(xml.contains("urn:schemas-upnp-org:service:ContentDirectory:1"))
    }

    @Test
    fun testBuildBrowseResponseForRootContainer() {
        val sharedDirs = listOf(
            SharedDirectory(id = 1, path = "/tmp", displayName = "Temp Folder")
        )

        val xml = ContentDirectoryXml.buildBrowseResponse(
            objectID = "0",
            browseFlag = "BrowseDirectChildren",
            startingIndex = 0,
            requestedCount = 0,
            sharedDirectories = sharedDirs,
            baseUrl = "http://192.168.1.100:8192"
        )

        assertTrue(xml.contains("&lt;container id=&quot;f_"))
        assertTrue(xml.contains("Temp Folder"))
    }

    @Test
    fun testBuildBrowseResponseForMediaFile() {
        val tempFile = File.createTempFile("video_sample", ".mkv").apply { deleteOnExit() }
        val sharedDir = SharedDirectory(id = 1, path = tempFile.parent ?: tempFile.absolutePath, displayName = "Videos")

        val xml = ContentDirectoryXml.buildBrowseResponse(
            objectID = "i_${ContentDirectoryXml.encodePath(tempFile.absolutePath)}",
            browseFlag = "BrowseMetadata",
            startingIndex = 0,
            requestedCount = 0,
            sharedDirectories = listOf(sharedDir),
            baseUrl = "http://192.168.1.100:8192"
        )

        assertTrue(xml.contains("video/x-matroska"))
        assertTrue(xml.contains("DLNA.ORG_PN=MATROSKA"))
    }

    @Test
    fun testEncodeAndDecodePath() {
        val path = "/storage/emulated/0/My Music & Songs"

        val encoded = ContentDirectoryXml.encodePath(path)
        val decoded = ContentDirectoryXml.decodePath(encoded)

        assertTrue(encoded != path)
        assertTrue(decoded == path)
    }
}
