package com.felixbrucker.dlnaserver.core.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.felixbrucker.dlnaserver.core.model.TranscodeOption

object HardwareCodecUtils {

    fun isEncoderSupported(mimeType: String?): Boolean {
        return hasHardwareEncoderSupport(mimeType)
    }

    fun hasHardwareEncoderSupport(mimeType: String?): Boolean {
        return !mimeType.isNullOrBlank() && try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos ?: return false

            infos.any { info ->
                info != null && info.isEncoder && supportsMimeType(info, mimeType) && isHardwareAccelerated(info)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun hasHardwareEncoderSupport(option: TranscodeOption): Boolean {
        return hasHardwareEncoderSupport(option.mimeType)
    }

    fun has10BitEncoderSupport(mimeType: String?): Boolean {
        return !mimeType.isNullOrBlank() && try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos ?: return false

            infos.any { info ->
                info != null && info.isEncoder && supportsMimeType(info, mimeType) && isHardwareAccelerated(info) && supports10Bit(info, mimeType)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun has10BitEncoderSupport(option: TranscodeOption): Boolean {
        return has10BitEncoderSupport(option.mimeType)
    }

    private fun supports10Bit(info: MediaCodecInfo, mimeType: String): Boolean {
        return try {
            val caps = info.getCapabilitiesForType(mimeType) ?: return false
            val profileLevels = caps.profileLevels ?: emptyArray()
            val has10BitProfile = profileLevels.any { pl ->
                is10BitProfileLevel(mimeType, pl.profile)
            }
            val colorFormats = caps.colorFormats ?: intArrayOf()
            val has10BitColorFormat = colorFormats.any { cf ->
                cf == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010 || cf == 2130706433
            }
            has10BitProfile || has10BitColorFormat
        } catch (_: Throwable) {
            false
        }
    }

    private fun is10BitProfileLevel(mimeType: String, profile: Int): Boolean {
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

    private fun supportsMimeType(info: MediaCodecInfo, mimeType: String): Boolean {
        return try {
            info.supportedTypes?.any { it.equals(mimeType, ignoreCase = true) } == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name
            !name.startsWith("OMX.google.", ignoreCase = true) &&
                !name.startsWith("c2.android.", ignoreCase = true)
        }
    }
}
