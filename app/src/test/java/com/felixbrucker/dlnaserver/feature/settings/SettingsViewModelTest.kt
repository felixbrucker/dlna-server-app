package com.felixbrucker.dlnaserver.feature.settings

import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.core.model.AudioTranscodeOption
import com.felixbrucker.dlnaserver.core.model.SubtitleTranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import com.felixbrucker.dlnaserver.core.model.VideoTranscodeOption
import com.felixbrucker.dlnaserver.core.model.VideoTranscodePreset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appSettingsRepositoryMock: AppSettingsRepository
    private lateinit var sharedDirectoryRepositoryMock: SharedDirectoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appSettingsRepositoryMock = mockk(relaxed = true)
        sharedDirectoryRepositoryMock = mockk(relaxed = true)

        coEvery { appSettingsRepositoryMock.transcodeSettings } returns flowOf(TranscodeSettings())
        coEvery { sharedDirectoryRepositoryMock.allDirectories } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUiStateProvidesTranscodeSettings() = runTest {
        val viewModel = SettingsViewModel(appSettingsRepositoryMock, sharedDirectoryRepositoryMock)

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.first()

        assertEquals(TranscodeSettings(), state.transcodeSettings)
    }

    @Test
    fun testOnVideoTranscodeOptionSelectedDelegatesToRepository() = runTest {
        val viewModel = SettingsViewModel(appSettingsRepositoryMock, sharedDirectoryRepositoryMock)
        val option = VideoTranscodeOption.KEEP

        viewModel.onVideoTranscodeOptionSelected(option)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepositoryMock.setVideoTranscodeOption(option) }
    }

    @Test
    fun testOnVideoTranscodePresetSelectedDelegatesToRepository() = runTest {
        val viewModel = SettingsViewModel(appSettingsRepositoryMock, sharedDirectoryRepositoryMock)
        val preset = VideoTranscodePreset.SLOW

        viewModel.onVideoTranscodePresetSelected(preset)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepositoryMock.setVideoTranscodePreset(preset) }
    }

    @Test
    fun testOnAudioTranscodeOptionSelectedDelegatesToRepository() = runTest {
        val viewModel = SettingsViewModel(appSettingsRepositoryMock, sharedDirectoryRepositoryMock)
        val option = AudioTranscodeOption.AAC_SW

        viewModel.onAudioTranscodeOptionSelected(option)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepositoryMock.setAudioTranscodeOption(option) }
    }

    @Test
    fun testOnSubtitleTranscodeOptionSelectedDelegatesToRepository() = runTest {
        val viewModel = SettingsViewModel(appSettingsRepositoryMock, sharedDirectoryRepositoryMock)
        val option = SubtitleTranscodeOption.SRT

        viewModel.onSubtitleTranscodeOptionSelected(option)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepositoryMock.setSubtitleTranscodeOption(option) }
    }
}
