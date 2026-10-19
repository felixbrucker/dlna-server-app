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
        val initialNotification = buildNotification(
            title = "Transcoding Service",
            summaryText = "Initializing transcoding...",
            jobLines = listOf("Preparing jobs..."),
            overallProgress = 0
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            0
        }

        if (foregroundType != 0) {
            startForeground(NOTIFICATION_ID, initialNotification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
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

                val runningCount = activeJobs.count { it.status == TranscodeJobStatus.RUNNING }
                val queuedCount = activeJobs.count { it.status == TranscodeJobStatus.QUEUED }
                val totalActive = activeJobs.size

                val title = "Transcoding ($runningCount running, $queuedCount queued)"

                val jobLines = activeJobs.map { job ->
                    when (job.status) {
                        TranscodeJobStatus.RUNNING -> {
                            val percent = (job.progress * 100).toInt()
                            "${job.inputFile.name}: $percent% (${job.speed}, ETA ${job.eta})"
                        }
                        TranscodeJobStatus.QUEUED -> "${job.inputFile.name}: Queued"
                        else -> "${job.inputFile.name}: ${job.status.name}"
                    }
                }

                val summaryText = jobLines.firstOrNull() ?: "Processing..."
                val runningJob = activeJobs.firstOrNull { it.status == TranscodeJobStatus.RUNNING }
                val progress = if (runningJob != null) (runningJob.progress * 100).toInt() else 0

                val notification = buildNotification(title, summaryText, jobLines, progress)

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(
        title: String,
        summaryText: String,
        jobLines: List<String>,
        overallProgress: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = jobLines.joinToString("\n")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, overallProgress.coerceIn(0, 100), overallProgress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
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

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "transcode_service_channel"
        private const val NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, TranscodeService::class.java)
            context.startForegroundService(intent)
        }
    }
}
