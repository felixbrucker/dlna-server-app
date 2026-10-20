package com.felixbrucker.dlnaserver.core.model

enum class VideoTranscodePreset(
    val id: String,
    val label: String
) {
    ULTRAFAST("ultrafast", "ultrafast"),
    MEDIUM("medium", "medium"),
    SLOW("slow", "slow");

    fun getFfmpegPreset(option: VideoTranscodeOption): String {
        return if (option == VideoTranscodeOption.AV1_SW) {
            when (this) {
                ULTRAFAST -> "13"
                MEDIUM -> "6"
                SLOW -> "3"
            }
        } else {
            id
        }
    }

    companion object {
        fun getDefaultPreset(): VideoTranscodePreset = ULTRAFAST

        fun fromId(id: String): VideoTranscodePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: getDefaultPreset()
        }
    }
}
