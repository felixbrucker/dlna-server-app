package com.felixbrucker.dlnaserver.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.util.PermissionUtils
import com.felixbrucker.dlnaserver.feature.dashboard.components.DirectoryList
import com.felixbrucker.dlnaserver.feature.dashboard.components.FolderPickerDialog
import com.felixbrucker.dlnaserver.feature.dashboard.components.PermissionCard
import com.felixbrucker.dlnaserver.feature.dashboard.components.ServerStatusCard

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setPermissionGranted(PermissionUtils.hasAllPermissions(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val hasAll = PermissionUtils.hasAllPermissions(context)
        viewModel.setPermissionGranted(hasAll)
        if (!hasAll) {
            PermissionUtils.openAppSettings(context)
        }
    }

    val requestPermissionsAction = {
        val missingPermissions = PermissionUtils.getMissingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        } else if (!PermissionUtils.hasAllPermissions(context)) {
            PermissionUtils.openAppSettings(context)
        } else {
            viewModel.setPermissionGranted(granted = true)
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = PermissionUtils.getMissingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        }
        viewModel.setPermissionGranted(PermissionUtils.hasAllPermissions(context))
    }

    DashboardContent(
        uiState = uiState,
        onToggleServer = {
            if (!PermissionUtils.hasAllPermissions(context)) {
                requestPermissionsAction()
            } else {
                viewModel.toggleServer(context)
            }
        },
        onSelectTranscodeOption = { viewModel.selectTranscodeOption(it) },
        onToggleDirectory = { viewModel.toggleDirectory(it) },
        onDeleteDirectory = { viewModel.deleteDirectory(it) },
        onOpenFolderPicker = {
            if (!PermissionUtils.hasAllPermissions(context)) {
                requestPermissionsAction()
            } else {
                viewModel.setFolderPickerOpen(isOpen = true)
            }
        },
        onRequestPermissions = requestPermissionsAction,
        modifier = modifier,
    )

    if (uiState.isFolderPickerOpen) {
        FolderPickerDialog(
            onDismissRequest = { viewModel.setFolderPickerOpen(isOpen = false) },
        ) { path, name ->
            viewModel.addDirectory(path, name)
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onToggleServer: () -> Unit,
    onSelectTranscodeOption: (TranscodeOption) -> Unit,
    onToggleDirectory: (SharedDirectory) -> Unit,
    onDeleteDirectory: (Long) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { DashboardTopAppBar() },
        floatingActionButton = { AddDirectoryFab(onClick = onOpenFolderPicker) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (!uiState.permissionGranted) {
                PermissionCard(onRequestPermission = onRequestPermissions)
                Spacer(modifier = Modifier.height(16.dp))
            }
            ServerStatusCard(
                serverInfo = uiState.serverInfo,
                selectedTranscodeOption = uiState.selectedTranscodeOption,
                availableTranscodeOptions = uiState.availableTranscodeOptions,
                onToggleServer = onToggleServer,
                onSelectTranscodeOption = onSelectTranscodeOption
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader()
            Spacer(modifier = Modifier.height(8.dp))
            DirectoryList(
                directories = uiState.sharedDirectories,
                onToggleDirectory = onToggleDirectory,
                onDeleteDirectory = onDeleteDirectory,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopAppBar() {
    TopAppBar(
        title = { Text("DLNA Server") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SectionHeader() {
    Text(
        text = "Shared Directories",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AddDirectoryFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Shared Directory")
    }
}
