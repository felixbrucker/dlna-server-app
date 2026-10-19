package com.felixbrucker.dlnaserver.core.transcoder

data class TranscodeJobProgress(
    val jobId: String,
    val progress: Float = 0f,
    val speed: String = "0.0x",
    val eta: String = "--:--",
    val etaSeconds: Long = 0L
)
