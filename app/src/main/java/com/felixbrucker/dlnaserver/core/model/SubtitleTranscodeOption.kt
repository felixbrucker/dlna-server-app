package com.felixbrucker.dlnaserver.core.model

enum class SubtitleTranscodeOption(
    val id: String,
    val codecName: String,
    val ffmpegEncoder: String
) {
    KEEP(
        id = "KEEP",
        codecName = "Keep original",
        ffmpegEncoder = "copy"
    ),
    ASS(
        id = "ASS",
        codecName = "ASS",
        ffmpegEncoder = "ass"
    ),
    SRT(
        id = "SRT",
        codecName = "SRT",
        ffmpegEncoder = "subrip"
    );

    companion object {
        fun getDefaultOption(): SubtitleTranscodeOption = KEEP

        fun fromId(id: String): SubtitleTranscodeOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: getDefaultOption()
        }
    }
}
