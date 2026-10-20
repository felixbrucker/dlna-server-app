package com.felixbrucker.dlnaserver.core.util

import android.media.MediaMetadataRetriever
import android.os.Build
import com.arthenica.ffmpegkit.FFprobeKit
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

data class VideoTrack(
    val codec: String,
    val width: Int,
    val height: Int,
    val is10Bit: Boolean
)

data class AudioTrack(
    val codec: String
)

data class SubtitleTrack(
    val codec: String
)

data class MediaTrackMetadata(
    val videoTracks: List<VideoTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList()
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

            val trackMetadata = extractTrackMetadata(file)
            val is10Bit = trackMetadata.videoTracks.firstOrNull()?.is10Bit ?: false

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

    fun extractTrackMetadata(file: File): MediaTrackMetadata {
        if (!file.exists() || !file.isFile) return MediaTrackMetadata()

        return try {
            val infoSession = FFprobeKit.getMediaInformation(file.absolutePath)
            val streams = infoSession?.mediaInformation?.streams ?: emptyList()
            val videoTracks = mutableListOf<VideoTrack>()
            val audioTracks = mutableListOf<AudioTrack>()
            val subtitleTracks = mutableListOf<SubtitleTrack>()

            for (stream in streams) {
                if (stream == null) continue
                val type = stream.type ?: continue
                val codec = stream.codec ?: continue

                if (type.equals("video", ignoreCase = true)) {
                    val videoCodec = when (codec.lowercase()) {
                        "h264" -> "x264"
                        "hevc" -> "x265"
                        else -> codec.uppercase()
                    }
                    val profile = stream.getNumberProperty("profile")?.toInt() ?: 0
                    val pixFmt = stream.getStringProperty("pix_fmt") ?: stream.format ?: ""
                    val is10Bit = pixFmt == "yuv420p10le" || profile.is10BitProfileFor(videoCodec)
                    val width = stream.width?.toInt() ?: stream.getNumberProperty("width")?.toInt() ?: 0
                    val height = stream.height?.toInt() ?: stream.getNumberProperty("height")?.toInt() ?: 0

                    videoTracks.add(VideoTrack(videoCodec, width, height, is10Bit))
                } else if (type.equals("audio", ignoreCase = true)) {
                    val audioCodec = when (codec.lowercase()) {
                        "vorbis" -> "Vorbis"
                        "opus" -> "Opus"
                        else -> codec.uppercase()
                    }
                    audioTracks.add(AudioTrack(audioCodec))
                } else if (type.equals("subtitle", ignoreCase = true)) {
                    val subtitleCodec = when (codec.lowercase()) {
                        "webvtt" -> "WebVTT"
                        "ass", "ssa" -> "ASS"
                        "subrip" -> "SRT"
                        else -> codec.uppercase()
                    }
                    subtitleTracks.add(SubtitleTrack(subtitleCodec))
                }
            }

            MediaTrackMetadata(
                videoTracks = videoTracks,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks
            )
        } catch (_: Throwable) {
            MediaTrackMetadata()
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

    private fun formatDuration(durationMs: Long): String {
        val secondsTotal = durationMs / 1000
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        val millis = durationMs % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
