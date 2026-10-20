package com.felixbrucker.dlnaserver.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TranscodeSettings(
    val option: TranscodeOption = TranscodeOption.getDefaultOption(),
    val resolution: TranscodeResolution = TranscodeResolution.KEEP,
    val bitrateMb: Int = 3,
    val crf: Int = 20,
    val maxParallelJobs: Int = 1
)

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val TRANSCODE_OPTION = stringPreferencesKey("transcode_option")
        val TRANSCODE_RESOLUTION = stringPreferencesKey("transcode_resolution")
        val TRANSCODE_BITRATE_MB = intPreferencesKey("transcode_bitrate_mb")
        val TRANSCODE_CRF = intPreferencesKey("transcode_crf")
        val MAX_PARALLEL_JOBS = intPreferencesKey("max_parallel_jobs")
    }

    val selectedTranscodeOption: Flow<TranscodeOption> = dataStore.data.map { preferences ->
        val rawId = preferences[PreferenceKeys.TRANSCODE_OPTION]
        if (rawId == null) TranscodeOption.getDefaultOption() else TranscodeOption.fromId(rawId)
    }

    val transcodeSettings: Flow<TranscodeSettings> = dataStore.data.map { preferences ->
        val rawOption = preferences[PreferenceKeys.TRANSCODE_OPTION]
        val option = if (rawOption == null) TranscodeOption.getDefaultOption() else TranscodeOption.fromId(rawOption)
        val resolution = TranscodeResolution.fromId(preferences[PreferenceKeys.TRANSCODE_RESOLUTION] ?: TranscodeResolution.KEEP.id)
        val bitrate = preferences[PreferenceKeys.TRANSCODE_BITRATE_MB] ?: 3
        val crf = preferences[PreferenceKeys.TRANSCODE_CRF] ?: 20
        val maxJobs = preferences[PreferenceKeys.MAX_PARALLEL_JOBS] ?: 1

        TranscodeSettings(
            option = option,
            resolution = resolution,
            bitrateMb = bitrate,
            crf = crf,
            maxParallelJobs = maxJobs
        )
    }

    suspend fun setTranscodeOption(option: TranscodeOption) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_OPTION] = option.id
        }
    }

    suspend fun setTranscodeResolution(resolution: TranscodeResolution) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_RESOLUTION] = resolution.id
        }
    }

    suspend fun setBitrateMb(bitrateMb: Int) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_BITRATE_MB] = bitrateMb.coerceIn(1, 12)
        }
    }

    suspend fun setCrf(crf: Int) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_CRF] = crf.coerceIn(0, 63)
        }
    }

    suspend fun setMaxParallelJobs(maxJobs: Int) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.MAX_PARALLEL_JOBS] = maxJobs.coerceAtLeast(1)
        }
    }
}
