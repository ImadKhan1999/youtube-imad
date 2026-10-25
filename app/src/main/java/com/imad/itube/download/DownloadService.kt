package com.imad.itube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.imad.itube.MainActivity
import com.imad.itube.R
import com.imad.itube.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that keeps downloads running with a progress notification. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService<NotificationManager>()!!
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Preparing download…", null, 0f),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (job?.isActive != true) {
            val manager = appContainer.downloads
            job = scope.launch {
                manager.runQueue { item ->
                    if (item == null) return@runQueue
                    val text = when (item.status) {
                        DownloadStatus.Done -> "Downloaded"
                        else -> if (item.bytesTotal > 0) "${(item.progress * 100).toInt()}%" else "Downloading…"
                    }
                    val queued = manager.items.value.count { it.status == DownloadStatus.Queued }
                    NotificationManagerCompat.from(this@DownloadService).runCatching {
                        notify(NOTIFICATION_ID, notification(item.video.title, text + if (queued > 0) " · $queued more" else "", item.progress))
                    }
                }
                val done = manager.items.value.count { it.status == DownloadStatus.Done }
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                NotificationManagerCompat.from(this@DownloadService).runCatching {
                    notify(DONE_ID, NotificationCompat.Builder(this@DownloadService, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Downloads finished")
                        .setContentText("$done videos saved in iTube")
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .build())
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 1,
        Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notification(title: String, text: String?, progress: Float) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setContentIntent(openApp())
            .build()

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 41
        const val DONE_ID = 42
    }
}
