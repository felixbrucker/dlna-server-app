package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaServerEngine @Inject constructor(
    private val httpMediaServer: HttpMediaServer
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ssdpServer: SsdpServer? = null

    private val _serverInfo = MutableStateFlow(DlnaServerInfo())
    val serverInfo: StateFlow<DlnaServerInfo> = _serverInfo.asStateFlow()

    @Synchronized
    fun startServer(): Boolean {
        if (_serverInfo.value.status == ServerStatus.RUNNING) return true

        _serverInfo.value = _serverInfo.value.copy(status = ServerStatus.STARTING)

        val netIf = NetworkUtils.getActiveNetworkInterface()
        if (netIf == null) {
            _serverInfo.value = DlnaServerInfo(
                status = ServerStatus.ERROR,
                errorMessage = "No active network interface (Wi-Fi or Ethernet) found."
            )
            return false
        }

        try {
            val port = 8192
            val ssdp = SsdpServer(port = port)

            httpMediaServer.start(netIf.ipAddress, engineScope)
            ssdp.start(netIf.ipAddress, netIf.name, engineScope)

            ssdpServer = ssdp

            engineScope.launch {
                httpMediaServer.connectedClients.collect { clients ->
                    _serverInfo.value = _serverInfo.value.copy(
                        connectedDeviceCount = clients.size
                    )
                }
            }

            _serverInfo.value = DlnaServerInfo(
                status = ServerStatus.RUNNING,
                ipAddress = netIf.ipAddress,
                boundInterface = netIf.displayName,
                port = port,
                connectedDeviceCount = 0,
                errorMessage = null
            )
            return true
        } catch (e: Exception) {
            _serverInfo.value = DlnaServerInfo(
                status = ServerStatus.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to start DLNA server"
            )
            stopServer()
            return false
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            httpMediaServer.stop()
            ssdpServer?.stop()
        } catch (_: Exception) {
        }
        ssdpServer = null

        _serverInfo.value = DlnaServerInfo(
            status = ServerStatus.STOPPED,
            ipAddress = "0.0.0.0",
            boundInterface = "None",
            port = 8192,
            connectedDeviceCount = 0,
            errorMessage = null
        )
    }

    fun isRunning(): Boolean {
        return _serverInfo.value.status == ServerStatus.RUNNING
    }

    fun shutdown() {
        stopServer()
        engineScope.cancel()
    }
}
