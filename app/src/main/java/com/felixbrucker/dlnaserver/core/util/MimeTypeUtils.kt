package com.felixbrucker.dlnaserver.core.util

import java.io.File
import java.util.Locale

object MimeTypeUtils {

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val extension = file.extension.lowercase(Locale.ROOT)
        return when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "3gp", "3g2" -> "video/3gpp"
            "ts", "m2ts" -> "video/mp2t"
            "mpg", "mpeg" -> "video/mpeg"

            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "ac3" -> "audio/ac3"
            "eac3" -> "audio/eac3"

            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"

            else -> "application/octet-stream"
        }
    }

    fun getUpnpClass(file: File): String {
        if (file.isDirectory) return "object.container.storageFolder"
        val mimeType = getMimeType(file)
        return when {
            mimeType.startsWith("video/") -> "object.item.videoItem"
            mimeType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            mimeType.startsWith("image/") -> "object.item.imageItem.photo"
            else -> "object.item.mediaItem"
        }
    }

    fun getDlnaProfile(file: File): String {
        if (file.isDirectory) return ""
        val extension = file.extension.lowercase(Locale.ROOT)
        return when (extension) {
            "mkv" -> "MATROSKA"
            "mp4", "m4v" -> "AVC_MP4_MP_SD"
            "mpg", "mpeg", "ts", "m2ts" -> "MPEG_PS_PAL"
            "jpg", "jpeg" -> "JPEG_LRG"
            "png" -> "PNG_LRG"
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            else -> ""
        }
    }
}
