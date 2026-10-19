package com.felixbrucker.dlnaserver.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJob
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import com.felixbrucker.dlnaserver.core.util.PermissionUtils
import com.felixbrucker.dlnaserver.feature.dashboard.components.PermissionCard
import com.felixbrucker.dlnaserver.feature.dashboard.components.ServerStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = PermissionUtils.hasAllPermissions(context)
        viewModel.setPermissionGranted(granted)
        if (!granted) {
            PermissionUtils.openAppSettings(context)
        }
    }

    LaunchedEffect(Unit) {
        val granted = PermissionUtils.hasAllPermissions(context)
        viewModel.setPermissionGranted(granted)
    }

    if (uiState.isSearchDialogOpen) {
        SearchFileDialog(
            uiState = uiState,
            onSearchInputChange = { viewModel.onSearchInputChanged(it) },
            onClearInput = { viewModel.clearSearchInput() },
            onSubmitSearch = { viewModel.submitSearch() },
            onTranscodeClick = { viewModel.enqueueTranscode(it.file) },
            onDismiss = { viewModel.setSearchDialogOpen(false) }
        )
    }

    if (uiState.isTranscodeBottomSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.setBottomSheetOpen(false) },
            sheetState = sheetState
        ) {
            TranscodeQueueBottomSheetContent(jobs = uiState.activeTranscodeJobs)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("DLNA Media Server") },
                actions = {
                    IconButton(onClick = { viewModel.setSearchDialogOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Video Files"
                        )
                    }

                    if (uiState.activeTranscodeJobs.isNotEmpty()) {
                        val runningJob = uiState.activeTranscodeJobs.firstOrNull { it.status == TranscodeJobStatus.RUNNING }
                        val progress = runningJob?.progress ?: 0f
                        val eta = runningJob?.eta ?: "--:--"

                        IconButton(onClick = { viewModel.setBottomSheetOpen(true) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { if (progress > 0) progress else 0.1f },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = eta,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!uiState.permissionGranted) {
                PermissionCard(
                    onRequestPermission = {
                        permissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                    }
                )
            } else {
                ServerStatusCard(
                    serverInfo = uiState.serverInfo,
                    onToggleServer = { viewModel.toggleServer() }
                )
            }
        }
    }
}

@Composable
private fun SearchFileDialog(
    uiState: DashboardUiState,
    onSearchInputChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onSubmitSearch: () -> Unit,
    onTranscodeClick: (DashboardMediaFileItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Video Files") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchInput,
                    onValueChange = onSearchInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter video filename...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    trailingIcon = {
                        if (uiState.searchInput.isNotEmpty()) {
                            IconButton(onClick = onClearInput) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Button(
                    onClick = onSubmitSearch,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Search")
                }

                if (uiState.isScanningFiles) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.submittedSearchQuery.isBlank()) {
                    Text(
                        text = "Enter a search term and tap Search to scan shared directories recursively.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (uiState.mediaFiles.isEmpty()) {
                    Text(
                        text = "No video files found matching \"${uiState.submittedSearchQuery}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        items(items = uiState.mediaFiles, key = { it.file.absolutePath }) { item ->
                            MediaFileItemCard(
                                item = item,
                                onTranscodeClick = { onTranscodeClick(item) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MediaFileItemCard(
    item: DashboardMediaFileItem,
    onTranscodeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.sizeFormatted} • ${item.resolution} • ${item.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onTranscodeClick,
                enabled = !item.isTranscodeDisabled
            ) {
                Text(if (item.isTranscodeDisabled) "Transcoded" else "Transcode")
            }
        }
    }
}

@Composable
private fun TranscodeQueueBottomSheetContent(
    jobs: List<TranscodeJob>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Transcode Queue",
            style = MaterialTheme.typography.titleLarge
        )

        if (jobs.isEmpty()) {
            Text(
                text = "No active or queued transcode jobs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(260.dp)
            ) {
                items(items = jobs, key = { it.id }) { job ->
                    TranscodeJobRow(job = job)
                }
            }
        }
    }
}

@Composable
private fun TranscodeJobRow(job: TranscodeJob) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.inputFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = job.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (job.status) {
                        TranscodeJobStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        TranscodeJobStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }

            if (job.status == TranscodeJobStatus.RUNNING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Speed: ${job.speed}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "ETA: ${job.eta}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else if (job.status == TranscodeJobStatus.FAILED && job.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${job.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
