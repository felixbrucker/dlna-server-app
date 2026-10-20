package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTranscodeOptionTest {

    @Test
    fun testFromIdReturnsCorrectOption() {
        val id = "X264_HW"

        val option = VideoTranscodeOption.fromId(id)

        assertEquals(VideoTranscodeOption.X264_HW, option)
    }

    @Test
    fun testFromIdKeepReturnsKeepOption() {
        val id = "KEEP"

        val option = VideoTranscodeOption.fromId(id)

        assertEquals(VideoTranscodeOption.KEEP, option)
    }

    @Test
    fun testFromIdUnknownReturnsDefaultOption() {
        val id = "UNKNOWN_ID"

        val option = VideoTranscodeOption.fromId(id)

        assertEquals(VideoTranscodeOption.getDefaultOption(), option)
    }

    @Test
    fun testGetDefaultOptionReturnsOption() {
        val defaultOption = VideoTranscodeOption.getDefaultOption()

        assertTrue(defaultOption in VideoTranscodeOption.entries)
    }

    @Test
    fun testIsHardwareProperty() {
        val hwOption = VideoTranscodeOption.X265_HW
        val swOption = VideoTranscodeOption.X265_SW

        val isHw = hwOption.isHardware
        val isSwHw = swOption.isHardware

        assertTrue(isHw)
        assertFalse(isSwHw)
    }

    @Test
    fun testSoftwareOptionsAlwaysSupported() {
        val swOption = VideoTranscodeOption.X265_SW

        val hwSupported = swOption.isHardwareSupported
        val tenBitSupported = swOption.is10BitSupported

        assertTrue(hwSupported)
        assertTrue(tenBitSupported)
    }

    @Test
    fun testSupportsPresets() {
        val x264Sw = VideoTranscodeOption.X264_SW
        val keepOption = VideoTranscodeOption.KEEP

        val x264Supports = x264Sw.supportsPresets
        val keepSupports = keepOption.supportsPresets

        assertTrue(x264Supports)
        assertFalse(keepSupports)
    }
}
