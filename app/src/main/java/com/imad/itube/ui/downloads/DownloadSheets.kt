package com.imad.itube.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.data.model.VideoItem
import com.imad.itube.download.DownloadKind
import com.imad.itube.ui.components.ChipRow
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.PillButton
import com.imad.itube.ui.components.Thumbnail
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed

/** What the user asked to download. */
sealed interface DownloadRequest {
    data class Single(val video: VideoItem, val heights: List<Int>) : DownloadRequest
    data class Playlist(val title: String, val url: String) : DownloadRequest
}

private val PRESETS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

/** "Download quality" sheet for one video. */
@Composable
fun DownloadSheet(request: DownloadRequest.Single, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val heights = request.heights.distinct().sortedDescending().ifEmpty { PRESETS.filter { it <= 1080 } }
    fun start(kind: DownloadKind, height: Int) {
        val n = container.downloads.enqueue(listOf(request.video), kind, height)
        actions.toast(if (n > 0) "Downloading… see You > Downloads" else "Already downloaded or in the queue")
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Yt.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Download quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            heights.forEach { h ->
                OptionRow(if (h >= 720) Icons.Outlined.HighQuality else Icons.Outlined.Videocam, "${h}p", qualityHint(h)) {
                    start(DownloadKind.Video, h)
                }
            }
            HorizontalDivider(color = Yt.colors.divider, modifier = Modifier.padding(vertical = 4.dp))
            OptionRow(Icons.Outlined.Audiotrack, "Audio only", "M4A, best quality") { start(DownloadKind.Audio, 0) }
        }
    }
}

private fun qualityHint(h: Int) = when {
    h >= 2160 -> "4K"
    h >= 1440 -> "Quad HD"
    h >= 1080 -> "Full HD"
    h >= 720 -> "HD"
    h >= 480 -> "Standard"
    else -> "Data saver"
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
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

/** Playlist download: every video with a checkbox, plus one quality for all of them. */
@Composable
fun PlaylistDownloadSheet(request: DownloadRequest.Playlist, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val videos = remember { mutableStateListOf<VideoItem>() }
    val checked = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val qualityLabels = listOf("1080p", "720p", "480p", "360p", "Audio only")
    val qualityValues = listOf(1080, 720, 480, 360, 0)
    var quality by remember {
        mutableIntStateOf(qualityValues.indexOf(container.settings.settings.value.downloadQuality).takeIf { it >= 0 } ?: 1)
    }

    LaunchedEffect(request.url) {
        try {
            val repo = container.repository
            val first = repo.playlist(request.url)
            fun addAll(list: List<com.imad.itube.data.model.FeedItem>) {
                list.filterIsInstance<VideoItem>().filter { v -> videos.none { it.url == v.url } }.forEach {
                    videos += it
                    checked += it.url
                }
            }
            addAll(first.firstPage.items)
            var next = first.firstPage.next
            var guard = 0
            while (next != null && guard++ < 50) {
                val page = repo.playlistMore(next)
                addAll(page.items)
                next = page.next
            }
        } catch (e: Exception) {
            if (videos.isEmpty()) error = e.message ?: "Couldn't load the playlist"
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Yt.colors.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
            Text(
                "Download playlist",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                request.title,
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ChipRow(qualityLabels, quality, onSelect = { quality = it })
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val all = videos.isNotEmpty() && checked.size == videos.size
                Checkbox(
                    checked = all,
                    onCheckedChange = {
                        checked.clear()
                        if (!all) checked += videos.map { it.url }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = YtRed),
                )
                Text(
                    if (loading) "Loading videos… ${videos.size}" else "Select all (${checked.size} of ${videos.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp, color = YtRed)
                Spacer(Modifier.width(12.dp))
            }
            HorizontalDivider(color = Yt.colors.divider)
            Box(Modifier.weight(1f)) {
                if (error != null) {
                    Text(error.orEmpty(), color = Yt.colors.textSecondary, modifier = Modifier.padding(24.dp))
                }
                LazyColumn {
                    itemsIndexed(videos, key = { _, v -> v.url }) { i, v ->
                        val isChecked = v.url in checked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (isChecked) checked.remove(v.url) else checked += v.url }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { if (it) checked += v.url else checked.remove(v.url) },
                                colors = CheckboxDefaults.colors(checkedColor = YtRed),
                            )
                            Thumbnail(v.thumbnail, v.duration, v.isLive, Modifier.width(100.dp), rounded = true)
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}. ${v.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(v.channelName.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Yt.colors.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
            PillButton(
                "Download ${checked.size} " + if (checked.size == 1) "video" else "videos",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (checked.isEmpty()) return@PillButton
                val chosen = videos.filter { it.url in checked }
                val h = qualityValues[quality]
                val kind = if (h == 0) DownloadKind.Audio else DownloadKind.Video
                container.settings.update { it.copy(downloadQuality = h) }
                val n = container.downloads.enqueue(chosen, kind, if (h == 0) 0 else h)
                actions.toast("Added $n to Downloads")
                onDismiss()
            }
        }
    }
}
