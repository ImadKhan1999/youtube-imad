package com.imad.itube.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.EmptyBox
import com.imad.itube.ui.components.LoadingBox
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.VideoRow
import com.imad.itube.ui.theme.YtRed
import kotlinx.coroutines.launch

/** The bell: new uploads from your subscriptions over the last week. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val alerts = container.uploadAlerts
    val recent by alerts.recent.collectAsState()
    val loading by alerts.loading.collectAsState()
    val library by container.library.data.collectAsState()
    // Freeze "unread" at the moment the screen opened, then mark everything read on leaving.
    val seenAt = remember { container.settings.settings.value.notificationsSeenAt }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { alerts.refresh() } }
    DisposableEffect(Unit) { onDispose { alerts.markAllSeen() } }

    ScreenScaffold(topBar = { BackTopBar("Notifications", onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = loading && recent.isNotEmpty(),
            onRefresh = { scope.launch { runCatching { alerts.refresh(force = true) } } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                library.subscriptions.isEmpty() -> EmptyBox(
                    "No notifications",
                    "Subscribe to channels, or sign in to import your subscriptions, to hear about new videos.",
                )
                loading && recent.isEmpty() -> LoadingBox(Modifier.padding(top = 120.dp))
                recent.isEmpty() -> EmptyBox("You're all caught up", "No new videos from your subscriptions this week.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(recent, key = { it.url }) { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(start = 6.dp).size(6.dp)) {
                                if ((v.uploadedAt ?: 0) > seenAt) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(YtRed))
                                }
                            }
                            Box(Modifier.weight(1f)) { VideoRow(v) }
                        }
                    }
                }
            }
        }
    }
}
