package com.felixbrucker.dlnaserver.core.model

enum class TranscodeOption(
    val id: String,
    val codecName: String,
    val mimeType: String?,
    val ffmpegEncoderSw: String?,
    val ffmpegEncoderHw: String?
) {
    NONE(
        id = "NONE",
        codecName = "Off",
        mimeType = null,
        ffmpegEncoderSw = null,
        ffmpegEncoderHw = null
    ),
    X264(
        id = "X264",
        codecName = "x264",
        mimeType = "video/avc",
        ffmpegEncoderSw = "libx264",
        ffmpegEncoderHw = "h264_mediacodec"
    ),
    X265(
        id = "X265",
        codecName = "x265",
        mimeType = "video/hevc",
        ffmpegEncoderSw = "libx265",
        ffmpegEncoderHw = "hevc_mediacodec"
    ),
    VP9(
        id = "VP9",
        codecName = "VP9",
        mimeType = "video/x-vnd.on2.vp9",
        ffmpegEncoderSw = "libvpx-vp9",
        ffmpegEncoderHw = "vp9_mediacodec"
    ),
    AV1(
        id = "AV1",
        codecName = "AV1",
        mimeType = "video/av01",
        ffmpegEncoderSw = "libsvtav1",
        ffmpegEncoderHw = "av1_mediacodec"
    );

    fun getDisplayLabel(hasHwSupport: Boolean): String {
        return when (this) {
            NONE -> "Off"
            else -> if (hasHwSupport) "$codecName (HW)" else "$codecName (SW)"
        }
    }

    companion object {
        fun fromId(id: String): TranscodeOption {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
