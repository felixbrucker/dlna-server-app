package com.felixbrucker.dlnaserver.core.transcoder

import java.io.File

enum class TranscodeJobStatus {
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
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null,
    val totalDurationMs: Long = 0L,
    val bitDepthChange: String? = null,
    val resolutionChange: String? = null,
    val videoCodecChange: String? = null,
    val audioCodecChange: String? = null,
    val subtitleCodecChange: String? = null
)
