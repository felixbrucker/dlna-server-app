package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ContentDirectoryXml {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun generateDeviceDescription(
        ipAddress: String,
        port: Int,
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
        val didlBuilder = StringBuilder()
        didlBuilder.append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/" xmlns:sec="http://www.sec.co.kr/dlna">""")

        var totalMatches = 0
        var returnedCount = 0

        if (objectID == "0") {
            if (browseFlag == "BrowseMetadata") {
                didlBuilder.append(
                    createContainerXml(
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
                    didlBuilder.append(
                        createContainerXml(
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
                    val parentId = getParentContainerId(targetDir, sharedDirectories)
                    didlBuilder.append(
                        createContainerXml(
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
                            didlBuilder.append(
                                createContainerXml(
                                    id = "f_$encPath",
                                    parentId = objectID,
                                    title = file.name,
                                    childCount = childCount
                                )
                            )
                        } else {
                            val mediaUrl = "$baseUrl/media?path=$encPath"
                            val mimeType = MimeTypeUtils.getMimeType(file)
                            val upnpClass = MimeTypeUtils.getUpnpClass(file)
                            val dlnaProfile = MimeTypeUtils.getDlnaProfile(file)
                            val modDate = isoDateFormat.format(Date(file.lastModified()))
                            didlBuilder.append(
                                createItemXml(
                                    id = "i_$encPath",
                                    parentId = objectID,
                                    title = file.name,
                                    upnpClass = upnpClass,
                                    mimeType = mimeType,
                                    dlnaProfile = dlnaProfile,
                                    size = file.length(),
                                    dateStr = modDate,
                                    mediaUrl = mediaUrl
                                )
                            )
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
                val parentId = getParentContainerId(file, sharedDirectories)
                val mediaUrl = "$baseUrl/media?path=$encPath"
                val mimeType = MimeTypeUtils.getMimeType(file)
                val upnpClass = MimeTypeUtils.getUpnpClass(file)
                val dlnaProfile = MimeTypeUtils.getDlnaProfile(file)
                val modDate = isoDateFormat.format(Date(file.lastModified()))
                didlBuilder.append(
                    createItemXml(
                        id = objectID,
                        parentId = parentId,
                        title = file.name,
                        upnpClass = upnpClass,
                        mimeType = mimeType,
                        dlnaProfile = dlnaProfile,
                        size = file.length(),
                        dateStr = modDate,
                        mediaUrl = mediaUrl
                    )
                )
                totalMatches = 1
                returnedCount = 1
            }
        }

        didlBuilder.append("</DIDL-Lite>")
        val escapedResult = escapeXml(didlBuilder.toString())

        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
      <Result>$escapedResult</Result>
      <NumberReturned>$returnedCount</NumberReturned>
      <TotalMatches>$totalMatches</TotalMatches>
      <UpdateID>1</UpdateID>
    </u:BrowseResponse>
  </s:Body>
</s:Envelope>"""
    }

    private fun getParentContainerId(file: File, sharedDirectories: List<SharedDirectory>): String {
        for (sharedDir in sharedDirectories) {
            if (file.absolutePath == File(sharedDir.path).absolutePath) {
                return "0"
            }
        }
        val parent = file.parentFile ?: return "0"
        for (sharedDir in sharedDirectories) {
            if (parent.absolutePath == File(sharedDir.path).absolutePath) {
                return "f_${encodePath(sharedDir.path)}"
            }
        }
        return "f_${encodePath(parent.absolutePath)}"
    }

    private fun createContainerXml(
        id: String,
        parentId: String,
        title: String,
        childCount: Int
    ): String {
        val escapedTitle = escapeXml(title)
        return """<container id="$id" parentID="$parentId" restricted="1" childCount="$childCount"><dc:title>$escapedTitle</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>"""
    }

    private fun createItemXml(
        id: String,
        parentId: String,
        title: String,
        upnpClass: String,
        mimeType: String,
        dlnaProfile: String,
        size: Long,
        dateStr: String,
        mediaUrl: String
    ): String {
        val escapedTitle = escapeXml(title)
        val escapedUrl = escapeXml(mediaUrl)
        val protocolInfo = "http-get:*:$mimeType:DLNA.ORG_PN=$dlnaProfile;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        return """<item id="$id" parentID="$parentId" restricted="1"><dc:title>$escapedTitle</dc:title><dc:date>$dateStr</dc:date><upnp:class>$upnpClass</upnp:class><res protocolInfo="$protocolInfo" size="$size">$escapedUrl</res></item>"""
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

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
