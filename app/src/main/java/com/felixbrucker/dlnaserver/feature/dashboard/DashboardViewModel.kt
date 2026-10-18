package com.felixbrucker.dlnaserver.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.dlnaserver.core.data.repository.DlnaRepository
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dlnaRepository: DlnaRepository,
    private val sharedDirectoryRepository: SharedDirectoryRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _isFolderPickerOpen = MutableStateFlow(false)
    private val _permissionGranted = MutableStateFlow(true)

    val uiState: StateFlow<DashboardUiState> = combine(
        dlnaRepository.serverInfo,
        sharedDirectoryRepository.allDirectories,
        appSettingsRepository.selectedTranscodeOption,
        _isFolderPickerOpen,
        _permissionGranted
    ) { serverInfo, directories, transcodeOption, isPickerOpen, hasPermission ->
        DashboardUiState(
            serverInfo = serverInfo,
            sharedDirectories = directories,
            isFolderPickerOpen = isPickerOpen,
            permissionGranted = hasPermission,
            selectedTranscodeOption = transcodeOption
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun toggleServer(context: Context) {
        if (uiState.value.serverInfo.status == ServerStatus.RUNNING) {
            dlnaRepository.stopServerService(context)
        } else {
            dlnaRepository.startServerService(context)
        }
    }

    fun addDirectory(path: String, name: String? = null) {
        viewModelScope.launch {
            sharedDirectoryRepository.addDirectory(path, name)
        }
    }

    fun toggleDirectory(directory: SharedDirectory) {
        viewModelScope.launch {
            sharedDirectoryRepository.toggleDirectory(directory)
        }
    }

    fun deleteDirectory(id: Long) {
        viewModelScope.launch {
            sharedDirectoryRepository.deleteDirectory(id)
        }
    }

    fun selectTranscodeOption(option: TranscodeOption) {
        viewModelScope.launch {
            appSettingsRepository.setTranscodeOption(option)
        }
    }

    fun setFolderPickerOpen(isOpen: Boolean) {
        _isFolderPickerOpen.value = isOpen
    }

    fun setPermissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }
}
