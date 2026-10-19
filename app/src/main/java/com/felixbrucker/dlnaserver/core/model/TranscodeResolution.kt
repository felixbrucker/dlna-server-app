package com.felixbrucker.dlnaserver.core.model

enum class TranscodeResolution(
    val id: String,
    val label: String,
    val scaleFilter: String?
) {
    KEEP("KEEP", "Keep original", null),
    RES_4K("RES_4K", "4K (2160p)", "scale=3840:-2"),
    RES_1080P("RES_1080P", "1080p", "scale=1920:-2"),
    RES_720P("RES_720P", "720p", "scale=1280:-2");

    companion object {
        fun fromId(id: String): TranscodeResolution {
            return entries.firstOrNull { it.id == id } ?: KEEP
        }
    }
}
