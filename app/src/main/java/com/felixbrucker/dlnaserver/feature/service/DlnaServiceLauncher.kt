package com.felixbrucker.dlnaserver.feature.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startService() {
        val intent = Intent(context, DlnaService::class.java).apply {
            action = DlnaService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopService() {
        val intent = Intent(context, DlnaService::class.java).apply {
            action = DlnaService.ACTION_STOP
        }
        context.startService(intent)
    }
}
