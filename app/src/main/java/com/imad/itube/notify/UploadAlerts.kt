package com.imad.itube.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.imad.itube.MainActivity
import com.imad.itube.R
import com.imad.itube.appContainer
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Recent uploads from subscriptions: the bell's list, its badge and the background alerts. */
class UploadAlerts(private val context: Context) {
    private val _recent = MutableStateFlow<List<VideoItem>>(emptyList())
    val recent: StateFlow<List<VideoItem>> = _recent.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val mutex = Mutex()
    private var fetchedAt = 0L

    /** Loads the last week of uploads from (up to 60) subscribed channels. */
    suspend fun refresh(force: Boolean = false): List<VideoItem> = mutex.withLock {
        if (!force && System.currentTimeMillis() - fetchedAt < 15 * 60_000 && _recent.value.isNotEmpty()) {
            return _recent.value
        }
        val container = context.appContainer
        container.library.loaded.first { it }
        val subs = container.library.data.value.subscriptions
        if (subs.isEmpty()) {
            _recent.value = emptyList()
            return emptyList()
        }
        _loading.value = true
        try {
            val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L
            val feed = container.repository.subscriptionFeed(subs.take(60))
                .filter { (it.uploadedAt ?: 0) > weekAgo && !it.isShort }
                .take(60)
            _recent.value = feed
            fetchedAt = System.currentTimeMillis()
            feed
        } finally {
            _loading.value = false
        }
    }

    fun markAllSeen() {
        val newest = _recent.value.maxOfOrNull { it.uploadedAt ?: 0 } ?: System.currentTimeMillis()
        context.appContainer.settings.update { it.copy(notificationsSeenAt = maxOf(newest, System.currentTimeMillis())) }
    }

    /** Posts a system notification for each upload that hasn't had one yet. */
    suspend fun notifyNew() {
        val settings = context.appContainer.settings
        val s = settings.current()
        if (!s.uploadAlerts) return
        val feed = refresh(force = true)
        val since = if (s.notifiedAt == 0L) System.currentTimeMillis() - 6 * 3600_000L else s.notifiedAt
        val fresh = feed.filter { (it.uploadedAt ?: 0) > since }.take(5)
        if (fresh.isNotEmpty() && canNotify()) {
            ensureChannel()
            val nm = NotificationManagerCompat.from(context)
            fresh.forEach { v ->
                val open = PendingIntent.getActivity(
                    context, v.url.hashCode(),
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(v.url)),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(v.channelName ?: "New video")
                    .setContentText(v.title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(v.title))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setGroup(GROUP)
                    .build()
                runCatching { nm.notify(v.url.hashCode(), n) }
            }
        }
        val newest = feed.maxOfOrNull { it.uploadedAt ?: 0 } ?: 0
        settings.update { it.copy(notifiedAt = maxOf(it.notifiedAt, newest, since)) }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New uploads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New videos from channels you're subscribed to"
                },
            )
        }
    }

    /** Schedules (or cancels) the background check that runs every few hours. */
    fun schedule(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<Worker>(4, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            runCatching { applicationContext.appContainer.uploadAlerts.notifyNew() }
                .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val CHANNEL = "uploads"
        private const val GROUP = "uploads"
        private const val WORK = "upload-alerts"
    }
}
