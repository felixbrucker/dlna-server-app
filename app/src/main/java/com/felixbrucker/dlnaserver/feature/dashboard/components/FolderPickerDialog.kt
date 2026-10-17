package com.felixbrucker.dlnaserver.feature.dashboard.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FolderPickerDialog(
    onDismissRequest: () -> Unit,
    onFolderSelected: (String, String?) -> Unit
) {
    val initialPath = Environment.getExternalStorageDirectory().absolutePath
    var currentDirectory by remember { mutableStateOf(File(initialPath)) }
    var displayNameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { FolderPickerHeader(currentDirectory = currentDirectory, onNavigateUp = {
            currentDirectory.parentFile?.let { currentDirectory = it }
        }) },
        text = {
            FolderPickerContent(
                currentDirectory = currentDirectory,
                displayNameInput = displayNameInput,
                onDisplayNameChange = { displayNameInput = it },
                onFolderClick = { currentDirectory = it }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onFolderSelected(currentDirectory.absolutePath, displayNameInput.ifEmpty { null })
                    onDismissRequest()
                }
            ) {
                Text("Select Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FolderPickerHeader(
    currentDirectory: File,
    onNavigateUp: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (currentDirectory.parentFile != null) {
            IconButton(onClick = onNavigateUp) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate Up")
            }
        }
        Text(
            text = "Select Directory",
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun FolderPickerContent(
    currentDirectory: File,
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    onFolderClick: (File) -> Unit
) {
    Column {
        Text(
            text = currentDirectory.absolutePath,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = displayNameInput,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        SubfoldersList(currentDirectory = currentDirectory, onFolderClick = onFolderClick)
    }
}

@Composable
private fun SubfoldersList(
    currentDirectory: File,
    onFolderClick: (File) -> Unit
) {
    val subfolders = remember(currentDirectory) {
        currentDirectory.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
        items(items = subfolders, key = { it.absolutePath }) { folder ->
            SubfolderItem(folder = folder, onClick = { onFolderClick(folder) })
        }
    }
}

@Composable
private fun SubfolderItem(
    folder: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
