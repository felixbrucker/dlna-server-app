package com.felixbrucker.dlnaserver.core.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareCodecUtils @Inject constructor() {

    fun isEncoderSupported(mimeType: String?): Boolean {
        return hasHardwareEncoderSupport(mimeType)
    }

    companion object {
        fun hasHardwareEncoderSupport(mimeType: String?): Boolean {
            if (mimeType.isNullOrBlank()) return false
            return try {
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
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    val name = info.name
                    !name.startsWith("OMX.google.", ignoreCase = true) &&
                            !name.startsWith("c2.android.", ignoreCase = true)
                }
            } catch (_: Throwable) {
                false
            }
        }
    }
}
