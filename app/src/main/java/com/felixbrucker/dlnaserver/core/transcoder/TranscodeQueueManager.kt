package com.felixbrucker.dlnaserver.core.transcoder

import android.media.MediaMetadataRetriever
import android.os.PowerManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Level
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import com.felixbrucker.dlnaserver.core.util.MediaMetadataUtils
import com.felixbrucker.dlnaserver.feature.service.TranscodeServiceLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.round

@Singleton
class TranscodeQueueManager @Inject constructor(
    powerManager: PowerManager,
    private val appSettingsRepository: AppSettingsRepository,
    private val transcodeServiceLauncher: TranscodeServiceLauncher,
    private val transcodeJobDao: TranscodeJobDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progressMap = MutableStateFlow<Map<String, TranscodeJobProgress>>(emptyMap())

    val jobs: StateFlow<List<TranscodeJob>> = combine(
        transcodeJobDao.getAllJobs(),
        _progressMap
    ) { entities, progressMap ->
        entities.map { entity ->
            val progress = progressMap[entity.id]
            TranscodeJob(
                id = entity.id,
                inputFile = entity.inputFile,
                outputFile = entity.outputFile,
                status = entity.status,
                progress = progress?.progress ?: 0f,
                speed = progress?.speed ?: "0.0x",
                eta = progress?.eta ?: "--:--",
                etaSeconds = progress?.etaSeconds ?: 0L,
                errorMessage = entity.errorMessage,
                totalDurationMs = entity.totalDurationMs,
                bitDepthChange = entity.bitDepthChange,
                resolutionChange = entity.resolutionChange,
                codecChange = entity.codecChange
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val runningJobTasks = ConcurrentHashMap<String, Job>()

    private val wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "DLNAServer:TranscodeWakeLock"
    )

    init {
        scope.launch {
            reEnqueueJobsOnInit()
        }
        scope.launch {
            appSettingsRepository.transcodeSettings
                .map { it.maxParallelJobs }
                .distinctUntilChanged()
                .collect {
                    processQueue()
                }
        }
    }

    private suspend fun reEnqueueJobsOnInit() {
        val existingJobs = transcodeJobDao.getAllJobsSync()
        var hasQueuedOrRunning = false
        for (job in existingJobs) {
            if (job.status == TranscodeJobStatus.RUNNING) {
                if (job.outputFile.exists()) {
                    try { job.outputFile.delete() } catch (_: Throwable) {}
                }
                transcodeJobDao.updateJobStatus(job.id, TranscodeJobStatus.QUEUED, errorMessage = null)
                hasQueuedOrRunning = true
            } else if (job.status == TranscodeJobStatus.QUEUED) {
                hasQueuedOrRunning = true
            }
        }
        if (hasQueuedOrRunning) {
            transcodeServiceLauncher.startService()
            processQueue()
        }
    }

    fun enqueueJob(inputFile: File) {
        val parentDir = inputFile.parentFile ?: return
        val outputFileName = "transcoded_${inputFile.name}"
        val outputFile = File(parentDir, outputFileName)

        val durationMs = getMediaDuration(inputFile)
        val jobId = UUID.randomUUID().toString()

        scope.launch {
            val settings = appSettingsRepository.transcodeSettings.first()
            val option = settings.option

            val isSource10Bit = MediaMetadataUtils.is10Bit(inputFile)
            val isTarget10Bit = isSource10Bit && option.is10BitSupported

            val bitDepthChange = if (isSource10Bit && !isTarget10Bit) {
                "10 bit -> 8 bit"
            } else null

            val sourceDims = MediaMetadataUtils.getVideoDimensions(inputFile)
            val resolutionChange = if (sourceDims != null) {
                val (srcW, srcH) = sourceDims
                val targetW = when (settings.resolution) {
                    TranscodeResolution.RES_4K -> 3840
                    TranscodeResolution.RES_1080P -> 1920
                    TranscodeResolution.RES_720P -> 1280
                    TranscodeResolution.KEEP -> srcW
                }
                if (targetW < srcW) {
                    val calcH = round((srcH.toDouble() * targetW.toDouble() / srcW.toDouble()) / 2.0).toInt() * 2
                    if (targetW != srcW || calcH != srcH) {
                        "${srcW}x${srcH} -> ${targetW}x${calcH}"
                    } else null
                } else null
            } else null

            val sourceCodec = MediaMetadataUtils.getVideoCodecName(inputFile)
            val targetCodec = option.codecName
            val codecChange = if (!sourceCodec.isNullOrBlank() && !sourceCodec.equals(targetCodec, ignoreCase = true)) {
                "$sourceCodec -> $targetCodec"
            } else null

            val entity = TranscodeJobEntity(
                id = jobId,
                inputFilePath = inputFile.absolutePath,
                outputFilePath = outputFile.absolutePath,
                status = TranscodeJobStatus.QUEUED,
                totalDurationMs = durationMs,
                bitDepthChange = bitDepthChange,
                resolutionChange = resolutionChange,
                codecChange = codecChange
            )

            transcodeJobDao.insertJob(entity)
            transcodeServiceLauncher.startService()
            processQueue()
        }
    }

    fun cancelJob(jobId: String) {
        val task = runningJobTasks.remove(jobId)
        task?.cancel()
        scope.launch {
            val targetJob = transcodeJobDao.getJobById(jobId)
            if (targetJob != null && targetJob.outputFile.exists()) {
                try { targetJob.outputFile.delete() } catch (_: Throwable) {}
            }
            transcodeJobDao.deleteJobById(jobId)
            _progressMap.value = _progressMap.value - jobId
            processQueue()
        }
    }

    fun retryJob(jobId: String) {
        scope.launch {
            transcodeJobDao.updateJobStatus(jobId, TranscodeJobStatus.QUEUED, errorMessage = null)
            _progressMap.value = _progressMap.value + (jobId to TranscodeJobProgress(jobId))
            transcodeServiceLauncher.startService()
            processQueue()
        }
    }

    private fun processQueue() {
        scope.launch {
            val settings = appSettingsRepository.transcodeSettings.first()
            val maxParallel = settings.maxParallelJobs

            val currentJobs = transcodeJobDao.getAllJobsSync()
            val currentRunning = currentJobs.count { it.status == TranscodeJobStatus.RUNNING }
            if (currentRunning >= maxParallel) return@launch

            val nextJobs = currentJobs
                .filter { it.status == TranscodeJobStatus.QUEUED && !runningJobTasks.containsKey(it.id) }
                .take(maxParallel - currentRunning)

            for (nextJob in nextJobs) {
                transcodeJobDao.updateJobStatus(nextJob.id, TranscodeJobStatus.RUNNING)
                val updatedEntity = nextJob.copy(status = TranscodeJobStatus.RUNNING)
                val jobTask = launch {
                    executeTranscodeJob(updatedEntity, settings)
                }
                runningJobTasks[nextJob.id] = jobTask
            }
        }
    }

    private suspend fun executeTranscodeJob(job: TranscodeJobEntity, settings: TranscodeSettings) {
        wakeLock.acquire()
        val inputFile = job.inputFile
        val outputFile = job.outputFile
        val option = settings.option

        val isSource10Bit = MediaMetadataUtils.is10Bit(inputFile)
        val hasHw = option.isHardware
        val is10BitEncoding = isSource10Bit && option.is10BitSupported

        val videoEncoder = option.ffmpegEncoder

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

            if (is10BitEncoding) {
                add("-pix_fmt")
                add("yuv420p10le")
            }

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
                transcodeJobDao.deleteJobById(job.id)
                _progressMap.value = _progressMap.value - job.id
            } else {
                val errorLogs = session.allLogs?.filter { log -> log.level.value <= Level.AV_LOG_ERROR.value } ?: emptyList()
                val errorMsg = if (errorLogs.isNotEmpty()) {
                    "FFmpeg exit code: ${session.returnCode}. Logs: ${errorLogs.joinToString("") { it.message }}"
                } else {
                    "FFmpeg exit code: ${session.returnCode}"
                }
                transcodeJobDao.updateJobStatus(
                    job.id,
                    TranscodeJobStatus.FAILED,
                    errorMessage = errorMsg
                )
                if (outputFile.exists()) outputFile.delete()
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            transcodeJobDao.updateJobStatus(job.id, TranscodeJobStatus.FAILED, errorMessage = e.message)
            if (outputFile.exists()) outputFile.delete()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            runningJobTasks.remove(job.id)
            processQueue()
        }
    }

    private fun updateJobProgress(jobId: String, progress: Float, speed: String, eta: String, etaSeconds: Long) {
        _progressMap.value = _progressMap.value + (
            jobId to TranscodeJobProgress(
                jobId = jobId,
                progress = progress,
                speed = speed,
                eta = eta,
                etaSeconds = etaSeconds
            )
        )
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
