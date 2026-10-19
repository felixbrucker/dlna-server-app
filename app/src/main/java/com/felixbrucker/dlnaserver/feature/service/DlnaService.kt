package com.felixbrucker.dlnaserver.feature.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.felixbrucker.dlnaserver.MainActivity
import com.felixbrucker.dlnaserver.R
import com.felixbrucker.dlnaserver.core.dlna.DlnaServerEngine
import com.felixbrucker.dlnaserver.core.model.ServerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DlnaService : Service() {

    @Inject
    lateinit var dlnaServerEngine: DlnaServerEngine

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var statusCollectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                createNotificationChannel()
                val notification = buildNotification(
                    interfaceName = "Starting...",
                    ipAddress = "0.0.0.0",
                    connectedDevices = 0
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val success = dlnaServerEngine.startServer()
                if (success) {
                    observeServerStatus()
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                dlnaServerEngine.stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun observeServerStatus() {
        statusCollectJob?.cancel()
        statusCollectJob = serviceScope.launch {
            dlnaServerEngine.serverInfo.collect { info ->
                if (info.status == ServerStatus.RUNNING) {
                    val notification = buildNotification(
                        interfaceName = info.boundInterface,
                        ipAddress = info.ipAddress,
                        connectedDevices = info.connectedDeviceCount
                    )
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else if (info.status == ServerStatus.STOPPED || info.status == ServerStatus.ERROR) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(
        interfaceName: String,
        ipAddress: String,
        connectedDevices: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DlnaService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = "Interface: $interfaceName ($ipAddress) | Devices: $connectedDevices"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DLNA Server Running")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_dlna)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Server",
                pendingStop
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DLNA Server Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the active status of the DLNA server."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        dlnaServerEngine.stopServer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.felixbrucker.dlnaserver.action.START"
        const val ACTION_STOP = "com.felixbrucker.dlnaserver.action.STOP"
        private const val CHANNEL_ID = "dlna_server_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
