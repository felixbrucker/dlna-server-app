package com.felixbrucker.dlnaserver.core.transcoder

import android.content.Context
import android.os.PowerManager
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.feature.service.TranscodeServiceLauncher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TranscodeQueueManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var contextMock: Context
    private lateinit var powerManagerMock: PowerManager
    private lateinit var wakeLockMock: PowerManager.WakeLock
    private lateinit var appSettingsRepositoryMock: AppSettingsRepository
    private lateinit var transcodeServiceLauncherMock: TranscodeServiceLauncher

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        contextMock = mockk(relaxed = true)
        powerManagerMock = mockk(relaxed = true)
        wakeLockMock = mockk(relaxed = true)
        appSettingsRepositoryMock = mockk(relaxed = true)
        transcodeServiceLauncherMock = mockk(relaxed = true)

        every { contextMock.getSystemService(Context.POWER_SERVICE) } returns powerManagerMock
        every { powerManagerMock.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DLNAServer:TranscodeWakeLock") } returns wakeLockMock
        coEvery { appSettingsRepositoryMock.transcodeSettings } returns flowOf(TranscodeSettings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWakeLockAcquiredAndReleasedOnJobExecution() = runTest {
        val manager = TranscodeQueueManager(
            contextMock,
            appSettingsRepositoryMock,
            transcodeServiceLauncherMock
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
}
