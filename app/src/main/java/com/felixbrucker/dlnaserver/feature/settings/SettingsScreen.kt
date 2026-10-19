package com.felixbrucker.dlnaserver.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.model.TranscodeResolution
import com.felixbrucker.dlnaserver.feature.dashboard.components.DirectoryItemCard
import com.felixbrucker.dlnaserver.feature.dashboard.components.EmptyDirectoriesCard
import com.felixbrucker.dlnaserver.feature.dashboard.components.FolderPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val uiState by viewModel.uiState.collectAsState()
    var showFolderPicker by remember { mutableStateOf(false) }

    if (showFolderPicker) {
        FolderPickerDialog(
            onDismissRequest = { showFolderPicker = false },
            onFolderSelected = { path, _ ->
                viewModel.addSharedDirectory(path)
                showFolderPicker = false
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shared Directories Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Directories",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = { showFolderPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Directory")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.sharedDirectories.isEmpty()) {
                        EmptyDirectoriesCard()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.sharedDirectories.forEach { directory ->
                                DirectoryItemCard(
                                    directory = directory,
                                    onToggleDirectory = { viewModel.toggleSharedDirectory(it) },
                                    onDeleteDirectory = { viewModel.deleteSharedDirectory(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Transcoding Configuration Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Transcoding Options",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Codec selection
                    var codecExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = codecExpanded,
                        onExpandedChange = { codecExpanded = !codecExpanded }
                    ) {
                        val currentOption = uiState.transcodeSettings.option
                        val hasHw = uiState.hardwareCodecSupportMap[currentOption] ?: false
                        OutlinedTextField(
                            value = currentOption.getDisplayLabel(hasHw),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Codec") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codecExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = codecExpanded,
                            onDismissRequest = { codecExpanded = false }
                        ) {
                            TranscodeOption.entries.forEach { option ->
                                val hasHwSupport = uiState.hardwareCodecSupportMap[option] ?: false
                                DropdownMenuItem(
                                    text = { Text(option.getDisplayLabel(hasHwSupport)) },
                                    onClick = {
                                        viewModel.onTranscodeOptionSelected(option)
                                        codecExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Resolution selection
                    var resolutionExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = resolutionExpanded,
                        onExpandedChange = { resolutionExpanded = !resolutionExpanded }
                    ) {
                        OutlinedTextField(
                            value = uiState.transcodeSettings.resolution.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Resolution") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = resolutionExpanded,
                            onDismissRequest = { resolutionExpanded = false }
                        ) {
                            TranscodeResolution.entries.forEach { res ->
                                DropdownMenuItem(
                                    text = { Text(res.label) },
                                    onClick = {
                                        viewModel.onResolutionSelected(res)
                                        resolutionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // HW Bitrate slider
                    Column {
                        Text(
                            text = "Bitrate (HW Encoder): ${uiState.transcodeSettings.bitrateMb} Mbps",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = uiState.transcodeSettings.bitrateMb.toFloat(),
                            onValueChange = { viewModel.onBitrateChanged(it.toInt()) },
                            valueRange = 1f..12f,
                            steps = 10
                        )
                    }

                    // SW CRF slider
                    Column {
                        Text(
                            text = "CRF (SW Encoder): ${uiState.transcodeSettings.crf}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = uiState.transcodeSettings.crf.toFloat(),
                            onValueChange = { viewModel.onCrfChanged(it.toInt()) },
                            valueRange = 0f..63f,
                            steps = 62
                        )
                    }

                    // Max Parallel Jobs slider
                    Column {
                        Text(
                            text = "Max Parallel Transcode Jobs: ${uiState.transcodeSettings.maxParallelJobs}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = uiState.transcodeSettings.maxParallelJobs.toFloat(),
                            onValueChange = { viewModel.onMaxParallelJobsChanged(it.toInt()) },
                            valueRange = 1f..4f,
                            steps = 2
                        )
                    }
                }
            }
        }
    }
}
