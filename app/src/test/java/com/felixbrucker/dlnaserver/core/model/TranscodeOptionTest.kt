package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscodeOptionTest {

    @Test
    fun testFromIdReturnsCorrectOption() {
        val id = "X264"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.X264, option)
    }

    @Test
    fun testFromIdUnknownReturnsX265Default() {
        val id = "UNKNOWN_ID"

        val option = TranscodeOption.fromId(id)

        assertEquals(TranscodeOption.X265, option)
    }

    @Test
    fun testDisplayLabelWithHwSupportReturnsHwLabel() {
        val option = TranscodeOption.X264

        val label = option.getDisplayLabel(hasHwSupport = true)

        assertEquals("x264 (HW)", label)
    }

    @Test
    fun testDisplayLabelWithoutHwSupportReturnsSwLabel() {
        val option = TranscodeOption.X265

        val label = option.getDisplayLabel(hasHwSupport = false)

        assertEquals("x265 (SW)", label)
    }
}
