package com.felixbrucker.dlnaserver.core.dlna.model

data class DidlResource(
    val protocolInfo: String,
    val size: Long,
    val duration: String? = null,
    val resolution: String? = null,
    val bitrate: Long? = null,
    val sampleFrequency: Long? = null,
    val nrAudioChannels: Int? = null,
    val url: String
) {
    fun toXml(): String {
        val durationAttr = duration?.let { " duration=\"$it\"" } ?: ""
        val resolutionAttr = resolution?.let { " resolution=\"$it\"" } ?: ""
        val bitrateAttr = bitrate?.let { " bitrate=\"$it\"" } ?: ""
        val sampleFreqAttr = sampleFrequency?.let { " sampleFrequency=\"$it\"" } ?: ""
        val channelsAttr = nrAudioChannels?.let { " nrAudioChannels=\"$it\"" } ?: ""
        val escapedUrl = url.escapeXml()

        return """<res protocolInfo="$protocolInfo" size="$size"$durationAttr$resolutionAttr$bitrateAttr$sampleFreqAttr$channelsAttr>$escapedUrl</res>"""
    }
}
