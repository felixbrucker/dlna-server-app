package com.felixbrucker.dlnaserver.core.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import com.felixbrucker.dlnaserver.core.datastore.AppSettingsRepository
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.core.model.TranscodeOption
import com.felixbrucker.dlnaserver.core.util.HardwareCodecUtils
import com.felixbrucker.dlnaserver.feature.service.DlnaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaRepository @Inject constructor(
    private val dlnaServerEngine: DlnaServerEngine,
    private val sharedDirectoryRepository: SharedDirectoryRepository,
    private val appSettingsRepository: AppSettingsRepository
) {

    @Volatile
    private var currentTranscodeOption: TranscodeOption = TranscodeOption.NONE

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        dlnaServerEngine.setSharedDirectoriesProvider {
            runBlocking(Dispatchers.IO) {
                sharedDirectoryRepository.getEnabledDirectoriesSync()
            }
        }
        dlnaServerEngine.setTranscodeOptionProvider { currentTranscodeOption }

        repositoryScope.launch {
            appSettingsRepository.selectedTranscodeOption.collect { option ->
                currentTranscodeOption = option
                val hasHw = HardwareCodecUtils.hasHardwareEncoderSupport(option)
                val label = option.getDisplayLabel(hasHw)
                dlnaServerEngine.updateTranscodeDisplayInfo(label)
            }
        }
    }

    val serverInfo: StateFlow<DlnaServerInfo> = dlnaServerEngine.serverInfo

    fun startServerService(context: Context) {
        val intent = Intent(context, DlnaService::class.java).apply {
            action = DlnaService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopServerService(context: Context) {
        val intent = Intent(context, DlnaService::class.java).apply {
            action = DlnaService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun startEngineDirect(): Boolean {
        return dlnaServerEngine.startServer()
    }

    fun stopEngineDirect() {
        dlnaServerEngine.stopServer()
    }

    fun isEngineRunning(): Boolean {
        return dlnaServerEngine.isRunning()
    }
}
