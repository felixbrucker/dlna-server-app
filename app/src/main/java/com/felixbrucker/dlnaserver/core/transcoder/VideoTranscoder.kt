package com.felixbrucker.dlnaserver.core.transcoder

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object VideoTranscoder {

    fun transcodeToStream(
        context: Context,
        inputFile: File,
        option: TranscodeOption,
        outputStream: OutputStream,
        startSeconds: Long = 0L,
        hasHwSupport: Boolean = HardwareCodecUtils.hasHardwareEncoderSupport(option),
        onStart: (() -> Unit)? = null,
        onProgress: (() -> Unit)? = null
    ): Boolean {
        if (!inputFile.exists() || !inputFile.isFile) {
            return false
        }

        if (option == TranscodeOption.NONE) {
            onStart?.invoke()
            streamRawFile(inputFile, outputStream, startSeconds = startSeconds, onProgress = onProgress)
            return true
        }

        onStart?.invoke()

        val videoEncoder = if (hasHwSupport && !option.ffmpegEncoderHw.isNullOrBlank()) {
            option.ffmpegEncoderHw
        } else {
            option.ffmpegEncoderSw ?: "copy"
        }

        return try {
            val pipePath = FFmpegKitConfig.registerNewFFmpegPipe(context)
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
                add("-c:v")
                add(videoEncoder)
                add("-c:a")
                add("copy")
                add("-c:s")
                add("copy")
                add("-flush_packets")
                add("1")
                add("-f")
                add("matroska")
                add("-live")
                add("true")
                add(pipePath)
            }.toTypedArray()

            val session = FFmpegKit.executeWithArgumentsAsync(argumentsList) {
                FFmpegKitConfig.closeFFmpegPipe(pipePath)
            }

            val pipeFile = File(pipePath)
            if (pipeFile.exists()) {
                FileInputStream(pipeFile).use { pipeStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (pipeStream.read(buffer).also { bytesRead = it } != -1) {
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

            session?.cancel()
            FFmpegKitConfig.closeFFmpegPipe(pipePath)
            true
        } catch (_: Throwable) {
            true
        }
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
}
