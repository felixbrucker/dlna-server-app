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
            jobLines = listOf("Preparing jobs..."),
            overallProgress = 0,
            subText = "--:--"
        )

        val foregroundType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
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
                val title = "Transcoding ($runningCount running, $queuedCount queued)"

                val sumProgress = activeJobs.sumOf { it.progress.toDouble() }.toFloat()
                val overallProgress = ((sumProgress / activeJobs.size.toFloat()) * 100).toInt()

                val totalEtaSeconds = activeJobs.filter { it.status == TranscodeJobStatus.RUNNING }.sumOf { it.etaSeconds }
                val subText = if (totalEtaSeconds > 0) {
                    val m = totalEtaSeconds / 60
                    val s = totalEtaSeconds % 60
                    String.format("%02d:%02d", m, s)
                } else "--:--"

                val jobLines = activeJobs.map { job ->
                    val truncatedName = truncateFilename(job.inputFile.name, 18)
                    when (job.status) {
                        TranscodeJobStatus.RUNNING -> {
                            val percent = (job.progress * 100).toInt()
                            "$truncatedName: $percent% (${job.speed}, ETA ${job.eta})"
                        }
                        TranscodeJobStatus.QUEUED -> "$truncatedName: Queued"
                        else -> "$truncatedName: ${job.status.name}"
                    }
                }

                val notification = buildNotification(title, jobLines, overallProgress, subText)

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun truncateFilename(filename: String, maxLength: Int): String {
        return if (filename.length > maxLength) {
            filename.take(maxLength - 1) + "…"
        } else {
            filename
        }
    }

    private fun buildNotification(
        title: String,
        jobLines: List<String>,
        overallProgress: Int,
        subText: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(subText)

        for (line in jobLines) {
            inboxStyle.addLine(line)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(jobLines.firstOrNull() ?: "")
            .setSubText(subText)
            .setStyle(inboxStyle)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, overallProgress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transcoding Service",
            NotificationManager.IMPORTANCE_HIGH
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
    }
}
