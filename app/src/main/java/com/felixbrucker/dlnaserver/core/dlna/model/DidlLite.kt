package com.felixbrucker.dlnaserver.core.dlna.model

data class DidlLite(
    val objects: List<DidlObject>
) {
    fun toXml(): String {
        val sb = StringBuilder()
        sb.append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/" xmlns:sec="http://www.sec.co.kr/dlna">""")
        for (obj in objects) {
            sb.append(obj.toXml())
        }
        sb.append("</DIDL-Lite>")
        return sb.toString()
    }
}
