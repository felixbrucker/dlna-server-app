package com.felixbrucker.dlnaserver.core.util

import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File

data class ExtractedMediaMetadata(
    val durationFormatted: String? = null,
    val resolution: String? = null,
    val bitrate: Long? = null,
    val sampleFrequency: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null
)

object MediaMetadataUtils {

    fun extractMetadata(file: File): ExtractedMediaMetadata {
        if (!file.exists() || !file.isFile) return ExtractedMediaMetadata()

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val durationStr = durationMs?.let { formatDuration(it) }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val resolutionStr = if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                "${width}x${height}"
            } else null

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val sampleFrequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull()
            } else null

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            ExtractedMediaMetadata(
                durationFormatted = durationStr,
                resolution = resolutionStr,
                bitrate = bitrate,
                sampleFrequency = sampleFrequency,
                artist = artist,
                album = album,
                genre = genre
            )
        } catch (_: Exception) {
            ExtractedMediaMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val secondsTotal = durationMs / 1000
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        val millis = durationMs % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
