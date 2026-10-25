package com.imad.itube.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.data.model.VideoItem
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.EmptyBox
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.PillButton
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.Thumbnail
import com.imad.itube.ui.components.VideoRow
import com.imad.itube.ui.components.YtTopBar
import com.imad.itube.ui.theme.Yt

enum class VideoList(val title: String) { History("History"), WatchLater("Watch later"), Liked("Liked videos") }

/** The "You" tab. */
@Composable
fun LibraryScreen(
    onOpenList: (VideoList) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val data by container.library.data.collectAsState()

    ScreenScaffold(topBar = { YtTopBar() }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Yt.colors.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(48.dp), tint = Yt.colors.textSecondary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("You", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Your library is stored on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = Yt.colors.textSecondary,
                        )
                    }
                }
                PillButton(
                    "Sign in with Google",
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { actions.toast("Google sign-in arrives in the next update") }
                Spacer(Modifier.height(16.dp))
            }

            item { SectionHeader("History") { onOpenList(VideoList.History) } }
            item {
                if (data.history.isEmpty()) {
                    Text(
                        "Videos you watch will show up here",
                        color = Yt.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(data.history.take(20), key = { it.url }) { v -> HistoryTile(v) }
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Yt.colors.divider) }
            item { MenuRow(Icons.Outlined.WatchLater, "Watch later", "${data.watchLater.size} videos") { onOpenList(VideoList.WatchLater) } }
            item { MenuRow(Icons.Outlined.ThumbUp, "Liked videos", "${data.liked.size} videos") { onOpenList(VideoList.Liked) } }
            item { MenuRow(Icons.Outlined.Download, "Downloads", null, onOpenDownloads) }
            item { MenuRow(Icons.Outlined.History, "Watch history", "${data.history.size} videos") { onOpenList(VideoList.History) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Yt.colors.divider) }
            item { MenuRow(Icons.Outlined.Settings, "Settings", null, onOpenSettings) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            "View all",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onViewAll).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HistoryTile(v: VideoItem) {
    val actions = LocalAppActions.current
    Column(
        Modifier
            .width(150.dp)
            .clickable { actions.openVideo(v) }
            .padding(4.dp),
    ) {
        Thumbnail(v.thumbnail, v.duration, v.isLive, Modifier.fillMaxWidth(), rounded = true)
        Text(
            v.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(v.channelName.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Yt.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
        }
    }
}

@Composable
fun VideoListScreen(list: VideoList, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val data by container.library.data.collectAsState()
    val videos = when (list) {
        VideoList.History -> data.history
        VideoList.WatchLater -> data.watchLater
        VideoList.Liked -> data.liked
    }
    ScreenScaffold(
        topBar = {
            BackTopBar(list.title, onBack) {
                if (list == VideoList.History && videos.isNotEmpty()) {
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).clickable { container.library.clearHistory() }.padding(12.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (videos.isEmpty()) {
            EmptyBox("No videos yet", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(videos, key = { it.url }) { v ->
                    val extra = when (list) {
                        VideoList.History -> listOf("Remove from watch history" to { container.library.removeFromHistory(v.url) })
                        VideoList.Liked -> listOf("Remove from Liked videos" to { container.library.toggleLiked(v) })
                        VideoList.WatchLater -> emptyList()
                    }
                    VideoRow(v, extra)
                }
            }
        }
    }
}
