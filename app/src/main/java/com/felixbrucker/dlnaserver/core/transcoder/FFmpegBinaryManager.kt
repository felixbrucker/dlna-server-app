package com.felixbrucker.dlnaserver.core.transcoder

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FFmpegBinaryManager {

    @Volatile
    private var cachedBinary: File? = null

    fun getExecutable(context: Context? = null): File? {
        cachedBinary?.let { if (it.exists() && it.isFile && it.canExecute() && it.length() > 0) return it }

        val candidates = mutableListOf<File>()

        if (context != null) {
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            candidates.add(File(nativeDir, "libffmpeg.so"))
            candidates.add(File(nativeDir, "ffmpeg"))

            val appFile = File(context.filesDir, "ffmpeg")
            if (extractAssetIfNeeded(context, appFile)) {
                candidates.add(appFile)
            }
        }

        candidates.add(File("/system/bin/ffmpeg"))
        candidates.add(File("/system/xbin/ffmpeg"))

        val pathEnv = System.getenv("PATH") ?: ""
        pathEnv.split(":").forEach { pathDir ->
            if (pathDir.isNotBlank()) {
                candidates.add(File(pathDir, "ffmpeg"))
            }
        }

        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                if (!candidate.canExecute()) {
                    candidate.setExecutable(true, false)
                }
                if (candidate.canExecute()) {
                    cachedBinary = candidate
                    return candidate
                }
            }
        }

        return null
    }

    private fun extractAssetIfNeeded(context: Context, targetFile: File): Boolean {
        return try {
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.setExecutable(true, false)
                return targetFile.canExecute()
            }

            val inputStream = context.assets.open("ffmpeg")
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
            inputStream.close()

            targetFile.setExecutable(true, false)
            targetFile.canExecute()
        } catch (_: Throwable) {
            false
        }
    }

    fun setCachedExecutable(file: File) {
        if (file.exists() && file.isFile && file.length() > 0) {
            if (!file.canExecute()) {
                file.setExecutable(true, false)
            }
            cachedBinary = file
        }
    }
}
