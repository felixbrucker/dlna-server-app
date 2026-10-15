package com.felixbrucker.dlnaserver.core.model

data class SharedDirectory(
    val id: Long = 0,
    val path: String,
    val displayName: String,
    val isEnabled: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)
