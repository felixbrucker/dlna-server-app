package com.felixbrucker.dlnaserver.feature.dashboard

import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJob
import java.io.File

data class DashboardMediaFileItem(
    val file: File,
    val filename: String,
    val sizeFormatted: String,
    val resolution: String,
    val duration: String,
    val isTranscodeDisabled: Boolean
)

data class DashboardUiState(
    val serverInfo: DlnaServerInfo = DlnaServerInfo(),
    val permissionGranted: Boolean = true,
    val searchQuery: String = "",
    val mediaFiles: List<DashboardMediaFileItem> = emptyList(),
    val activeTranscodeJobs: List<TranscodeJob> = emptyList(),
    val isTranscodePopoverOpen: Boolean = false,
    val isScanningFiles: Boolean = false
)
