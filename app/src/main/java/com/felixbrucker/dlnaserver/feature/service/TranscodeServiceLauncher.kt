package com.felixbrucker.dlnaserver.feature.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscodeServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startService() {
        val intent = Intent(context, TranscodeService::class.java)
        context.startForegroundService(intent)
    }
}
