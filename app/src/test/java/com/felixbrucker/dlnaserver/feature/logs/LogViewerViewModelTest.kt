package com.felixbrucker.dlnaserver.feature.logs

import android.util.Log
import com.felixbrucker.dlnaserver.core.logging.LogEntry
import com.felixbrucker.dlnaserver.core.logging.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LogViewerViewModel.defaultDispatcher = testDispatcher
        LogRepository.ioDispatcher = testDispatcher
        LogRepository.clearLogs()
    }

    @After
    fun tearDown() {
        LogRepository.clearLogs()
        Dispatchers.resetMain()
    }

    @Test
    fun testLogFilterByPriorityAndQuery() = runTest {
        val entry1 = LogEntry(id = "1", priority = Log.INFO, tag = "DlnaServer", message = "Server starting")
        val entry2 = LogEntry(id = "2", priority = Log.ERROR, tag = "HttpServer", message = "Address in use")
        LogRepository.addLog(entry1)
        LogRepository.addLog(entry2)
        testDispatcher.scheduler.advanceUntilIdle()

        val viewModel = LogViewerViewModel()
        viewModel.setSearchQuery("Address")
        viewModel.setSelectedPriority(Log.ERROR)
        testDispatcher.scheduler.advanceUntilIdle()

        val filtered = viewModel.filteredLogs.value
        assertEquals(1, filtered.size)
        assertEquals("2", filtered.first().id)
    }

    @Test
    fun testClearLogsEmptiesList() = runTest {
        val entry = LogEntry(id = "1", priority = Log.INFO, tag = "DlnaServer", message = "Hello")
        LogRepository.addLog(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        val viewModel = LogViewerViewModel()
        viewModel.clearLogs()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.allLogs.value.size)
    }
}
