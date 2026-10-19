package com.felixbrucker.dlnaserver.core.model

data class ConnectedClientInfo(
    val ipAddress: String,
    val userAgent: String = "Unknown",
    val currentActivity: String = "Browsing",
    val lastAccessTimestamp: Long = System.currentTimeMillis()
)

data class DlnaServerInfo(
    val status: ServerStatus = ServerStatus.STOPPED,
    val ipAddress: String = "0.0.0.0",
    val boundInterface: String = "None",
    val port: Int = 8192,
    val connectedDeviceCount: Int = 0,
    val connectedClients: List<ConnectedClientInfo> = emptyList(),
    val errorMessage: String? = null
)
