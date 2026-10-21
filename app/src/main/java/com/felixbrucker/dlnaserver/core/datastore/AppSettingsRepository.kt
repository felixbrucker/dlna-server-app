package com.felixbrucker.dlnaserver.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.felixbrucker.dlnaserver.core.model.AudioTranscodeOption
import com.felixbrucker.dlnaserver.core.model.SubtitleTranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import com.felixbrucker.dlnaserver.core.model.VideoTranscodeOption
import com.felixbrucker.dlnaserver.core.model.VideoTranscodePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TranscodeSettings(
    val videoTranscodeOption: VideoTranscodeOption = VideoTranscodeOption.getDefaultOption(),
    val resolution: TranscodeResolution = TranscodeResolution.KEEP,
    val preset: VideoTranscodePreset = VideoTranscodePreset.getDefaultPreset(),
    val bitrateMb: Int = 3,
    val crf: Int = 20,
    val audioTranscodeOption: AudioTranscodeOption = AudioTranscodeOption.getDefaultOption(),
    val subtitleTranscodeOption: SubtitleTranscodeOption = SubtitleTranscodeOption.getDefaultOption(),
    val maxParallelJobs: Int = 1
)

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val TRANSCODE_OPTION = stringPreferencesKey("transcode_option")
        val TRANSCODE_RESOLUTION = stringPreferencesKey("transcode_resolution")
        val TRANSCODE_PRESET = stringPreferencesKey("transcode_preset")
        val TRANSCODE_BITRATE_MB = intPreferencesKey("transcode_bitrate_mb")
        val TRANSCODE_CRF = intPreferencesKey("transcode_crf")
        val AUDIO_TRANSCODE_OPTION = stringPreferencesKey("audio_transcode_option")
        val SUBTITLE_TRANSCODE_OPTION = stringPreferencesKey("subtitle_transcode_option")
        val MAX_PARALLEL_JOBS = intPreferencesKey("max_parallel_jobs")
    }

    val selectedVideoTranscodeOption: Flow<VideoTranscodeOption> = dataStore.data.map { preferences ->
        val rawId = preferences[PreferenceKeys.TRANSCODE_OPTION]
        if (rawId == null) VideoTranscodeOption.getDefaultOption() else VideoTranscodeOption.fromId(rawId)
    }

    val transcodeSettings: Flow<TranscodeSettings> = dataStore.data.map { preferences ->
        val rawOption = preferences[PreferenceKeys.TRANSCODE_OPTION]
        val option = if (rawOption == null) VideoTranscodeOption.getDefaultOption() else VideoTranscodeOption.fromId(rawOption)
        val resolution = TranscodeResolution.fromId(preferences[PreferenceKeys.TRANSCODE_RESOLUTION] ?: TranscodeResolution.KEEP.id)
        val preset = VideoTranscodePreset.fromId(preferences[PreferenceKeys.TRANSCODE_PRESET] ?: VideoTranscodePreset.getDefaultPreset().id)
        val bitrate = preferences[PreferenceKeys.TRANSCODE_BITRATE_MB] ?: 3
        val crf = preferences[PreferenceKeys.TRANSCODE_CRF] ?: 20
        val audioOption = AudioTranscodeOption.fromId(preferences[PreferenceKeys.AUDIO_TRANSCODE_OPTION] ?: AudioTranscodeOption.getDefaultOption().id)
        val subtitleOption = SubtitleTranscodeOption.fromId(preferences[PreferenceKeys.SUBTITLE_TRANSCODE_OPTION] ?: SubtitleTranscodeOption.getDefaultOption().id)
        val maxJobs = preferences[PreferenceKeys.MAX_PARALLEL_JOBS] ?: 1

        TranscodeSettings(
            videoTranscodeOption = option,
            resolution = resolution,
            preset = preset,
            bitrateMb = bitrate,
            crf = crf,
            audioTranscodeOption = audioOption,
            subtitleTranscodeOption = subtitleOption,
            maxParallelJobs = maxJobs
        )
    }

    suspend fun setVideoTranscodeOption(option: VideoTranscodeOption) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_OPTION] = option.id
        }
    }

    suspend fun setTranscodeResolution(resolution: TranscodeResolution) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_RESOLUTION] = resolution.id
        }
    }

    suspend fun setVideoTranscodePreset(preset: VideoTranscodePreset) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TRANSCODE_PRESET] = preset.id
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

    suspend fun setAudioTranscodeOption(option: AudioTranscodeOption) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.AUDIO_TRANSCODE_OPTION] = option.id
        }
    }

    suspend fun setSubtitleTranscodeOption(option: SubtitleTranscodeOption) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SUBTITLE_TRANSCODE_OPTION] = option.id
        }
    }

    suspend fun setMaxParallelJobs(maxJobs: Int) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.MAX_PARALLEL_JOBS] = maxJobs.coerceAtLeast(1)
        }
    }
}
