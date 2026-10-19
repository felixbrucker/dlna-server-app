package com.felixbrucker.dlnaserver.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.dlnaserver.core.data.repository.SharedDirectoryRepository
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJob
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeQueueManager
import com.felixbrucker.dlnaserver.core.util.MediaMetadataUtils
import com.felixbrucker.dlnaserver.core.util.MimeTypeUtils
import com.felixbrucker.dlnaserver.feature.service.DlnaServiceLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val dlnaServerEngine: DlnaServerEngine,
    private val dlnaServiceLauncher: DlnaServiceLauncher,
    private val sharedDirectoryRepository: SharedDirectoryRepository,
    private val transcodeQueueManager: TranscodeQueueManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val permissionGranted = MutableStateFlow(true)
    private val isPopoverOpen = MutableStateFlow(false)
    private val scannedFiles = MutableStateFlow<List<DashboardMediaFileItem>>(emptyList())
    private val isScanning = MutableStateFlow(false)
    private var scanJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<DashboardUiState> = combine(
        dlnaServerEngine.serverInfo,
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
            mediaFiles = files,
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
        scanSharedDirectories()
    }

    fun toggleServer() {
        if (uiState.value.serverInfo.status == com.felixbrucker.dlnaserver.core.model.ServerStatus.RUNNING) {
            dlnaServiceLauncher.stopService()
        } else {
            dlnaServiceLauncher.startService()
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        permissionGranted.value = granted
        if (granted && searchQuery.value.isNotBlank()) {
            scanSharedDirectories()
        }
    }

    fun togglePopover() {
        isPopoverOpen.value = !isPopoverOpen.value
    }

    fun enqueueTranscode(file: File) {
        transcodeQueueManager.enqueueJob(file)
        scanSharedDirectories()
    }

    fun rescanFiles() {
        scanSharedDirectories()
    }

    private fun scanSharedDirectories() {
        scanJob?.cancel()
        val query = searchQuery.value.trim()
        if (query.isBlank()) {
            scannedFiles.value = emptyList()
            isScanning.value = false
            return
        }

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            isScanning.value = true
            val directories = sharedDirectoryRepository.getEnabledDirectoriesSync()
            val mediaItems = mutableListOf<DashboardMediaFileItem>()

            for (dir in directories) {
                val folder = File(dir.path)
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .filter { file ->
                            file.isFile &&
                                    !file.name.startsWith("transcoded_") &&
                                    file.name.contains(query, ignoreCase = true)
                        }
                        .forEach { file ->
                            val mime = MimeTypeUtils.getMimeType(file)
                            if (mime.startsWith("video/")) {
                                val parent = file.parentFile
                                val transcodedFile = parent?.let { File(it, "transcoded_${file.name}") }
                                val isTranscodeDisabled = transcodedFile?.exists() == true

                                val metadata = MediaMetadataUtils.extractMetadata(file)
                                val durationStr = metadata.durationFormatted ?: "--:--"
                                val resStr = metadata.resolution ?: "Unknown"
                                val sizeStr = formatFileSize(file.length())

                                mediaItems.add(
                                    DashboardMediaFileItem(
                                        file = file,
                                        filename = file.name,
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
