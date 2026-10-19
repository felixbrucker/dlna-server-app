package com.felixbrucker.dlnaserver.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import java.io.File

@Entity(tableName = "transcode_jobs")
data class TranscodeJobEntity(
    @PrimaryKey
    val id: String,
    val inputFilePath: String,
    val outputFilePath: String,
    val status: TranscodeJobStatus = TranscodeJobStatus.QUEUED,
    val errorMessage: String? = null,
    val totalDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val bitDepthChange: String? = null,
    val resolutionChange: String? = null,
    val codecChange: String? = null
) {
    val inputFile: File get() = File(inputFilePath)
    val outputFile: File get() = File(outputFilePath)
}
