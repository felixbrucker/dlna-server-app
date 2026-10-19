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
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeJobStatus
import com.felixbrucker.dlnaserver.core.transcoder.TranscodeQueueManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TranscodeService : Service() {

    @Inject
    lateinit var transcodeQueueManager: TranscodeQueueManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification("Initializing transcoding...", 0, 0, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            if (foregroundType != 0) {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        observeQueue()
        return START_NOT_STICKY
    }

    private fun observeQueue() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            transcodeQueueManager.jobs.collect { jobs ->
                val activeJobs = jobs.filter {
                    it.status == TranscodeJobStatus.QUEUED || it.status == TranscodeJobStatus.RUNNING
                }

                if (activeJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                val runningJob = activeJobs.firstOrNull { it.status == TranscodeJobStatus.RUNNING }
                val completedCount = jobs.count { it.status == TranscodeJobStatus.COMPLETED || it.status == TranscodeJobStatus.FAILED }
                val totalCount = jobs.size
                val overallPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 0

                val title = if (runningJob != null) {
                    "Transcoding: ${runningJob.inputFile.name}"
                } else {
                    "Transcoding Queued (${activeJobs.size} remaining)"
                }

                val content = if (runningJob != null) {
                    "Progress: ${(runningJob.progress * 100).toInt()}% | Speed: ${runningJob.speed} | ETA: ${runningJob.eta}"
                } else {
                    "Queued jobs waiting..."
                }

                val progress = if (runningJob != null) (runningJob.progress * 100).toInt() else overallPercent
                val notification = buildNotification(title, content, progress, runningJob != null)

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(
        title: String,
        content: Any,
        progress: Int,
        isIndeterminate: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (content is String) content else content.toString()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_dlna)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, progress.coerceIn(0, 100), isIndeterminate && progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcoding Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows transcoding background job progress and statistics."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "transcode_service_channel"
        private const val NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, TranscodeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
