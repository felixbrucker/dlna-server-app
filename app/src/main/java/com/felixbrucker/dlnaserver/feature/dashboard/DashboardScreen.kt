package com.felixbrucker.dlnaserver.feature.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJob
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
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

    if (uiState.isTranscodePopoverOpen) {
        TranscodeQueuePopoverDialog(
            jobs = uiState.activeTranscodeJobs,
            onDismiss = { viewModel.togglePopover() }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("DLNA Media Server") },
                actions = {
                    val hasRunningOrQueued = uiState.activeTranscodeJobs.any {
                        it.status == TranscodeJobStatus.RUNNING || it.status == TranscodeJobStatus.QUEUED
                    }
                    val runningJob = uiState.activeTranscodeJobs.firstOrNull { it.status == TranscodeJobStatus.RUNNING }

                    IconButton(onClick = { viewModel.togglePopover() }) {
                        if (hasRunningOrQueued) {
                            val progress = runningJob?.progress ?: 0f
                            CircularProgressIndicator(
                                progress = { if (progress > 0) progress else 0.2f },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            val infiniteTransition = rememberInfiniteTransition(label = "idle_spin")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing)
                                ),
                                label = "rotation"
                            )
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Transcode Queue",
                                modifier = Modifier.rotate(if (uiState.activeTranscodeJobs.isNotEmpty()) rotation else 0f)
                            )
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
                    onRequestPermission = { viewModel.setPermissionGranted(true) }
                )
            } else {
                ServerStatusCard(
                    serverInfo = uiState.serverInfo,
                    onToggleServer = { viewModel.toggleServer(context) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search video files...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.rescanFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan Files")
                    }
                }

                if (uiState.mediaFiles.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isBlank()) "No video files found in shared directories." else "No files match search query.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(items = uiState.mediaFiles, key = { it.file.absolutePath }) { item ->
                            MediaFileItemCard(
                                item = item,
                                onTranscodeClick = { viewModel.enqueueTranscode(item.file) }
                            )
                        }
                    }
                }
            }
        }
    }
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
private fun TranscodeQueuePopoverDialog(
    jobs: List<TranscodeJob>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Transcode Jobs",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
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
