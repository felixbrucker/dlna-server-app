package com.felixbrucker.dlnaserver.core.util

import android.media.MediaExtractor
import android.media.MediaFormat
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
    val genre: String? = null,
    val is10Bit: Boolean = false
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
            val is10Bit = is10Bit(file)

            ExtractedMediaMetadata(
                durationFormatted = durationStr,
                resolution = resolutionStr,
                bitrate = bitrate,
                sampleFrequency = sampleFrequency,
                artist = artist,
                album = album,
                genre = genre,
                is10Bit = is10Bit
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

    fun getVideoDimensions(file: File): Pair<Int, Int>? {
        if (!file.exists() || !file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) Pair(w, h) else null
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    fun getVideoCodecName(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    return when (mime.lowercase()) {
                        "video/avc" -> "x264"
                        "video/hevc" -> "x265"
                        "video/x-vnd.on2.vp9" -> "VP9"
                        "video/av01" -> "AV1"
                        "video/mp4v-es" -> "MPEG4"
                        "video/3gpp" -> "H263"
                        else -> mime.removePrefix("video/").uppercase()
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    fun is10Bit(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                        val profile = format.getInteger(MediaFormat.KEY_PROFILE)
                        if (profile.is10BitProfileFor(mime)) {
                            return true
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        if (transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084) {
                            return true
                        }
                    }
                    if (format.containsKey("bits-per-sample") && format.getInteger("bits-per-sample") == 10) {
                        return true
                    }
                    if (format.containsKey("bit-depth") && format.getInteger("bit-depth") == 10) {
                        return true
                    }
                }
            }
            false
        } catch (_: Throwable) {
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {
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
