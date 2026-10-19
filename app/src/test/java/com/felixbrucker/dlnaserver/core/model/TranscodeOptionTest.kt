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
    fun testFromIdLegacyIdReturnsSwOption() {
        val id = "X264"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.X264_SW, option)
    }

    @Test
    fun testFromIdUnknownReturnsX265SwDefault() {
        val id = "UNKNOWN_ID"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.X265_SW, option)
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
}
