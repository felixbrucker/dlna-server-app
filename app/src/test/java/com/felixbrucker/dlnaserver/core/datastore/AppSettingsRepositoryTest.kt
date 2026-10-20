package com.felixbrucker.dlnaserver.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.felixbrucker.dlnaserver.core.model.AudioTranscodeOption
import com.felixbrucker.dlnaserver.core.model.SubtitleTranscodeOption
import com.felixbrucker.dlnaserver.core.model.VideoTranscodeOption
import com.felixbrucker.dlnaserver.core.model.VideoTranscodePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AppSettingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        tempFile = File.createTempFile("app_settings_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )
        repository = AppSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testDefaultSelectedVideoTranscodeOptionIsX265Sw() = runTest {
        val flow = repository.selectedVideoTranscodeOption

        val result = flow.first()

        assertEquals(VideoTranscodeOption.X265_SW, result)
    }

    @Test
    fun testSetVideoTranscodeOptionUpdatesPreference() = runTest {
        val option = VideoTranscodeOption.X264_HW

        repository.setVideoTranscodeOption(option)

        val result = repository.selectedVideoTranscodeOption.first()
        assertEquals(VideoTranscodeOption.X264_HW, result)
    }

    @Test
    fun testSetVideoTranscodePresetUpdatesPreference() = runTest {
        val preset = VideoTranscodePreset.SLOW

        repository.setVideoTranscodePreset(preset)

        val result = repository.transcodeSettings.first()
        assertEquals(VideoTranscodePreset.SLOW, result.preset)
    }

    @Test
    fun testSetAudioTranscodeOptionUpdatesPreference() = runTest {
        val option = AudioTranscodeOption.AC3_SW

        repository.setAudioTranscodeOption(option)

        val result = repository.transcodeSettings.first()
        assertEquals(AudioTranscodeOption.AC3_SW, result.audioOption)
    }

    @Test
    fun testSetSubtitleTranscodeOptionUpdatesPreference() = runTest {
        val option = SubtitleTranscodeOption.SRT

        repository.setSubtitleTranscodeOption(option)

        val result = repository.transcodeSettings.first()
        assertEquals(SubtitleTranscodeOption.SRT, result.subtitleOption)
    }
}
