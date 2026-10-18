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

        onStart?.invoke()
        streamRawFile(inputFile, outputStream, onProgress)
        return true
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
