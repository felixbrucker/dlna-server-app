package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTranscodeOptionTest {

    @Test
    fun testFromIdReturnsCorrectOption() {
        val id = "ASS"

        val option = SubtitleTranscodeOption.fromId(id)

        assertEquals(SubtitleTranscodeOption.ASS, option)
    }

    @Test
    fun testFromIdUnknownReturnsDefault() {
        val id = "invalid"

        val option = SubtitleTranscodeOption.fromId(id)

        assertEquals(SubtitleTranscodeOption.KEEP, option)
    }
}
