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

        return try {
            if (context != null) {
                val configClass = try {
                    Class.forName("dev.ffmpegkit.FFmpegKitConfig")
                } catch (_: ClassNotFoundException) {
                    Class.forName("com.arthenica.ffmpegkit.FFmpegKitConfig")
                }
                val ffmpegKitClass = try {
                    Class.forName("dev.ffmpegkit.FFmpegKit")
                } catch (_: ClassNotFoundException) {
                    Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
                }

                val registerPipeMethod = configClass.getMethod("registerNewFFmpegPipe", Context::class.java)
                val pipePath = registerPipeMethod.invoke(null, context) as String

                val closePipeMethod = configClass.getMethod("closeFFmpegPipe", String::class.java)

                val seekArg = if (startSeconds > 0) "-ss $startSeconds " else ""
                val command = "-hide_banner -loglevel error $seekArg-i \"${inputFile.absolutePath}\" -map 0:v:0 -map 0:a? -map 0:s? -c:v $videoEncoder -c:a copy -c:s copy -f matroska \"$pipePath\""

                onStart?.invoke()

                val executeAsyncMethod = ffmpegKitClass.methods.firstOrNull { it.name == "executeAsync" }
                executeAsyncMethod?.invoke(null, command, null)

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

                closePipeMethod.invoke(null, pipePath)
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
