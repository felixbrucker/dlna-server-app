package com.felixbrucker.dlnaserver.core.transcoder

import java.io.File

enum class TranscodeJobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

data class TranscodeJob(
    val id: String,
    val inputFile: File,
    val outputFile: File,
    val status: TranscodeJobStatus = TranscodeJobStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String = "0.0x",
    val eta: String = "--:--",
    val errorMessage: String? = null,
    val totalDurationMs: Long = 0L
)
