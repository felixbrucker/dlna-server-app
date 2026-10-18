package com.felixbrucker.dlnaserver.feature.dashboard

import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption

data class DashboardUiState(
    val serverInfo: DlnaServerInfo = DlnaServerInfo(),
    val sharedDirectories: List<SharedDirectory> = emptyList(),
    val isFolderPickerOpen: Boolean = false,
    val currentPickerPath: String = "/storage/emulated/0",
    val permissionGranted: Boolean = true,
    val selectedTranscodeOption: TranscodeOption = TranscodeOption.NONE,
    val availableTranscodeOptions: List<TranscodeOption> = TranscodeOption.entries
)
