package com.felixbrucker.dlnaserver.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
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
    fun testDefaultSelectedTranscodeOptionIsX265Sw() = runTest {
        val flow = repository.selectedTranscodeOption

        val result = flow.first()

        assertEquals(TranscodeOption.X265_SW, result)
    }

    @Test
    fun testSetTranscodeOptionUpdatesPreference() = runTest {
        val option = TranscodeOption.X264_HW

        repository.setTranscodeOption(option)

        val result = repository.selectedTranscodeOption.first()
        assertEquals(TranscodeOption.X264_HW, result)
    }
}
