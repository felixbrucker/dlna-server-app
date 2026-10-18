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

        return try {
            if (context != null) {
                val pipePath = FFmpegKitConfig.registerNewFFmpegPipe(context)
                val seekArg = if (startSeconds > 0) "-ss $startSeconds " else ""
                val command = "-hide_banner -loglevel error $seekArg-i \"${inputFile.absolutePath}\" -map 0:v:0 -map 0:a? -map 0:s? -c:v $videoEncoder -c:a copy -c:s copy -flush_packets 1 -f matroska -live true \"$pipePath\""

                onStart?.invoke()

                val session = FFmpegKit.executeAsync(command) {
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
            } else {
                onStart?.invoke()
                streamRawFile(inputFile, outputStream, startSeconds = startSeconds, onProgress = onProgress)
                true
            }
        } catch (_: Throwable) {
            onStart?.invoke()
            streamRawFile(inputFile, outputStream, startSeconds = startSeconds, onProgress = onProgress)
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
