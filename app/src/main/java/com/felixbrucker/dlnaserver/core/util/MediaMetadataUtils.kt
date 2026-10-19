package com.felixbrucker.dlnaserver.core.util

import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File

import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat

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

    fun is10Bit(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        if (transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084) {
                            return true
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                        val profile = format.getInteger(MediaFormat.KEY_PROFILE)
                        if (is10BitProfile(mime, profile)) {
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
        } catch (_: Throwable) {
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pixelFormat = retriever.extractMetadata(39) // METADATA_KEY_VIDEO_PIXEL_FORMAT
                if (!pixelFormat.isNullOrEmpty() && (pixelFormat.contains("10") || pixelFormat.contains("p10"))) {
                    return true
                }
                val colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                if (colorTransfer == "6" || colorTransfer == "7") {
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun is10BitProfile(mimeType: String, profile: Int): Boolean {
        return when (mimeType.lowercase()) {
            "video/hevc" -> profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            "video/avc" -> profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10
            "video/x-vnd.on2.vp9" -> profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
                profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR ||
                profile == MediaCodecInfo.CodecProfileLevel.VP9Profile3 ||
                profile == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR
            "video/av01" -> profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
                profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
                profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
            else -> false
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
