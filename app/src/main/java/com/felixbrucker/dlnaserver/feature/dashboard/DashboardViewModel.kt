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
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.pow

@HiltViewModel
class DashboardViewModel @Inject constructor(
    dlnaServerEngine: DlnaServerEngine,
    private val dlnaServiceLauncher: DlnaServiceLauncher,
    private val sharedDirectoryRepository: SharedDirectoryRepository,
    private val transcodeQueueManager: TranscodeQueueManager
) : ViewModel() {

    private val searchInput = MutableStateFlow("")
    private val submittedSearchQuery = MutableStateFlow("")
    private val isSearchDialogOpen = MutableStateFlow(false)
    private val isBottomSheetOpen = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(true)
    private val scannedFiles = MutableStateFlow<List<DashboardMediaFileItem>>(emptyList())
    private val isScanning = MutableStateFlow(false)
    private var scanJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<DashboardUiState> = combine(
        dlnaServerEngine.serverInfo,
        permissionGranted,
        searchInput,
        submittedSearchQuery,
        isSearchDialogOpen,
        scannedFiles,
        transcodeQueueManager.jobs,
        isBottomSheetOpen,
        isScanning
    ) { args: Array<Any?> ->
        val serverInfo = args[0] as DlnaServerInfo
        val hasPerm = args[1] as Boolean
        val sInput = args[2] as String
        val sSubmitted = args[3] as String
        val searchOpen = args[4] as Boolean
        val files = args[5] as List<DashboardMediaFileItem>
        val jobs = args[6] as List<TranscodeJob>
        val bottomSheetOpen = args[7] as Boolean
        val scanning = args[8] as Boolean

        val visibleJobs = jobs.filter { job ->
            job.status == TranscodeJobStatus.QUEUED ||
                    job.status == TranscodeJobStatus.RUNNING ||
                    job.status == TranscodeJobStatus.FAILED
        }

        val activeJobPaths = jobs.mapTo(HashSet(jobs.size)) { it.inputFile.absolutePath }
        val updatedFiles = files.map { fileItem ->
            val parent = fileItem.file.parentFile
            val transcodedFile = parent?.let { File(it, "transcoded_${fileItem.filename}") }
            val isEnqueuedOrActive = activeJobPaths.contains(fileItem.file.absolutePath)
            val isDisabled = (transcodedFile?.exists() == true) || isEnqueuedOrActive
            fileItem.copy(isTranscodeDisabled = isDisabled)
        }

        DashboardUiState(
            serverInfo = serverInfo,
            permissionGranted = hasPerm,
            searchInput = sInput,
            submittedSearchQuery = sSubmitted,
            isSearchDialogOpen = searchOpen,
            mediaFiles = updatedFiles,
            activeTranscodeJobs = visibleJobs,
            isTranscodeBottomSheetOpen = bottomSheetOpen,
            isScanningFiles = scanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun onSearchInputChanged(newInput: String) {
        searchInput.value = newInput
    }

    fun clearSearchInput() {
        searchInput.value = ""
        submittedSearchQuery.value = ""
        scannedFiles.value = emptyList()
    }

    fun submitSearch() {
        submittedSearchQuery.value = searchInput.value.trim()
        scanSharedDirectories()
    }

    fun setSearchDialogOpen(isOpen: Boolean) {
        isSearchDialogOpen.value = isOpen
    }

    fun setBottomSheetOpen(isOpen: Boolean) {
        isBottomSheetOpen.value = isOpen
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
    }

    fun enqueueTranscode(file: File) {
        transcodeQueueManager.enqueueJob(file)
    }

    fun cancelTranscodeJob(jobId: String) {
        transcodeQueueManager.cancelJob(jobId)
    }

    fun retryJob(jobId: String) {
        transcodeQueueManager.retryJob(jobId)
    }

    private fun scanSharedDirectories() {
        scanJob?.cancel()
        val query = submittedSearchQuery.value.trim()
        if (query.isBlank()) {
            scannedFiles.value = emptyList()
            isScanning.value = false
            return
        }

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            isScanning.value = true
            val directories = sharedDirectoryRepository.getEnabledDirectoriesSync()
            val mediaItems = mutableListOf<DashboardMediaFileItem>()
            val activeJobs = transcodeQueueManager.jobs.value
            val activeJobPaths = activeJobs.mapTo(HashSet(activeJobs.size)) { it.inputFile.absolutePath }

            for (dir in directories) {
                val folder = File(dir.path)
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .filter { file ->
                            file.isFile &&
                                    !file.name.startsWith("transcoded_") &&
                                    file.absolutePath.contains(query, ignoreCase = true)
                        }
                        .forEach { file ->
                            val mime = MimeTypeUtils.getMimeType(file)
                            if (mime.startsWith("video/")) {
                                val parent = file.parentFile
                                val transcodedFile = parent?.let { File(it, "transcoded_${file.name}") }
                                val isEnqueuedOrActive = activeJobPaths.contains(file.absolutePath)
                                val isTranscodeDisabled = (transcodedFile?.exists() == true) || isEnqueuedOrActive

                                val metadata = MediaMetadataUtils.extractGenericMetadata(file)
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

            scannedFiles.value = mediaItems.sortedBy { it.filename.lowercase() }
            isScanning.value = false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
    }
}
