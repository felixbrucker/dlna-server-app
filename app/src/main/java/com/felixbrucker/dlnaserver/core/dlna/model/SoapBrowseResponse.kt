package com.felixbrucker.dlnaserver.core.dlna.model

data class SoapBrowseResponse(
    val didlLite: DidlLite,
    val numberReturned: Int,
    val totalMatches: Int,
    val updateId: Int = 1
) {
    fun toXml(): String {
        val escapedResult = escapeXml(didlLite.toXml())
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
      <Result>$escapedResult</Result>
      <NumberReturned>$numberReturned</NumberReturned>
      <TotalMatches>$totalMatches</TotalMatches>
      <UpdateID>$updateId</UpdateID>
    </u:BrowseResponse>
  </s:Body>
</s:Envelope>"""
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
