package com.felixbrucker.dlnaserver.core.util

import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import org.junit.Assert.assertFalse
import org.junit.Test

class HardwareCodecUtilsTest {

    @Test
    fun testHasHardwareEncoderSupportWithNullMimeTypeReturnsFalse() {
        val mimeType: String? = null

        val result = HardwareCodecUtils.hasHardwareEncoderSupport(mimeType)

        assertFalse(result)
    }

    @Test
    fun testHasHardwareEncoderSupportWithEmptyMimeTypeReturnsFalse() {
        val mimeType = ""

        val result = HardwareCodecUtils.hasHardwareEncoderSupport(mimeType)

        assertFalse(result)
    }

    @Test
    fun testHasHardwareEncoderSupportForOptionReturnsBoolean() {
        val option = TranscodeOption.X265_HW

        val result = HardwareCodecUtils.hasHardwareEncoderSupport(option)

        assertFalse(result)
    }

    @Test
    fun testHas10BitEncoderSupportWithNullMimeTypeReturnsFalse() {
        val mimeType: String? = null

        val result = HardwareCodecUtils.has10BitEncoderSupport(mimeType)

        assertFalse(result)
    }

    @Test
    fun testHas10BitEncoderSupportWithEmptyMimeTypeReturnsFalse() {
        val mimeType = ""

        val result = HardwareCodecUtils.has10BitEncoderSupport(mimeType)

        assertFalse(result)
    }

    @Test
    fun testHas10BitEncoderSupportForOptionReturnsBoolean() {
        val option = TranscodeOption.X265_HW

        val result = HardwareCodecUtils.has10BitEncoderSupport(option)

        assertFalse(result)
    }
}
