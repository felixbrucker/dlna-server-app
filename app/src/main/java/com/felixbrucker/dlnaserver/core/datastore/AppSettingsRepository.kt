package com.felixbrucker.dlnaserver.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val TRANSCODE_OPTION = stringPreferencesKey("transcode_option")
    }

    val selectedTranscodeOption: Flow<TranscodeOption> = dataStore.data.map { preferences ->
        val rawId = preferences[PreferenceKeys.TRANSCODE_OPTION] ?: TranscodeOption.NONE.id
        TranscodeOption.fromId(rawId)
    }

    suspend fun setTranscodeOption(option: TranscodeOption) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_OPTION] = option.id
        }
    }
}
