package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTranscodeOptionTest {

    @Test
    fun testFromIdReturnsCorrectOption() {
        val id = "AAC_SW"

        val option = AudioTranscodeOption.fromId(id)

        assertEquals(AudioTranscodeOption.AAC_SW, option)
    }

    @Test
    fun testFromIdUnknownReturnsDefault() {
        val id = "invalid"

        val option = AudioTranscodeOption.fromId(id)

        assertEquals(AudioTranscodeOption.KEEP, option)
    }

    @Test
    fun testIsHardwareProperty() {
        val hwOption = AudioTranscodeOption.AAC_HW
        val swOption = AudioTranscodeOption.AAC_SW

        val isHw = hwOption.isHardware
        val isSw = swOption.isHardware

        assertTrue(isHw)
        assertFalse(isSw)
    }
}
