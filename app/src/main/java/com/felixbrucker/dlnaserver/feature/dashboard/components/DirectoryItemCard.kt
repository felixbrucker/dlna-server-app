package com.felixbrucker.dlnaserver.feature.dashboard.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.felixbrucker.dlnaserver.core.model.SharedDirectory

@Composable
fun DirectoryItemCard(
    directory: SharedDirectory,
    onToggleDirectory: (SharedDirectory) -> Unit,
    onDeleteDirectory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (directory.isEnabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectoryItemInfo(directory = directory, modifier = Modifier.weight(1f))
            DirectoryItemActions(
                directory = directory,
                onToggleDirectory = onToggleDirectory,
                onDeleteDirectory = onDeleteDirectory
            )
        }
    }
}

@Composable
private fun DirectoryItemInfo(
    directory: SharedDirectory,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = directory.displayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = directory.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectoryItemActions(
    directory: SharedDirectory,
    onToggleDirectory: (SharedDirectory) -> Unit,
    onDeleteDirectory: (Long) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = directory.isEnabled,
            onCheckedChange = { onToggleDirectory(directory) }
        )
        IconButton(onClick = { onDeleteDirectory(directory.id) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Directory",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
