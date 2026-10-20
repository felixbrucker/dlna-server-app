package com.felixbrucker.dlnaserver.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTranscodePresetTest {

    @Test
    fun testFromIdReturnsCorrectPreset() {
        val id = "medium"

        val preset = VideoTranscodePreset.fromId(id)

        assertEquals(VideoTranscodePreset.MEDIUM, preset)
    }

    @Test
    fun testFromIdUnknownReturnsDefault() {
        val id = "invalid"

        val preset = VideoTranscodePreset.fromId(id)

        assertEquals(VideoTranscodePreset.ULTRAFAST, preset)
    }

    @Test
    fun testGetFfmpegPresetForX264() {
        val option = VideoTranscodeOption.X264_SW
        val preset = VideoTranscodePreset.SLOW

        val result = preset.getFfmpegPreset(option)

        assertEquals("slow", result)
    }

    @Test
    fun testGetFfmpegPresetForAv1() {
        val option = VideoTranscodeOption.AV1_SW
        val presetUltrafast = VideoTranscodePreset.ULTRAFAST
        val presetMedium = VideoTranscodePreset.MEDIUM
        val presetSlow = VideoTranscodePreset.SLOW

        val resUltrafast = presetUltrafast.getFfmpegPreset(option)
        val resMedium = presetMedium.getFfmpegPreset(option)
        val resSlow = presetSlow.getFfmpegPreset(option)

        assertEquals("13", resUltrafast)
        assertEquals("6", resMedium)
        assertEquals("3", resSlow)
    }
}
