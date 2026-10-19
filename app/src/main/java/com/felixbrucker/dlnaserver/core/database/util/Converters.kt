package com.felixbrucker.dlnaserver.core.database.util

import androidx.room.TypeConverter
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus

class Converters {
    @TypeConverter
    fun fromTranscodeJobStatus(status: TranscodeJobStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTranscodeJobStatus(value: String): TranscodeJobStatus {
        return try {
            TranscodeJobStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            TranscodeJobStatus.QUEUED
        }
    }
}
