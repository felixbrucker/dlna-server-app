package com.felixbrucker.dlnaserver.feature.dashboard

import com.felixbrucker.dlnaserver.core.data.repository.DlnaRepository
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dlnaRepositoryMock: DlnaRepository
    private lateinit var sharedDirectoryRepositoryMock: SharedDirectoryRepository
    private lateinit var appSettingsRepositoryMock: AppSettingsRepository
    private val serverInfoFlow = MutableStateFlow(DlnaServerInfo(status = ServerStatus.STOPPED))
    private val selectedTranscodeOptionFlow = MutableStateFlow(TranscodeOption.NONE)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dlnaRepositoryMock = mockk(relaxed = true)
        sharedDirectoryRepositoryMock = mockk(relaxed = true)
        appSettingsRepositoryMock = mockk(relaxed = true)

        coEvery { dlnaRepositoryMock.serverInfo } returns serverInfoFlow
        coEvery { sharedDirectoryRepositoryMock.allDirectories } returns flowOf(emptyList())
        coEvery { appSettingsRepositoryMock.selectedTranscodeOption } returns selectedTranscodeOptionFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock, appSettingsRepositoryMock)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ServerStatus.STOPPED, state.serverInfo.status)
        assertTrue(state.sharedDirectories.isEmpty())
        assertEquals(TranscodeOption.NONE, state.selectedTranscodeOption)
    }

    @Test
    fun testAddDirectoryDelegatesToRepository() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock, appSettingsRepositoryMock)

        viewModel.addDirectory("/tmp", "Temp")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sharedDirectoryRepositoryMock.addDirectory("/tmp", "Temp") }
    }

    @Test
    fun testDeleteDirectoryDelegatesToRepository() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock, appSettingsRepositoryMock)

        viewModel.deleteDirectory(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sharedDirectoryRepositoryMock.deleteDirectory(1L) }
    }

    @Test
    fun testSelectTranscodeOptionDelegatesToRepository() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock, appSettingsRepositoryMock)

        viewModel.selectTranscodeOption(TranscodeOption.X264)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepositoryMock.setTranscodeOption(TranscodeOption.X264) }
    }
}
