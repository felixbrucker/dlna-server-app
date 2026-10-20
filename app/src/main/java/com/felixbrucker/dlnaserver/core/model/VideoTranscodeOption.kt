package com.felixbrucker.dlnaserver.core.model

import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils

enum class VideoTranscodeOption(
    val id: String,
    val codecName: String,
    val mimeType: String,
    val ffmpegEncoder: String,
    val isHardware: Boolean
) {
    KEEP(
        id = "KEEP",
        codecName = "Keep",
        mimeType = "",
        ffmpegEncoder = "copy",
        isHardware = false
    ),
    X264_SW(
        id = "X264_SW",
        codecName = "x264",
        mimeType = "video/avc",
        ffmpegEncoder = "libx264",
        isHardware = false
    ),
    X264_HW(
        id = "X264_HW",
        codecName = "x264",
        mimeType = "video/avc",
        ffmpegEncoder = "h264_mediacodec",
        isHardware = true
    ),
    X265_SW(
        id = "X265_SW",
        codecName = "x265",
        mimeType = "video/hevc",
        ffmpegEncoder = "libx265",
        isHardware = false
    ),
    X265_HW(
        id = "X265_HW",
        codecName = "x265",
        mimeType = "video/hevc",
        ffmpegEncoder = "hevc_mediacodec",
        isHardware = true
    ),
    VP9_SW(
        id = "VP9_SW",
        codecName = "VP9",
        mimeType = "video/x-vnd.on2.vp9",
        ffmpegEncoder = "libvpx-vp9",
        isHardware = false
    ),
    VP9_HW(
        id = "VP9_HW",
        codecName = "VP9",
        mimeType = "video/x-vnd.on2.vp9",
        ffmpegEncoder = "vp9_mediacodec",
        isHardware = true
    ),
    AV1_SW(
        id = "AV1_SW",
        codecName = "AV1",
        mimeType = "video/av01",
        ffmpegEncoder = "libsvtav1",
        isHardware = false
    ),
    AV1_HW(
        id = "AV1_HW",
        codecName = "AV1",
        mimeType = "video/av01",
        ffmpegEncoder = "av1_mediacodec",
        isHardware = true
    );

    val isHardwareSupported: Boolean by lazy {
        if (isHardware) {
            HardwareCodecUtils.hasHardwareEncoderSupport(mimeType)
        } else {
            true
        }
    }

    val is10BitSupported: Boolean = !isHardware

    val supportsPresets: Boolean get() = this == X264_SW || this == X265_SW || this == AV1_SW

    companion object {
        fun getDefaultOption(): VideoTranscodeOption {
            val hwOrder = listOf(X264_HW, X265_HW, AV1_HW, VP9_HW)
            return hwOrder.firstOrNull { it.isHardwareSupported } ?: X265_SW
        }

        fun fromId(id: String): VideoTranscodeOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: getDefaultOption()
        }
    }
}
