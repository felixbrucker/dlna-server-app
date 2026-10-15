package com.felixbrucker.dlnaserver.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felixbrucker.dlnaserver.core.model.SharedDirectory

@Composable
fun DirectoryList(
    directories: List<SharedDirectory>,
    onToggleDirectory: (SharedDirectory) -> Unit,
    onDeleteDirectory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (directories.isEmpty()) {
        EmptyDirectoriesCard(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = directories, key = { it.id }) { directory ->
                DirectoryItemCard(
                    directory = directory,
                    onToggleDirectory = onToggleDirectory,
                    onDeleteDirectory = onDeleteDirectory
                )
            }
        }
    }
}
