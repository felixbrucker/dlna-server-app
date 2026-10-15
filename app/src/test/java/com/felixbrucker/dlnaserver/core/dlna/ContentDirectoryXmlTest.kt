package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDirectoryXmlTest {

    @Test
    fun testGenerateDeviceDescriptionContainsFriendlyNameAndServices() {
        val ip = "192.168.1.100"
        val port = 8192

        val xml = ContentDirectoryXml.generateDeviceDescription(ip, port, "Test DLNA")

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
    fun testEncodeAndDecodePath() {
        val path = "/storage/emulated/0/My Music & Songs"

        val encoded = ContentDirectoryXml.encodePath(path)
        val decoded = ContentDirectoryXml.decodePath(encoded)

        assertTrue(encoded != path)
        assertTrue(decoded == path)
    }
}
