package com.felixbrucker.dlnaserver.core.dlna.model

sealed interface DidlObject {
    fun toXml(): String
}

data class DidlContainer(
    val id: String,
    val parentId: String,
    val title: String,
    val childCount: Int,
    val restricted: Boolean = true,
    val upnpClass: String = "object.container.storageFolder"
) : DidlObject {
    override fun toXml(): String {
        val escapedTitle = title.escapeXml()
        val restrStr = if (restricted) "1" else "0"
        return """<container id="$id" parentID="$parentId" restricted="$restrStr" childCount="$childCount"><dc:title>$escapedTitle</dc:title><upnp:class>$upnpClass</upnp:class></container>"""
    }
}

data class DidlItem(
    val id: String,
    val parentId: String,
    val title: String,
    val dateStr: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val upnpClass: String,
    val resources: List<DidlResource>,
    val restricted: Boolean = true
) : DidlObject {
    override fun toXml(): String {
        val sb = StringBuilder()
        val restrStr = if (restricted) "1" else "0"
        val escapedTitle = title.escapeXml()

        sb.append("""<item id="$id" parentID="$parentId" restricted="$restrStr">""")
        sb.append("""<dc:title>$escapedTitle</dc:title>""")

        dateStr?.let { sb.append("""<dc:date>$it</dc:date>""") }
        artist?.let { sb.append("""<upnp:artist>${it.escapeXml()}</upnp:artist>""") }
        album?.let { sb.append("""<upnp:album>${it.escapeXml()}</upnp:album>""") }
        genre?.let { sb.append("""<upnp:genre>${it.escapeXml()}</upnp:genre>""") }

        sb.append("""<upnp:class>$upnpClass</upnp:class>""")

        for (res in resources) {
            sb.append(res.toXml())
        }

        sb.append("""<sec:dcmInfo>CREATIONDATE=0,FOLDER=0,CHILDCOUNT=0</sec:dcmInfo>""")
        sb.append("""</item>""")

        return sb.toString()
    }
}
