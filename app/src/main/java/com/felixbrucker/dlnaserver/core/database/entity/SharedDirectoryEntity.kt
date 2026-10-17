package com.felixbrucker.dlnaserver.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_directories",
    indices = [Index(value = ["path"], unique = true)]
)
data class SharedDirectoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val displayName: String,
    val isEnabled: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)
