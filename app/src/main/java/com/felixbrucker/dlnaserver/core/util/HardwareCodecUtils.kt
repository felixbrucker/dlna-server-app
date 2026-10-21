package com.felixbrucker.dlnaserver.core.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.felixbrucker.dlnaserver.core.model.AudioTranscodeOption
import com.felixbrucker.dlnaserver.core.model.VideoTranscodeOption

fun Int.is10BitProfileFor(codec: String): Boolean {
    val isApi29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    return when (codec.lowercase()) {
        "x265" -> this == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
            this == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
            (isApi29 && this == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)
        "x264" -> this == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10
        "VP9" -> this == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
            this == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR ||
            this == MediaCodecInfo.CodecProfileLevel.VP9Profile3 ||
            this == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR
        "AV1" -> isApi29 && (this == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
            this == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
            this == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus)
        else -> false
    }
}

object HardwareCodecUtils {
    fun hasHardwareEncoderSupport(mimeType: String?): Boolean {
        return !mimeType.isNullOrBlank() && try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos ?: return false

            infos.any { info -> info.isHardwareAcceleratedEncoderForMimeType(mimeType) }
        } catch (_: Throwable) {
            false
        }
    }

    fun hasHardwareEncoderSupport(option: VideoTranscodeOption): Boolean {
        return hasHardwareEncoderSupport(option.mimeType)
    }

    fun hasHardwareEncoderSupport(option: AudioTranscodeOption): Boolean {
        return hasHardwareEncoderSupport(option.mimeType)
    }

    /**
     * Note: This method is currently unused until FFmpeg provides 10-bit support for MediaCodec encoders.
     */
    fun has10BitEncoderSupport(mimeType: String?): Boolean {
        return !mimeType.isNullOrBlank() && try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos ?: return false

            infos.any { info ->
                info.isHardwareAcceleratedEncoderForMimeType(mimeType) && info.supports10Bit(mimeType)
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Note: This method is currently unused until FFmpeg provides 10-bit support for MediaCodec encoders.
     */
    fun has10BitEncoderSupport(option: VideoTranscodeOption): Boolean {
        return has10BitEncoderSupport(option.mimeType)
    }
}

fun MediaCodecInfo?.isHardwareAcceleratedEncoderForMimeType(mimeType: String): Boolean {
    return this != null && isEncoder && supportsMimeType(mimeType) && isHardwareAcceleratedCompat()
}

fun MediaCodecInfo.supportsMimeType(mimeType: String): Boolean {
    return supportedTypes?.any { it.equals(mimeType, ignoreCase = true) } == true
}

fun MediaCodecInfo.isHardwareAcceleratedCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated
    } else {
        !name.startsWith("OMX.google.", ignoreCase = true) &&
            !name.startsWith("c2.android.", ignoreCase = true)
    }
}

fun MediaCodecInfo.supports10Bit(mimeType: String): Boolean {
    return try {
        val caps = getCapabilitiesForType(mimeType) ?: return false
        val profileLevels = caps.profileLevels ?: emptyArray()
        val has10BitProfile = profileLevels.any { pl ->
            pl.profile.is10BitProfileFor(mimeType)
        }
        val colorFormats = caps.colorFormats ?: intArrayOf()
        val isApi33 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val has10BitColorFormat = colorFormats.any { cf ->
            cf == 0x7F000001 || (isApi33 && cf == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010)
        }
        has10BitProfile || has10BitColorFormat
    } catch (_: Throwable) {
        false
    }
}
