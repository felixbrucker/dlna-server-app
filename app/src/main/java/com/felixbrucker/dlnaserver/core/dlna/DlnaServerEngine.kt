package com.felixbrucker.dlnaserver.core.dlna

import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import com.felixbrucker.dlnaserver.core.model.SharedDirectory
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.transcoder.VideoTranscoder
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
    private val videoTranscoder: VideoTranscoder
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpMediaServer: HttpMediaServer? = null
    private var ssdpServer: SsdpServer? = null

    private var activeSharedDirectoriesProvider: (() -> List<SharedDirectory>)? = null
    private var activeTranscodeOptionProvider: (() -> TranscodeOption)? = null

    private val _serverInfo = MutableStateFlow(DlnaServerInfo())
    val serverInfo: StateFlow<DlnaServerInfo> = _serverInfo.asStateFlow()

    fun setSharedDirectoriesProvider(provider: () -> List<SharedDirectory>) {
        this.activeSharedDirectoriesProvider = provider
    }

    fun setTranscodeOptionProvider(provider: () -> TranscodeOption) {
        this.activeTranscodeOptionProvider = provider
    }

    fun updateTranscodeDisplayInfo(transcodeDisplayInfo: String) {
        _serverInfo.value = _serverInfo.value.copy(transcodeDisplayInfo = transcodeDisplayInfo)
    }

    @Synchronized
    fun startServer(): Boolean {
        if (_serverInfo.value.status == ServerStatus.RUNNING) return true

        val currentTranscodeInfo = _serverInfo.value.transcodeDisplayInfo
        _serverInfo.value = _serverInfo.value.copy(status = ServerStatus.STARTING)

        val netIf = NetworkUtils.getActiveNetworkInterface()
        if (netIf == null) {
            _serverInfo.value = DlnaServerInfo(
                status = ServerStatus.ERROR,
                transcodeDisplayInfo = currentTranscodeInfo,
                errorMessage = "No active network interface (Wi-Fi or Ethernet) found."
            )
            return false
        }

        try {
            val port = 8192
            val httpServer = HttpMediaServer(
                port = port,
                videoTranscoder = videoTranscoder,
                getTranscodeOption = { activeTranscodeOptionProvider?.invoke() ?: TranscodeOption.NONE },
                getSharedDirectories = { activeSharedDirectoriesProvider?.invoke() ?: emptyList() }
            )
            val ssdp = SsdpServer(port = port)

            httpServer.start(netIf.ipAddress, engineScope)
            ssdp.start(netIf.ipAddress, netIf.name, engineScope)

            httpMediaServer = httpServer
            ssdpServer = ssdp

            engineScope.launch {
                httpServer.connectedClients.collect { clients ->
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
                transcodeDisplayInfo = currentTranscodeInfo,
                errorMessage = null
            )
            return true
        } catch (e: Exception) {
            _serverInfo.value = DlnaServerInfo(
                status = ServerStatus.ERROR,
                transcodeDisplayInfo = currentTranscodeInfo,
                errorMessage = e.localizedMessage ?: "Failed to start DLNA server"
            )
            stopServer()
            return false
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            httpMediaServer?.stop()
            ssdpServer?.stop()
        } catch (_: Exception) {
        }
        httpMediaServer = null
        ssdpServer = null

        _serverInfo.value = DlnaServerInfo(
            status = ServerStatus.STOPPED,
            ipAddress = "0.0.0.0",
            boundInterface = "None",
            port = 8192,
            connectedDeviceCount = 0,
            transcodeDisplayInfo = _serverInfo.value.transcodeDisplayInfo,
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
