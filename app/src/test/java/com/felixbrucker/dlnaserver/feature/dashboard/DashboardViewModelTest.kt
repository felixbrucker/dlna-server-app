package com.felixbrucker.dlnaserver.feature.dashboard

import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeQueueManager
import com.felixbrucker.dlnaserver.feature.service.DlnaServiceLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dlnaServerEngineMock: DlnaServerEngine
    private lateinit var dlnaServiceLauncherMock: DlnaServiceLauncher
    private lateinit var sharedDirectoryRepositoryMock: SharedDirectoryRepository
    private lateinit var transcodeQueueManagerMock: TranscodeQueueManager
    private val serverInfoFlow = MutableStateFlow(DlnaServerInfo(status = ServerStatus.STOPPED))
    private val selectedInterfaceFlow = MutableStateFlow("auto")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dlnaServerEngineMock = mockk(relaxed = true)
        dlnaServiceLauncherMock = mockk(relaxed = true)
        sharedDirectoryRepositoryMock = mockk(relaxed = true)
        transcodeQueueManagerMock = mockk(relaxed = true)

        coEvery { dlnaServerEngineMock.serverInfo } returns serverInfoFlow
        coEvery { dlnaServerEngineMock.selectedInterface } returns selectedInterfaceFlow
        coEvery { sharedDirectoryRepositoryMock.allDirectories } returns flowOf(emptyList())
        coEvery { sharedDirectoryRepositoryMock.getEnabledDirectoriesSync() } returns emptyList()
        coEvery { transcodeQueueManagerMock.jobs } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        val viewModel = DashboardViewModel(
            dlnaServerEngineMock,
            dlnaServiceLauncherMock,
            sharedDirectoryRepositoryMock,
            transcodeQueueManagerMock
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(ServerStatus.STOPPED, state.serverInfo.status)
        assertEquals("auto", state.selectedNetworkInterface)
        assertTrue(state.mediaFiles.isEmpty())
    }

    @Test
    fun testEnqueueTranscodeDelegatesToQueueManager() = runTest {
        val viewModel = DashboardViewModel(
            dlnaServerEngineMock,
            dlnaServiceLauncherMock,
            sharedDirectoryRepositoryMock,
            transcodeQueueManagerMock
        )
        val dummyFile = File("/tmp/sample.mkv")

        viewModel.enqueueTranscode(dummyFile)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { transcodeQueueManagerMock.enqueueJob(dummyFile) }
    }

    @Test
    fun testOnNetworkInterfaceSelectedDelegatesToEngine() = runTest {
        val viewModel = DashboardViewModel(
            dlnaServerEngineMock,
            dlnaServiceLauncherMock,
            sharedDirectoryRepositoryMock,
            transcodeQueueManagerMock
        )

        viewModel.onNetworkInterfaceSelected("wlan0")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { dlnaServerEngineMock.setSelectedInterface("wlan0") }
    }
}
