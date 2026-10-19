package com.felixbrucker.dlnaserver.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.dlnaserver.core.data.repository.DlnaRepository
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJob
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeQueueManager
import com.felixbrucker.dlnaserver.core.util.MediaMetadataUtils
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dlnaRepository: DlnaRepository,
    private val sharedDirectoryRepository: SharedDirectoryRepository,
    private val transcodeQueueManager: TranscodeQueueManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val permissionGranted = MutableStateFlow(true)
    private val isPopoverOpen = MutableStateFlow(false)
    private val scannedFiles = MutableStateFlow<List<DashboardMediaFileItem>>(emptyList())
    private val isScanning = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            sharedDirectoryRepository.allDirectories.collect {
                scanSharedDirectories()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<DashboardUiState> = combine(
        dlnaRepository.serverInfo,
        permissionGranted,
        searchQuery,
        scannedFiles,
        transcodeQueueManager.jobs,
        isPopoverOpen,
        isScanning
    ) { args: Array<Any?> ->
        val serverInfo = args[0] as DlnaServerInfo
        val hasPerm = args[1] as Boolean
        val query = args[2] as String
        val files = args[3] as List<DashboardMediaFileItem>
        val jobs = args[4] as List<TranscodeJob>
        val popoverOpen = args[5] as Boolean
        val scanning = args[6] as Boolean

        val filteredFiles = if (query.isBlank()) {
            files
        } else {
            files.filter { it.filename.contains(query, ignoreCase = true) }
        }

        val visibleJobs = jobs.filter { job ->
            job.status == TranscodeJobStatus.PENDING ||
                    job.status == TranscodeJobStatus.QUEUED ||
                    job.status == TranscodeJobStatus.RUNNING ||
                    job.status == TranscodeJobStatus.FAILED
        }

        DashboardUiState(
            serverInfo = serverInfo,
            permissionGranted = hasPerm,
            searchQuery = query,
            mediaFiles = filteredFiles,
            activeTranscodeJobs = visibleJobs,
            isTranscodePopoverOpen = popoverOpen,
            isScanningFiles = scanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun onSearchQueryChanged(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun toggleServer(context: Context) {
        if (uiState.value.serverInfo.status == ServerStatus.RUNNING) {
            dlnaRepository.stopServerService(context)
        } else {
            dlnaRepository.startServerService(context)
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        permissionGranted.value = granted
        if (granted) {
            scanSharedDirectories()
        }
    }

    fun togglePopover() {
        isPopoverOpen.value = !isPopoverOpen.value
    }

    fun enqueueTranscode(file: File) {
        transcodeQueueManager.enqueueJob(file)
        viewModelScope.launch {
            scanSharedDirectories()
        }
    }

    fun rescanFiles() {
        scanSharedDirectories()
    }

    private fun scanSharedDirectories() {
        viewModelScope.launch(Dispatchers.IO) {
            isScanning.value = true
            val directories = sharedDirectoryRepository.getEnabledDirectoriesSync()
            val mediaItems = mutableListOf<DashboardMediaFileItem>()

            for (dir in directories) {
                val folder = File(dir.path)
                if (folder.exists() && folder.isDirectory) {
                    val files = folder.listFiles() ?: continue
                    for (file in files) {
                        if (file.isFile) {
                            val name = file.name
                            if (name.startsWith("transcoded_")) continue

                            val mime = MimeTypeUtils.getMimeType(file)
                            if (mime.startsWith("video/")) {
                                val transcodedFile = File(folder, "transcoded_$name")
                                val isTranscodeDisabled = transcodedFile.exists()

                                val metadata = MediaMetadataUtils.extractMetadata(file)
                                val durationStr = metadata.durationFormatted ?: "--:--"
                                val resStr = metadata.resolution ?: "Unknown"
                                val sizeStr = formatFileSize(file.length())

                                mediaItems.add(
                                    DashboardMediaFileItem(
                                        file = file,
                                        filename = name,
                                        sizeFormatted = sizeStr,
                                        resolution = resStr,
                                        duration = durationStr,
                                        isTranscodeDisabled = isTranscodeDisabled
                                    )
                                )
                            }
                        }
                    }
                }
            }

            scannedFiles.value = mediaItems
            isScanning.value = false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
