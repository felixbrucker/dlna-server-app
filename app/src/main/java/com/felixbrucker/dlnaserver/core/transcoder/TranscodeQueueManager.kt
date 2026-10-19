package com.felixbrucker.dlnaserver.core.transcoder

import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils
import com.felixbrucker.dlnaserver.feature.service.TranscodeServiceLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TranscodeQueueManager @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val transcodeServiceLauncher: TranscodeServiceLauncher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<TranscodeJob>>(emptyList())
    val jobs: StateFlow<List<TranscodeJob>> = _jobs.asStateFlow()

    private val runningJobTasks = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            appSettingsRepository.transcodeSettings
                .map { it.maxParallelJobs }
                .distinctUntilChanged()
                .collect {
                    processQueue()
                }
        }
    }

    fun enqueueJob(inputFile: File) {
        val parentDir = inputFile.parentFile ?: return
        val outputFileName = "transcoded_${inputFile.name}"
        val outputFile = File(parentDir, outputFileName)

        val durationMs = getMediaDuration(inputFile)
        val jobId = UUID.randomUUID().toString()

        val job = TranscodeJob(
            id = jobId,
            inputFile = inputFile,
            outputFile = outputFile,
            status = TranscodeJobStatus.QUEUED,
            totalDurationMs = durationMs
        )

        _jobs.value = _jobs.value + job
        transcodeServiceLauncher.startService()
        processQueue()
    }

    fun cancelJob(jobId: String) {
        val task = runningJobTasks.remove(jobId)
        task?.cancel()
        val targetJob = _jobs.value.find { it.id == jobId }
        if (targetJob != null && targetJob.outputFile.exists()) {
            try { targetJob.outputFile.delete() } catch (_: Throwable) {}
        }
        _jobs.value = _jobs.value.filter { it.id != jobId }
        processQueue()
    }

    fun retryJob(jobId: String) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    status = TranscodeJobStatus.QUEUED,
                    progress = 0f,
                    speed = "0.0x",
                    eta = "--:--",
                    etaSeconds = 0L,
                    errorMessage = null
                )
            } else job
        }
        transcodeServiceLauncher.startService()
        processQueue()
    }

    private fun processQueue() {
        scope.launch {
            val settings = appSettingsRepository.transcodeSettings.first()
            val maxParallel = settings.maxParallelJobs

            val currentRunning = _jobs.value.count { it.status == TranscodeJobStatus.RUNNING }
            if (currentRunning >= maxParallel) return@launch

            val nextJobs = _jobs.value
                .filter { it.status == TranscodeJobStatus.QUEUED }
                .take(maxParallel - currentRunning)

            for (nextJob in nextJobs) {
                updateJobStatus(nextJob.id, TranscodeJobStatus.RUNNING)
                val jobTask = launch {
                    executeTranscodeJob(nextJob, settings)
                }
                runningJobTasks[nextJob.id] = jobTask
            }
        }
    }

    private suspend fun executeTranscodeJob(job: TranscodeJob, settings: TranscodeSettings) {
        val inputFile = job.inputFile
        val outputFile = job.outputFile
        val option = settings.option

        val hasHw = HardwareCodecUtils.isEncoderSupported(option.mimeType)
        val videoEncoder = if (hasHw) {
            option.ffmpegEncoderHw
        } else {
            option.ffmpegEncoderSw
        }

        val argumentsList = mutableListOf<String>().apply {
            add("-hide_banner")
            add("-loglevel")
            add("info")
            add("-i")
            add(inputFile.absolutePath)
            add("-map")
            add("0:v:0")
            add("-map")
            add("0:a?")
            add("-map")
            add("0:s?")

            settings.resolution.scaleFilter?.let { filter ->
                add("-vf")
                add(filter)
            }

            add("-c:v")
            add(videoEncoder)

            if (hasHw) {
                add("-b:v")
                add("${settings.bitrateMb}M")
            } else {
                add("-crf")
                add(settings.crf.toString())
            }

            add("-c:a")
            add("copy")
            add("-c:s")
            add("copy")
            add("-f")
            add("matroska")
            add("-y")
            add(outputFile.absolutePath)
        }.toTypedArray()

        try {
            val session = suspendCancellableCoroutine { continuation ->
                val asyncSession = FFmpegKit.executeWithArgumentsAsync(
                    argumentsList,
                    { completedSession ->
                        continuation.resume(completedSession)
                    },
                    { _ -> },
                    { statistics ->
                        if (statistics != null) {
                            val timeMs = statistics.time
                            val totalDuration = job.totalDurationMs
                            val progress = if (totalDuration > 0) {
                                (timeMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            val speed = if (statistics.speed > 0) String.format("%.1fx", statistics.speed) else "1.0x"
                            val remainingSeconds = if (statistics.speed > 0 && totalDuration > timeMs) {
                                ((totalDuration - timeMs) / statistics.speed).toLong() / 1000
                            } else 0L

                            val etaMinutes = remainingSeconds / 60
                            val etaSecs = remainingSeconds % 60
                            val etaStr = String.format("%02d:%02d", etaMinutes, etaSecs)

                            updateJobProgress(job.id, progress, speed, etaStr, remainingSeconds)
                        }
                    }
                )

                continuation.invokeOnCancellation {
                    try {
                        FFmpegKit.cancel(asyncSession.sessionId)
                    } catch (_: Throwable) {}
                }
            }

            if (session.returnCode.isValueSuccess) {
                updateJobStatus(job.id, TranscodeJobStatus.COMPLETED)
            } else {
                updateJobStatus(
                    job.id,
                    TranscodeJobStatus.FAILED,
                    errorMessage = "FFmpeg exit code: ${session.returnCode}"
                )
                if (outputFile.exists()) outputFile.delete()
            }
        } catch (e: Exception) {
            updateJobStatus(job.id, TranscodeJobStatus.FAILED, errorMessage = e.message)
            if (outputFile.exists()) outputFile.delete()
        } finally {
            runningJobTasks.remove(job.id)
            processQueue()
        }
    }

    private fun updateJobStatus(jobId: String, status: TranscodeJobStatus, errorMessage: String? = null) {
        _jobs.value = _jobs.value.mapNotNull { job ->
            if (job.id == jobId) {
                if (status == TranscodeJobStatus.COMPLETED) {
                    null
                } else {
                    job.copy(status = status, errorMessage = errorMessage ?: job.errorMessage)
                }
            } else job
        }
    }

    private fun updateJobProgress(jobId: String, progress: Float, speed: String, eta: String, etaSeconds: Long) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(progress = progress, speed = speed, eta = eta, etaSeconds = etaSeconds)
            } else job
        }
    }

    private fun getMediaDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }
}
