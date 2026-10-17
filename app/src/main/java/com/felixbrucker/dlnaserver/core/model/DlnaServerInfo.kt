package com.felixbrucker.dlnaserver.core.model

data class DlnaServerInfo(
    val status: ServerStatus = ServerStatus.STOPPED,
    val ipAddress: String = "0.0.0.0",
    val boundInterface: String = "None",
    val port: Int = 8192,
    val connectedDeviceCount: Int = 0,
    val errorMessage: String? = null
)
