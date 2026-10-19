package com.felixbrucker.dlnaserver.core.transcoder

import android.os.PowerManager
import com.felixbrucker.dlnaserver.core.database.dao.TranscodeJobDao
import com.felixbrucker.dlnaserver.core.database.entity.TranscodeJobEntity
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.feature.service.TranscodeServiceLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TranscodeQueueManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var powerManagerMock: PowerManager
    private lateinit var wakeLockMock: PowerManager.WakeLock
    private lateinit var appSettingsRepositoryMock: AppSettingsRepository
    private lateinit var transcodeServiceLauncherMock: TranscodeServiceLauncher
    private lateinit var transcodeJobDaoMock: TranscodeJobDao
    private val dbJobsFlow = MutableStateFlow<List<TranscodeJobEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        powerManagerMock = mockk(relaxed = true)
        wakeLockMock = mockk(relaxed = true)
        appSettingsRepositoryMock = mockk(relaxed = true)
        transcodeServiceLauncherMock = mockk(relaxed = true)
        transcodeJobDaoMock = mockk(relaxed = true)

        every { powerManagerMock.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DLNAServer:TranscodeWakeLock") } returns wakeLockMock
        coEvery { appSettingsRepositoryMock.transcodeSettings } returns flowOf(TranscodeSettings())
        coEvery { transcodeJobDaoMock.getAllJobs() } returns dbJobsFlow
        coEvery { transcodeJobDaoMock.getAllJobsSync() } answers { dbJobsFlow.value }
        coEvery { transcodeJobDaoMock.insertJob(any()) } answers {
            val newJob = firstArg<TranscodeJobEntity>()
            val current = dbJobsFlow.value.toMutableList()
            current.removeAll { it.id == newJob.id }
            current.add(newJob)
            dbJobsFlow.value = current
        }
        coEvery { transcodeJobDaoMock.updateJobStatus(any(), any(), any()) } answers {
            val id = firstArg<String>()
            val status = secondArg<TranscodeJobStatus>()
            val err = thirdArg<String?>()
            val current = dbJobsFlow.value.map {
                if (it.id == id) it.copy(status = status, errorMessage = err) else it
            }
            dbJobsFlow.value = current
        }
        coEvery { transcodeJobDaoMock.deleteJobById(any()) } answers {
            val id = firstArg<String>()
            dbJobsFlow.value = dbJobsFlow.value.filter { it.id != id }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWakeLockAcquiredAndReleasedOnJobExecution() = runTest {
        val manager = TranscodeQueueManager(
            powerManagerMock,
            appSettingsRepositoryMock,
            transcodeServiceLauncherMock,
            transcodeJobDaoMock
        )
        val testFile = File("/tmp/test_video.mp4")
        every { wakeLockMock.isHeld } returns true

        manager.enqueueJob(testFile)
        val released = withContext(Dispatchers.Default) {
            var elapsed = 0L
            while (elapsed < 3000L) {
                try {
                    verify(exactly = 1) { wakeLockMock.release() }
                    return@withContext true
                } catch (_: Throwable) {
                    delay(50)
                    elapsed += 50
                }
            }
            false
        }

        assertTrue(released)
        verify { wakeLockMock.acquire() }
        verify { wakeLockMock.release() }
    }

    @Test
    fun testReEnqueueJobsOnInitResetsRunningJobs() = runTest {
        val tempFile = File.createTempFile("transcoded_test", ".mp4")
        val runningEntity = TranscodeJobEntity(
            id = "job-1",
            inputFilePath = "/tmp/input.mp4",
            outputFilePath = tempFile.absolutePath,
            status = TranscodeJobStatus.RUNNING
        )
        dbJobsFlow.value = listOf(runningEntity)

        TranscodeQueueManager(
            powerManagerMock,
            appSettingsRepositoryMock,
            transcodeServiceLauncherMock,
            transcodeJobDaoMock
        )
        val resetVerified = withContext(Dispatchers.Default) {
            var elapsed = 0L
            while (elapsed < 3000L) {
                try {
                    coVerify { transcodeJobDaoMock.updateJobStatus("job-1", TranscodeJobStatus.QUEUED, null) }
                    return@withContext true
                } catch (_: Throwable) {
                    delay(50)
                    elapsed += 50
                }
            }
            false
        }

        assertTrue(resetVerified)
        assertFalse(tempFile.exists())
        verify { transcodeServiceLauncherMock.startService() }
    }

    @Test
    fun testJobsFlowCombinesEntityAndProgress() = runTest {
        val entity = TranscodeJobEntity(
            id = "job-2",
            inputFilePath = "/tmp/input2.mp4",
            outputFilePath = "/tmp/transcoded_input2.mp4",
            status = TranscodeJobStatus.FAILED,
            errorMessage = "Error occurred"
        )
        dbJobsFlow.value = listOf(entity)
        val manager = TranscodeQueueManager(
            powerManagerMock,
            appSettingsRepositoryMock,
            transcodeServiceLauncherMock,
            transcodeJobDaoMock
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val job = manager.jobs.value.first()

        assertEquals("job-2", job.id)
        assertEquals(TranscodeJobStatus.FAILED, job.status)
        assertEquals("Error occurred", job.errorMessage)
        assertEquals(0f, job.progress)
    }
}
