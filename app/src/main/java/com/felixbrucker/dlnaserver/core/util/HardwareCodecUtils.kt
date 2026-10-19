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
