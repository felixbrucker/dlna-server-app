package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.dlna.model.DidlContainer
import com.felixbrucker.dlnaserver.core.dlna.model.DidlItem
import com.felixbrucker.dlnaserver.core.dlna.model.DidlLite
import com.felixbrucker.dlnaserver.core.dlna.model.DidlObject
import com.felixbrucker.dlnaserver.core.dlna.model.DidlResource
import com.felixbrucker.dlnaserver.core.dlna.model.SoapBrowseResponse
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.util.MediaMetadataUtils
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant

object ContentDirectoryXml {

    fun generateDeviceDescription(
        friendlyName: String = "DLNA Server"
    ): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
    <friendlyName>$friendlyName</friendlyName>
    <manufacturer>Felix Brucker</manufacturer>
    <manufacturerURL>https://github.com/felixbrucker</manufacturerURL>
    <modelDescription>DLNA Media Server for Android</modelDescription>
    <modelName>DLNA Server</modelName>
    <modelNumber>1.0</modelNumber>
    <modelURL>https://github.com/felixbrucker</modelURL>
    <serialNumber>10000000</serialNumber>
    <UDN>uuid:550e8400-e29b-41d4-a716-446655440000</UDN>
    <dlna:X_DLDOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMS-1.50</dlna:X_DLDOC>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
        <SCPDURL>/scpd/ContentDirectory.xml</SCPDURL>
        <controlURL>/ctl/ContentDirectory</controlURL>
        <eventSubURL>/evt/ContentDirectory</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/scpd/ConnectionManager.xml</SCPDURL>
        <controlURL>/ctl/ConnectionManager</controlURL>
        <eventSubURL>/evt/ConnectionManager</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>"""
    }

    fun getContentDirectoryScpd(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <actionList>
    <action>
      <name>GetSearchCapabilities</name>
      <argumentList>
        <argument>
          <name>SearchCaps</name>
          <direction>out</direction>
          <relatedStateVariable>SearchCapabilities</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
    <action>
      <name>GetSortCapabilities</name>
      <argumentList>
        <argument>
          <name>SortCaps</name>
          <direction>out</direction>
          <relatedStateVariable>SortCapabilities</relatedStateVariable>
        </argument>
      </argumentList>
    </action>

    <action>
      <name>GetSystemUpdateID</name>
      <argumentList>
        <argument>
          <name>Id</name>
          <direction>out</direction>
          <relatedStateVariable>SystemUpdateID</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
    <action>
      <name>Browse</name>
      <argumentList>
        <argument>
          <name>ObjectID</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable>
        </argument>
        <argument>
          <name>BrowseFlag</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable>
        </argument>
        <argument>
          <name>Filter</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable>
        </argument>
        <argument>
          <name>StartingIndex</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable>
        </argument>
        <argument>
          <name>RequestedCount</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
        </argument>
        <argument>
          <name>SortCriteria</name>
          <direction>in</direction>
          <relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable>
        </argument>
        <argument>
          <name>Result</name>
          <direction>out</direction>
          <relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable>
        </argument>
        <argument>
          <name>NumberReturned</name>
          <direction>out</direction>
          <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
        </argument>
        <argument>
          <name>TotalMatches</name>
          <direction>out</direction>
          <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
        </argument>
        <argument>
          <name>UpdateID</name>
          <direction>out</direction>
          <relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_ObjectID</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_Result</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_BrowseFlag</name>
      <dataType>string</dataType>
      <allowedValueList>
        <allowedValue>BrowseMetadata</allowedValue>
        <allowedValue>BrowseDirectChildren</allowedValue>
      </allowedValueList>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_Filter</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_SortCriteria</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_Index</name>
      <dataType>ui4</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_Count</name>
      <dataType>ui4</dataType>
    </stateVariable>
    <stateVariable sendEvents="no">
      <name>A_ARG_TYPE_UpdateID</name>
      <dataType>ui4</dataType>
    </stateVariable>
    <stateVariable sendEvents="yes">
      <name>SearchCapabilities</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="yes">
      <name>SortCapabilities</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="yes">
      <name>SystemUpdateID</name>
      <dataType>ui4</dataType>
    </stateVariable>
  </serviceStateTable>
</scpd>"""
    }

    fun getConnectionManagerScpd(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <actionList>
    <action>
      <name>GetProtocolInfo</name>
      <argumentList>
        <argument>
          <name>Source</name>
          <direction>out</direction>
          <relatedStateVariable>SourceProtocolInfo</relatedStateVariable>
        </argument>
        <argument>
          <name>Sink</name>
          <direction>out</direction>
          <relatedStateVariable>SinkProtocolInfo</relatedStateVariable>
        </argument>
      </argumentList>
    </action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="yes">
      <name>SourceProtocolInfo</name>
      <dataType>string</dataType>
    </stateVariable>
    <stateVariable sendEvents="yes">
      <name>SinkProtocolInfo</name>
      <dataType>string</dataType>
    </stateVariable>
  </serviceStateTable>
</scpd>"""
    }

    fun buildBrowseResponse(
        objectID: String,
        browseFlag: String,
        startingIndex: Int,
        requestedCount: Int,
        sharedDirectories: List<SharedDirectory>,
        baseUrl: String
    ): String {
        val objectsList = mutableListOf<DidlObject>()
        var totalMatches = 0
        var returnedCount = 0
        val sharedDirPaths = sharedDirectories.map { it.path to File(it.path).absolutePath }

        if (objectID == "0") {
            if (browseFlag == "BrowseMetadata") {
                objectsList.add(
                    DidlContainer(
                        id = "0",
                        parentId = "-1",
                        title = "Root",
                        childCount = sharedDirectories.size
                    )
                )
                totalMatches = 1
                returnedCount = 1
            } else {
                val items = sharedDirectories
                totalMatches = items.size
                val endIndex = if (requestedCount > 0) {
                    minOf(startingIndex + requestedCount, items.size)
                } else {
                    items.size
                }
                val subList = if (startingIndex < items.size) items.subList(startingIndex, endIndex) else emptyList()
                for (dir in subList) {
                    val file = File(dir.path)
                    val childCount = if (file.exists() && file.isDirectory) file.listFiles()?.size ?: 0 else 0
                    val encPath = encodePath(dir.path)
                    objectsList.add(
                        DidlContainer(
                            id = "f_$encPath",
                            parentId = "0",
                            title = dir.displayName,
                            childCount = childCount
                        )
                    )
                    returnedCount++
                }
            }
        } else if (objectID.startsWith("f_")) {
            val path = decodePath(objectID.removePrefix("f_"))
            val targetDir = File(path)
            if (targetDir.exists() && targetDir.isDirectory) {
                if (browseFlag == "BrowseMetadata") {
                    val childCount = targetDir.listFiles()?.size ?: 0
                    val parentId = getParentContainerId(targetDir, sharedDirPaths)
                    objectsList.add(
                        DidlContainer(
                            id = objectID,
                            parentId = parentId,
                            title = targetDir.name,
                            childCount = childCount
                        )
                    )
                    totalMatches = 1
                    returnedCount = 1
                } else {
                    val files = targetDir.listFiles()?.sortedWith(
                        compareBy({ !it.isDirectory }, { it.name.lowercase() })
                    ) ?: emptyList()
                    totalMatches = files.size
                    val endIndex = if (requestedCount > 0) {
                        minOf(startingIndex + requestedCount, files.size)
                    } else {
                        files.size
                    }
                    val subList = if (startingIndex < files.size) files.subList(startingIndex, endIndex) else emptyList()
                    for (file in subList) {
                        val encPath = encodePath(file.absolutePath)
                        if (file.isDirectory) {
                            val childCount = file.listFiles()?.size ?: 0
                            objectsList.add(
                                DidlContainer(
                                    id = "f_$encPath",
                                    parentId = objectID,
                                    title = file.name,
                                    childCount = childCount
                                )
                            )
                        } else {
                            val mediaUrl = "$baseUrl/media?path=$encPath"
                            objectsList.add(buildDidlItem(objectID, file, encPath, mediaUrl, sharedDirPaths))
                        }
                        returnedCount++
                    }
                }
            }
        } else if (objectID.startsWith("i_")) {
            val path = decodePath(objectID.removePrefix("i_"))
            val file = File(path)
            if (file.exists() && file.isFile) {
                val encPath = encodePath(file.absolutePath)
                val mediaUrl = "$baseUrl/media?path=$encPath"
                objectsList.add(buildDidlItem("i_$encPath", file, encPath, mediaUrl, sharedDirPaths))
                totalMatches = 1
                returnedCount = 1
            }
        }

        val didlLite = DidlLite(objects = objectsList)
        val response = SoapBrowseResponse(
            didlLite = didlLite,
            numberReturned = returnedCount,
            totalMatches = totalMatches
        )
        return response.toXml()
    }

    private fun buildDidlItem(
        parentId: String,
        file: File,
        encPath: String,
        mediaUrl: String,
        sharedDirPaths: List<Pair<String, String>>
    ): DidlItem {
        val mimeType = MimeTypeUtils.getMimeType(file)
        val upnpClass = MimeTypeUtils.getUpnpClass(file, mimeType)
        val dlnaProfile = MimeTypeUtils.getDlnaProfile(file)
        val modDate = Instant.ofEpochMilli(file.lastModified()).toString()

        val metadata = MediaMetadataUtils.extractGenericMetadata(file)

        val dlnaPnPart = if (dlnaProfile.isNotEmpty()) "DLNA.ORG_PN=$dlnaProfile;" else ""
        val protocolInfo = "http-get:*:$mimeType:${dlnaPnPart}DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

        val resource = DidlResource(
            protocolInfo = protocolInfo,
            size = file.length(),
            duration = metadata.durationFormatted,
            resolution = metadata.resolution,
            bitrate = metadata.bitrate,
            sampleFrequency = metadata.sampleFrequency,
            url = mediaUrl
        )

        val itemParentId = if (parentId.startsWith("i_")) getParentContainerId(file, sharedDirPaths) else parentId

        return DidlItem(
            id = "i_$encPath",
            parentId = itemParentId,
            title = file.name,
            dateStr = modDate,
            artist = metadata.artist,
            album = metadata.album,
            genre = metadata.genre,
            upnpClass = upnpClass,
            resources = listOf(resource)
        )
    }

    private fun getParentContainerId(file: File, sharedDirPaths: List<Pair<String, String>>): String {
        val fileAbs = file.absolutePath
        for ((_, sharedAbs) in sharedDirPaths) {
            if (fileAbs == sharedAbs) {
                return "0"
            }
        }
        val parent = file.parentFile ?: return "0"
        val parentAbs = parent.absolutePath
        for ((sharedPath, sharedAbs) in sharedDirPaths) {
            if (parentAbs == sharedAbs) {
                return "f_${encodePath(sharedPath)}"
            }
        }
        return "f_${encodePath(parentAbs)}"
    }

    fun encodePath(path: String): String {
        return try {
            URLEncoder.encode(path, "UTF-8")
        } catch (_: Exception) {
            path
        }
    }

    fun decodePath(encoded: String): String {
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Exception) {
            encoded
        }
    }
}
