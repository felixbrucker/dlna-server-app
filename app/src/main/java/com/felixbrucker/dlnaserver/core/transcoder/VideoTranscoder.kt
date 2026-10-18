package com.felixbrucker.dlnaserver.core.transcoder

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
        hasHwSupport: Boolean = HardwareCodecUtils.hasHardwareEncoderSupport(option),
        onStart: (() -> Unit)? = null,
        onProgress: (() -> Unit)? = null
    ): Boolean {
        if (!inputFile.exists() || !inputFile.isFile) {
            return false
        }

        if (option == TranscodeOption.NONE) {
            onStart?.invoke()
            streamRawFile(inputFile, outputStream, onProgress)
            return true
        }

        val videoEncoder = if (hasHwSupport && !option.ffmpegEncoderHw.isNullOrBlank()) {
            option.ffmpegEncoderHw
        } else {
            option.ffmpegEncoderSw ?: "copy"
        }

        val command = mutableListOf(
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "error",
            "-i", inputFile.absolutePath,
            "-map", "0:v:0",
            "-map", "0:a?",
            "-map", "0:s?",
            "-c:v", videoEncoder,
            "-c:a", "copy",
            "-c:s", "copy",
            "-f", "matroska",
            "pipe:1"
        )

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
            streamRawFile(inputFile, outputStream, onProgress)
            true
        } finally {
            process?.destroy()
        }
    }

    fun streamRawFile(
        inputFile: File,
        outputStream: OutputStream,
        onProgress: (() -> Unit)? = null
    ) {
        if (!inputFile.exists() || !inputFile.isFile) return

        FileInputStream(inputFile).use { fileStream ->
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
