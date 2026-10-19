package com.felixbrucker.dlnaserver.core.util

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

fun File.notifySystemOfNewFile(context: Context) {
    if (!exists()) return
    try {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(absolutePath),
            null,
            null
        )
    } catch (_: Throwable) {
    }
}
