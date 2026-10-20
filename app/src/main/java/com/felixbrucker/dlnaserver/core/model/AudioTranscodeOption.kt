package com.felixbrucker.dlnaserver.core.model

import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils

enum class AudioTranscodeOption(
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
    AAC_SW(
        id = "AAC_SW",
        codecName = "AAC",
        mimeType = "audio/mp4a-latm",
        ffmpegEncoder = "aac",
        isHardware = false
    ),
    AAC_HW(
        id = "AAC_HW",
        codecName = "AAC",
        mimeType = "audio/mp4a-latm",
        ffmpegEncoder = "aac_mediacodec",
        isHardware = true
    ),
    AC3_SW(
        id = "AC3_SW",
        codecName = "AC3",
        mimeType = "audio/ac3",
        ffmpegEncoder = "ac3",
        isHardware = false
    ),
    FLAC_SW(
        id = "FLAC_SW",
        codecName = "FLAC",
        mimeType = "audio/flac",
        ffmpegEncoder = "flac",
        isHardware = false
    );

    val isHardwareSupported: Boolean by lazy {
        if (isHardware) {
            HardwareCodecUtils.hasHardwareEncoderSupport(mimeType)
        } else {
            true
        }
    }

    companion object {
        fun getDefaultOption(): AudioTranscodeOption = KEEP

        fun fromId(id: String): AudioTranscodeOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: getDefaultOption()
        }
    }
}
