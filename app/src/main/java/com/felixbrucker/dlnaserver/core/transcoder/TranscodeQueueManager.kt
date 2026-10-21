package com.felixbrucker.dlnaserver.core.transcoder

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.Statistics
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.core.extensions.deleteIfExists
import com.felixbrucker.dlnaserver.core.extensions.notifySystemOfNewFile
import com.felixbrucker.dlnaserver.core.model.AudioTranscodeOption
import com.felixbrucker.dlnaserver.core.model.SubtitleTranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import com.felixbrucker.dlnaserver.core.model.VideoTranscodeOption
import com.felixbrucker.dlnaserver.core.util.MediaMetadataUtils
import com.felixbrucker.dlnaserver.feature.service.TranscodeServiceLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.round

@Singleton
class TranscodeQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
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
                isSource10Bit = entity.isSource10Bit,
                bitDepthChange = entity.bitDepthChange,
                resolutionChange = entity.resolutionChange,
                videoCodecChange = entity.videoCodecChange,
                audioCodecChange = entity.audioCodecChange,
                subtitleCodecChange = entity.subtitleCodecChange
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
        val jobId = UUID.randomUUID().toString()

        scope.launch {
            val settings = appSettingsRepository.transcodeSettings.first()
            val videoTranscodeOption = settings.videoTranscodeOption

            val metadata = MediaMetadataUtils.extractVideoMetadata(inputFile)
            val firstVideo = metadata.videoTracks.firstOrNull()

            val isSource10Bit = firstVideo?.is10Bit ?: false
            val isTarget10Bit = isSource10Bit && videoTranscodeOption.is10BitSupported

            var bitDepthChange: String? = null
            var resolutionChange: String? = null
            var videoCodecChange: String? = null
            if (videoTranscodeOption != VideoTranscodeOption.KEEP) {
                bitDepthChange = if (isSource10Bit && !isTarget10Bit) {
                    "10 bit → 8 bit"
                } else null

                resolutionChange = if (firstVideo != null && firstVideo.width > 0) {
                    val srcW = firstVideo.width
                    val srcH = firstVideo.height
                    val targetW = when (settings.resolution) {
                        TranscodeResolution.RES_4K -> 3840
                        TranscodeResolution.RES_1080P -> 1920
                        TranscodeResolution.RES_720P -> 1280
                        TranscodeResolution.KEEP -> srcW
                    }
                    if (targetW < srcW) {
                        val calcH = round((srcH.toDouble() * targetW.toDouble() / srcW.toDouble()) / 2.0).toInt() * 2
                        "${srcW}x${srcH} → ${targetW}x${calcH}"
                    } else null
                } else null

                val sourceVideoCodec = firstVideo?.codec
                val targetVideoCodec = videoTranscodeOption.codecName
                videoCodecChange = if (!sourceVideoCodec.isNullOrBlank() && !sourceVideoCodec.equals(targetVideoCodec, ignoreCase = true)) {
                    "$sourceVideoCodec → $targetVideoCodec"
                } else null
            }

            val sourceAudioCodecs = metadata.audioTracks.map { it.codec }.distinct()
            val targetAudioCodec = settings.audioTranscodeOption.codecName
            val audioCodecChange = if (settings.audioTranscodeOption != AudioTranscodeOption.KEEP && sourceAudioCodecs.isNotEmpty()) {
                if (!sourceAudioCodecs.all { it.equals(targetAudioCodec, ignoreCase = true) }) {
                    "${sourceAudioCodecs.joinToString(", ")} → $targetAudioCodec"
                } else null
            } else null

            val sourceSubCodecs = metadata.subtitleTracks.map { it.codec }.distinct()
            val targetSubCodec = settings.subtitleTranscodeOption.codecName
            val subtitleCodecChange = if (settings.subtitleTranscodeOption != SubtitleTranscodeOption.KEEP && sourceSubCodecs.isNotEmpty()) {
                if (!sourceSubCodecs.all { it.equals(targetSubCodec, ignoreCase = true) }) {
                    "${sourceSubCodecs.joinToString(", ")} → $targetSubCodec"
                } else null
            } else null

            val entity = TranscodeJobEntity(
                id = jobId,
                inputFilePath = inputFile.absolutePath,
                outputFilePath = outputFile.absolutePath,
                status = TranscodeJobStatus.QUEUED,
                totalDurationMs = metadata.duration?.inWholeMilliseconds ?: 0,
                isSource10Bit = isSource10Bit,
                bitDepthChange = bitDepthChange,
                resolutionChange = resolutionChange,
                videoCodecChange = videoCodecChange,
                audioCodecChange = audioCodecChange,
                subtitleCodecChange = subtitleCodecChange
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
            _progressMap.value -= jobId
            processQueue()
        }
    }

    fun retryJob(jobId: String) {
        scope.launch {
            transcodeJobDao.updateJobStatus(jobId, TranscodeJobStatus.QUEUED, errorMessage = null)
            _progressMap.value += (jobId to TranscodeJobProgress(jobId))
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

    @SuppressLint("WakelockTimeout")
    private suspend fun executeTranscodeJob(job: TranscodeJobEntity, settings: TranscodeSettings) {
        wakeLock.acquire()
        val outputFile = job.outputFile
        val argumentsList = job.makeFfmpegCommandArguments(settings)

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
                            updateJobProgress(statistics.makeTranscodeJobProgress(job))
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
                outputFile.notifySystemOfNewFile(context)
                transcodeJobDao.deleteJobById(job.id)
                _progressMap.value -= job.id
            } else {
                val errorLogLines = session.getAllErrorLogLines()
                val errorMsg = if (errorLogLines.isNotEmpty()) {
                    "FFmpeg exit code: ${session.returnCode}. Logs: ${errorLogLines.joinToString("")}"
                } else {
                    "FFmpeg exit code: ${session.returnCode}"
                }
                transcodeJobDao.updateJobStatus(
                    job.id,
                    TranscodeJobStatus.FAILED,
                    errorMessage = errorMsg
                )
                outputFile.deleteIfExists()
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            transcodeJobDao.updateJobStatus(job.id, TranscodeJobStatus.FAILED, errorMessage = e.message)
            outputFile.deleteIfExists()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            runningJobTasks.remove(job.id)
            processQueue()
        }
    }

    private fun updateJobProgress(jobProgress: TranscodeJobProgress) {
        _progressMap.value += (jobProgress.jobId to jobProgress)
    }
}

fun TranscodeJobEntity.makeFfmpegCommandArguments(settings: TranscodeSettings): Array<String> {
    val inputFile = inputFile
    val outputFile = outputFile
    val videoTranscodeOption = settings.videoTranscodeOption

    val isSource10Bit = isSource10Bit
    val hasHw = videoTranscodeOption.isHardware
    val is10BitEncoding = isSource10Bit && videoTranscodeOption.is10BitSupported

    return mutableListOf<String>().apply {
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

        add("-c:v")
        add(videoTranscodeOption.ffmpegEncoder)

        if (videoTranscodeOption != VideoTranscodeOption.KEEP) {
            settings.resolution.scaleFilter?.let { filter ->
                add("-vf")
                add(filter)
            }

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

            if (!hasHw && videoTranscodeOption.supportsPresets) {
                add("-preset")
                add(settings.preset.getFfmpegPreset(videoTranscodeOption))
            }

            add("-g")
            add("250")
            add("-keyint_min")
            add("60")
        }

        add("-c:a")
        add(settings.audioTranscodeOption.ffmpegEncoder)
        add("-c:s")
        add(settings.subtitleTranscodeOption.ffmpegEncoder)
        add("-f")
        add("matroska")
        add("-y")
        add(outputFile.absolutePath)
    }.toTypedArray()
}

fun Statistics.makeTranscodeJobProgress(job: TranscodeJobEntity): TranscodeJobProgress {
    val timeMs = time
    val totalDuration = job.totalDurationMs
    val progress = if (totalDuration > 0) {
        (timeMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val speedFormatted = if (speed > 0) {
        String.format(Locale.getDefault(), "%.1fx", speed)
    } else "1.0x"
    val remainingSeconds = if (speed > 0 && totalDuration > timeMs) {
        ((totalDuration - timeMs) / speed).toLong() / 1000
    } else 0L

    val etaMinutes = remainingSeconds / 60
    val etaSecs = remainingSeconds % 60
    val etaFormatted = String.format(Locale.getDefault(),"%02d:%02d", etaMinutes, etaSecs)

    return TranscodeJobProgress(
        jobId = job.id,
        progress = progress,
        speed = speedFormatted,
        eta = etaFormatted,
        etaSeconds = remainingSeconds
    )
}

fun FFmpegSession.getAllErrorLogLines(): List<String> {
    val errorLogs = allLogs?.filter { log -> log.level.value <= Level.AV_LOG_ERROR.value } ?: emptyList()

    return errorLogs.map { it.message }
}
