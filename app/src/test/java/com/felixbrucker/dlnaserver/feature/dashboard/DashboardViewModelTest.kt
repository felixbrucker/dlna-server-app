package com.felixbrucker.dlnaserver.feature.dashboard

import com.felixbrucker.dlnaserver.core.data.repository.DlnaRepository
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
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
    private val serverInfoFlow = MutableStateFlow(DlnaServerInfo(status = ServerStatus.STOPPED))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dlnaRepositoryMock = mockk(relaxed = true)
        sharedDirectoryRepositoryMock = mockk(relaxed = true)

        coEvery { dlnaRepositoryMock.serverInfo } returns serverInfoFlow
        coEvery { sharedDirectoryRepositoryMock.allDirectories } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ServerStatus.STOPPED, state.serverInfo.status)
        assertTrue(state.sharedDirectories.isEmpty())
    }

    @Test
    fun testAddDirectoryDelegatesToRepository() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock)

        viewModel.addDirectory("/tmp", "Temp")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sharedDirectoryRepositoryMock.addDirectory("/tmp", "Temp") }
    }

    @Test
    fun testDeleteDirectoryDelegatesToRepository() = runTest {
        val viewModel = DashboardViewModel(dlnaRepositoryMock, sharedDirectoryRepositoryMock)

        viewModel.deleteDirectory(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { sharedDirectoryRepositoryMock.deleteDirectory(1L) }
    }
}
