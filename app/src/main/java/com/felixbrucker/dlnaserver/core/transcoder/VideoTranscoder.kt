package com.felixbrucker.dlnaserver.core.transcoder

import android.os.ParcelFileDescriptor
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class VideoTranscoder @Inject constructor() {

    suspend fun transcodeToStream(
        inputFile: File,
        option: TranscodeOption,
        outputStream: OutputStream,
        startSeconds: Long = 0L,
        hasHwSupport: Boolean = HardwareCodecUtils.hasHardwareEncoderSupport(option),
        onStart: (() -> Unit),
        onProgress: (() -> Unit)
    ) {
        logDebug("VideoTranscoder", "Transcoding video ${inputFile.name} to stream...")
        if (!inputFile.exists() || !inputFile.isFile) {
            return
        }

        onStart.invoke()

        if (option == TranscodeOption.NONE) {
            streamRawFile(inputFile, outputStream, startSeconds = startSeconds, onProgress = onProgress)
            return
        }

        val videoEncoder = if (hasHwSupport && !option.ffmpegEncoderHw.isNullOrBlank()) {
            option.ffmpegEncoderHw
        } else {
            option.ffmpegEncoderSw ?: "copy"
        }

        try {
            val pipe = try {
                ParcelFileDescriptor.createPipe()
            } catch (e: Throwable) {
                logDebug("VideoTranscoder", "ParcelFileDescriptor pipe unavailable: ${e.message}")
                null
            }

            if (pipe == null) {
                return
            }

            val readSide = pipe[0]
            val writeSide = pipe[1]

            var activeSessionId: Long? = null

            coroutineScope {
                val readerJob = launch(Dispatchers.IO) {
                    try {
                        ParcelFileDescriptor.AutoCloseInputStream(readSide).use { inputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var lastUpdate = System.currentTimeMillis()
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                logDebug("VideoTranscoder", "Sending $bytesRead bytes to client (${inputFile.name}) ...")
                                outputStream.write(buffer, 0, bytesRead)
                                outputStream.flush()

                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= 5000) {
                                    onProgress.invoke()
                                    lastUpdate = now
                                }
                            }
                            onProgress.invoke()
                            logDebug("VideoTranscoder", "Reader finished for ${inputFile.name}.")
                        }
                    } catch (e: SocketException) {
                        logDebug("VideoTranscoder", "Client disconnected for ${inputFile.name} (${e.message})")
                        activeSessionId?.let {
                            try { FFmpegKit.cancel(it) } catch (_: Throwable) {}
                        }
                    } catch (e: Exception) {
                        logError("VideoTranscoder", "Error during reading: ${e.message}", e)
                    }
                }

                try {
                    writeSide.use { ws ->
                        val argumentsList = mutableListOf<String>().apply {
                            add("-hide_banner")
                            add("-loglevel")
                            add("error")
                            if (startSeconds > 0) {
                                add("-ss")
                                add(startSeconds.toString())
                            }
                            add("-i")
                            add(inputFile.absolutePath)
                            add("-map")
                            add("0:v:0")
                            add("-map")
                            add("0:a?")
                            add("-map")
                            add("0:s?")
                            add("-vf")
                            add("scale=1920:1080")
                            add("-c:v")
                            add(videoEncoder)
                            add("-b:v")
                            add("6M")
                            add("-c:a")
                            add("copy")
                            add("-c:s")
                            add("copy")
                            add("-f")
                            add("matroska")
                            add("-y")
                            add("pipe:${ws.fd}")
                        }.toTypedArray()

                        logDebug("VideoTranscoder", "Executing FFmpeg with arguments: ${argumentsList.joinToString(" ")}")

                        try {
                            val session = suspendCancellableCoroutine { continuation ->
                                val asyncSession = FFmpegKit.executeWithArgumentsAsync(
                                    argumentsList,
                                    { completedSession -> continuation.resume(completedSession) },
                                    null,
                                    null
                                )
                                activeSessionId = asyncSession.sessionId

                                continuation.invokeOnCancellation {
                                    try { FFmpegKit.cancel(asyncSession.sessionId) } catch (_: Throwable) {}
                                }
                            }

                            logDebug("VideoTranscoder", "FFmpeg session returned: $session")

                            if (!session.returnCode.isValueSuccess) {
                                val logs = session.allLogsAsString
                                logError("VideoTranscoder", "FFmpeg encoding failed: $logs")
                            }
                        } catch (e: Throwable) {
                            logError("VideoTranscoder", "FFmpeg execution error: ${e.message}", e)
                        }
                    }
                } finally {
                    readerJob.join()
                }
            }
        } catch (e: Throwable) {
            logError("VideoTranscoder", "Pipe transcode setup failed: ${e.message}", e)
        }

        logDebug("VideoTranscoder", "Transcoding completed for ${inputFile.name}.")
    }

    fun streamRawFile(
        inputFile: File,
        outputStream: OutputStream,
        startSeconds: Long = 0L,
        onProgress: (() -> Unit)? = null
    ) {
        if (!inputFile.exists() || !inputFile.isFile) return

        FileInputStream(inputFile).use { fileStream ->
            if (startSeconds > 0) {
                val fileLength = inputFile.length()
                val estimatedByteOffset = maxOf(0L, (fileLength * startSeconds / 3600))
                if (estimatedByteOffset < fileLength) {
                    fileStream.skip(estimatedByteOffset)
                }
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            while (fileStream.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush()

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 5000) {
                        onProgress?.invoke()
                        lastUpdate = now
                    }
                }
            }
            onProgress?.invoke()
        }
    }

    private fun logDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
        }
    }

    private fun logError(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (_: Throwable) {
        }
    }
}
