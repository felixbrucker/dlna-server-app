package com.felixbrucker.dlnaserver.core.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import com.felixbrucker.dlnaserver.core.model.DlnaServerInfo
import com.felixbrucker.dlnaserver.feature.service.DlnaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaRepository @Inject constructor(
    private val dlnaServerEngine: DlnaServerEngine,
    private val sharedDirectoryRepository: SharedDirectoryRepository
) {

    init {
        dlnaServerEngine.setSharedDirectoriesProvider {
            runBlocking(Dispatchers.IO) {
                sharedDirectoryRepository.getEnabledDirectoriesSync()
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
