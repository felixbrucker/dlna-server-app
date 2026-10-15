package com.felixbrucker.dlnaserver.core.model

data class MediaItem(
    val id: String,
    val title: String,
    val filePath: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val mimeType: String = "application/octet-stream",
    val upnpClass: String = "object.item.mediaItem",
    val childCount: Int = 0
)
