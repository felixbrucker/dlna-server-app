package com.felixbrucker.dlnaserver.core.transcoder

import android.content.Context
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object VideoTranscoder {

    fun transcodeToStream(
        inputFile: File,
        option: TranscodeOption,
        outputStream: OutputStream,
        startSeconds: Long = 0L,
        context: Context? = null,
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

        val videoEncoder = if (hasHwSupport && !option.ffmpegEncoderHw.isNullOrBlank()) {
            option.ffmpegEncoderHw
        } else {
            option.ffmpegEncoderSw ?: "copy"
        }

        val ffmpegBin = FFmpegBinaryManager.getExecutable(context)?.absolutePath ?: "ffmpeg"

        val command = mutableListOf<String>()
        command.add(ffmpegBin)
        command.add("-hide_banner")
        command.add("-loglevel")
        command.add("error")

        if (startSeconds > 0) {
            command.add("-ss")
            command.add(startSeconds.toString())
        }

        command.add("-i")
        command.add(inputFile.absolutePath)
        command.add("-map")
        command.add("0:v:0")
        command.add("-map")
        command.add("0:a?")
        command.add("-map")
        command.add("0:s?")
        command.add("-c:v")
        command.add(videoEncoder)
        command.add("-c:a")
        command.add("copy")
        command.add("-c:s")
        command.add("copy")
        command.add("-f")
        command.add("matroska")
        command.add("pipe:1")

        var process: Process? = null
        return try {
            val processBuilder = ProcessBuilder(command)
            process = processBuilder.start()

            val processErrorStream = process.errorStream
            val errorGobbler = Thread {
                try {
                    val errBuffer = ByteArray(4096)
                    while (processErrorStream.read(errBuffer) != -1) {
                    }
                } catch (_: Throwable) {
                }
            }
            errorGobbler.isDaemon = true
            errorGobbler.start()

            onStart?.invoke()

            val processInputStream = process.inputStream
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            while (processInputStream.read(buffer).also { bytesRead = it } != -1) {
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
            process.waitFor()
            true
        } catch (_: Throwable) {
            onStart?.invoke()
            streamRawFile(inputFile, outputStream, startSeconds = startSeconds, onProgress = onProgress)
            true
        } finally {
            process?.destroy()
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
