package com.imad.itube.ui.downloads

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.download.DownloadItem
import com.imad.itube.download.DownloadKind
import com.imad.itube.download.DownloadStatus
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.EmptyBox
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.Thumbnail
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format

@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlayed: () -> Unit) {
    val container = LocalContext.current.appContainer
    val items by container.downloads.items.collectAsState()
    val sorted = items.sortedByDescending { it.createdAt }

    val waitingForWifi by container.downloads.waitingForWifi.collectAsState()
    val active = items.any { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Running }
    val paused = items.any { it.status == DownloadStatus.Paused }

    ScreenScaffold(topBar = {
        BackTopBar("Downloads", onBack) {
            if (active) {
                IconButton(onClick = { container.downloads.pauseAll() }) { Icon(Icons.Filled.Pause, "Pause all") }
            } else if (paused) {
                IconButton(onClick = { container.downloads.resumeAll() }) { Icon(Icons.Filled.PlayArrow, "Resume all") }
            }
        }
    }) { padding ->
        if (sorted.isEmpty()) {
            EmptyBox(
                "No downloads yet",
                "Tap Download under a video, or on a playlist, to save it for offline viewing.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (waitingForWifi) {
                    item {
                        Text(
                            "Waiting for Wi-Fi. Downloads start when you connect, or turn off \"Download over Wi-Fi only\" in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Yt.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(sorted, key = { it.id }) { d ->
                    DownloadRow(d) {
                        val uri = d.fileUri ?: return@DownloadRow
                        container.player.playLocal(d.video, Uri.parse(uri))
                        onPlayed()
                    }
                }
            }
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0)
}

@Composable
private fun DownloadRow(d: DownloadItem, onPlay: () -> Unit) {
    val manager = LocalContext.current.appContainer.downloads
    val actions = LocalAppActions.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = d.status == DownloadStatus.Done, onClick = onPlay)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Thumbnail(d.video.thumbnail, d.video.duration, false, Modifier.width(150.dp), rounded = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Format.joinDot(
                    d.video.channelName,
                    d.qualityLabel ?: if (d.kind == DownloadKind.Audio) "Audio" else "${d.maxHeight}p",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                maxLines = 1,
            )
            val status = when (d.status) {
                DownloadStatus.Queued -> "Waiting…"
                DownloadStatus.Paused -> if (d.bytesTotal > 0) {
                    "Paused · ${(d.progress * 100).toInt()}% · ${sizeText(d.bytesDone)} of ${sizeText(d.bytesTotal)}"
                } else {
                    "Paused"
                }
                DownloadStatus.Running -> if (d.bytesTotal > 0) {
                    "${(d.progress * 100).toInt()}% · ${sizeText(d.bytesDone)} of ${sizeText(d.bytesTotal)}"
                } else {
                    "Downloading… ${sizeText(d.bytesDone)}"
                }
                DownloadStatus.Done -> "Downloaded · ${sizeText(d.bytesTotal)}"
                DownloadStatus.Failed -> "Failed: ${d.error ?: "unknown error"}"
                DownloadStatus.Cancelled -> "Cancelled"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (d.status == DownloadStatus.Failed) YtRed else Yt.colors.textSecondary,
                maxLines = 2,
            )
            if (d.status == DownloadStatus.Running || (d.status == DownloadStatus.Paused && d.bytesTotal > 0)) {
                Spacer(Modifier.height(4.dp))
                if (d.bytesTotal > 0) {
                    LinearProgressIndicator(progress = { d.progress }, color = YtRed, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(color = YtRed, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        when (d.status) {
            DownloadStatus.Queued, DownloadStatus.Running ->
                IconButton(onClick = { manager.pause(d.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Pause, "Pause", Modifier.size(20.dp))
                }
            DownloadStatus.Paused ->
                IconButton(onClick = { manager.resume(d.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.PlayArrow, "Resume", Modifier.size(20.dp))
                }
            else -> {}
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "More", Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (d.status == DownloadStatus.Done) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; onPlay() })
                }
                if (d.status == DownloadStatus.Queued || d.status == DownloadStatus.Running || d.status == DownloadStatus.Paused) {
                    DropdownMenuItem(text = { Text("Cancel") }, onClick = { menu = false; manager.cancel(d.id) })
                }
                if (d.status == DownloadStatus.Failed || d.status == DownloadStatus.Cancelled) {
                    DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; manager.retry(d.id) })
                }
                DropdownMenuItem(text = { Text("Watch online") }, onClick = { menu = false; actions.openVideo(d.video) })
                DropdownMenuItem(
                    text = { Text(if (d.status == DownloadStatus.Done) "Delete from device" else "Remove") },
                    onClick = { menu = false; manager.delete(d.id) },
                )
            }
        }
    }
}

