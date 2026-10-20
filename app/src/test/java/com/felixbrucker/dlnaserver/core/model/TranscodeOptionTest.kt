package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscodeOptionTest {

    @Test
    fun testFromIdReturnsCorrectOption() {
        val id = "X264_HW"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.X264_HW, option)
    }

    @Test
    fun testFromIdUnknownReturnsDefaultOption() {
        val id = "UNKNOWN_ID"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.getDefaultOption(), option)
    }

    @Test
    fun testGetDefaultOptionReturnsOption() {
        val defaultOption = TranscodeOption.getDefaultOption()

        assertTrue(defaultOption in TranscodeOption.entries)
    }

    @Test
    fun testIsHardwareProperty() {
        val hwOption = TranscodeOption.X265_HW
        val swOption = TranscodeOption.X265_SW

        val isHw = hwOption.isHardware
        val isSwHw = swOption.isHardware

        assertTrue(isHw)
        assertFalse(isSwHw)
    }

    @Test
    fun testSoftwareOptionsAlwaysSupported() {
        val swOption = TranscodeOption.X265_SW

        val hwSupported = swOption.isHardwareSupported
        val tenBitSupported = swOption.is10BitSupported

        assertTrue(hwSupported)
        assertTrue(tenBitSupported)
    }
}
