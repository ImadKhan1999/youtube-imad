package com.imad.itube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.FeedItem
import com.imad.itube.data.model.PlaylistItem
import com.imad.itube.data.model.VideoItem
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format

/** Renders any feed item in the large, full-width YouTube home style. */
@Composable
fun FeedItemView(item: FeedItem, compact: Boolean = false, extraMenu: List<Pair<String, () -> Unit>> = emptyList()) {
    when (item) {
        is VideoItem -> if (compact) VideoRow(item, extraMenu) else VideoCard(item, extraMenu)
        is ChannelItem -> ChannelRow(item)
        is PlaylistItem -> PlaylistRow(item)
    }
}

@Composable
fun Thumbnail(
    url: String?,
    duration: Long,
    isLive: Boolean,
    modifier: Modifier = Modifier,
    rounded: Boolean = false,
) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .then(if (rounded) Modifier.clip(RoundedCornerShape(8.dp)) else Modifier)
            .background(Yt.colors.chip),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        val badge = when {
            isLive -> "LIVE"
            duration > 0 -> Format.duration(duration)
            else -> null
        }
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isLive) YtRed else Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private fun VideoItem.metaLine(includeChannel: Boolean): String = Format.joinDot(
    if (includeChannel) channelName else null,
    if (isLive) Format.watching(viewCount) else Format.views(viewCount),
    if (isLive) null else Format.ago(uploadedAt, uploadedText),
)

@Composable
fun VideoCard(video: VideoItem, extraMenu: List<Pair<String, () -> Unit>> = emptyList()) {
    val actions = LocalAppActions.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openVideo(video) }
            .padding(bottom = 12.dp),
    ) {
        Thumbnail(video.thumbnail, video.duration, video.isLive, Modifier.fillMaxWidth())
        Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
            Avatar(
                video.channelAvatar,
                36.dp,
                Modifier.clickable(enabled = video.channelUrl != null) {
                    video.channelUrl?.let(actions.openChannel)
                },
                name = video.channelName,
                channelUrl = video.channelUrl,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    video.metaLine(includeChannel = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            VideoMenu(video, extraMenu)
        }
    }
}

/** Compact row: small thumbnail on the left (history, playlists, Watch later). */
@Composable
fun VideoRow(video: VideoItem, extraMenu: List<Pair<String, () -> Unit>> = emptyList()) {
    val actions = LocalAppActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openVideo(video) }
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Thumbnail(video.thumbnail, video.duration, video.isLive, Modifier.width(160.dp), rounded = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                video.channelName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                video.metaLine(includeChannel = false),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                maxLines = 1,
            )
        }
        VideoMenu(video, extraMenu)
    }
}

@Composable
private fun VideoMenu(video: VideoItem, extraMenu: List<Pair<String, () -> Unit>>) {
    val actions = LocalAppActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val saved = actions.isInWatchLater(video.url)
            DropdownMenuItem(
                text = { Text(if (saved) "Remove from Watch later" else "Save to Watch later") },
                leadingIcon = { Icon(Icons.Outlined.WatchLater, null) },
                onClick = {
                    open = false
                    actions.toggleWatchLater(video)
                    actions.toast(if (saved) "Removed from Watch later" else "Saved to Watch later")
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = {
                    open = false
                    actions.share(video.title, video.url)
                },
            )
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                onClick = {
                    open = false
                    actions.download(video)
                },
            )
            extraMenu.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
fun ChannelRow(channel: ChannelItem) {
    val actions = LocalAppActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openChannel(channel.url) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(136.dp), contentAlignment = Alignment.Center) {
            Avatar(channel.avatar, 88.dp, name = channel.name)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (channel.verified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge()
                }
            }
            Text(
                Format.joinDot(Format.subscribers(channel.subscriberCount)),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
            )
            channel.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistItem) {
    val actions = LocalAppActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openPlaylist(playlist.url) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(Modifier.width(160.dp)) {
            Thumbnail(playlist.thumbnail, 0, false, Modifier.fillMaxWidth(), rounded = true)
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(14.dp))
                if (playlist.videoCount >= 0) {
                    Text("${playlist.videoCount}", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Format.joinDot(playlist.uploaderName, "Playlist"),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
