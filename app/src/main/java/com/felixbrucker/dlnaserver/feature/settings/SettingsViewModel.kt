package com.felixbrucker.dlnaserver.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.datastore.TranscodeSettings
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val transcodeSettings: TranscodeSettings = TranscodeSettings(),
    val sharedDirectories: List<SharedDirectory> = emptyList(),
    val supportedOptions: List<TranscodeOption> = emptyList(),
    val tenBitSupportMap: Map<TranscodeOption, Boolean> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val sharedDirectoryRepository: SharedDirectoryRepository
) : ViewModel() {

    private val supportedOptionsList: List<TranscodeOption> by lazy {
        TranscodeOption.entries.filter { it.isHardwareSupported }
    }

    private val tenBitSupportMapLazy: Map<TranscodeOption, Boolean> by lazy {
        TranscodeOption.entries.associateWith { it.is10BitSupported }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        appSettingsRepository.transcodeSettings,
        sharedDirectoryRepository.allDirectories
    ) { settings, directories ->
        SettingsUiState(
            transcodeSettings = settings,
            sharedDirectories = directories,
            supportedOptions = supportedOptionsList,
            tenBitSupportMap = tenBitSupportMapLazy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            supportedOptions = supportedOptionsList,
            tenBitSupportMap = tenBitSupportMapLazy
        )
    )

    fun onTranscodeOptionSelected(option: TranscodeOption) {
        viewModelScope.launch {
            appSettingsRepository.setTranscodeOption(option)
        }
    }

    fun onResolutionSelected(resolution: TranscodeResolution) {
        viewModelScope.launch {
            appSettingsRepository.setTranscodeResolution(resolution)
        }
    }

    fun onBitrateChanged(bitrateMb: Int) {
        viewModelScope.launch {
            appSettingsRepository.setBitrateMb(bitrateMb)
        }
    }

    fun onCrfChanged(crf: Int) {
        viewModelScope.launch {
            appSettingsRepository.setCrf(crf)
        }
    }

    fun onMaxParallelJobsChanged(maxJobs: Int) {
        viewModelScope.launch {
            appSettingsRepository.setMaxParallelJobs(maxJobs)
        }
    }

    fun addSharedDirectory(path: String) {
        viewModelScope.launch {
            sharedDirectoryRepository.addDirectory(path)
        }
    }

    fun toggleSharedDirectory(directory: SharedDirectory) {
        viewModelScope.launch {
            sharedDirectoryRepository.toggleDirectory(directory)
        }
    }

    fun deleteSharedDirectory(id: Long) {
        viewModelScope.launch {
            sharedDirectoryRepository.removeDirectory(id)
        }
    }
}
