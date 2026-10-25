package com.imad.itube.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imad.itube.appContainer
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.VideoDetails
import com.imad.itube.data.model.VideoItem
import com.imad.itube.player.WatchState
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.ErrorBox
import com.imad.itube.ui.components.FeedItemView
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.PillButton
import com.imad.itube.ui.components.VerifiedBadge
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format

/** Callbacks the watch page needs from the activity. */
class WatchCallbacks(
    val onCollapse: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onEnterPip: () -> Unit,
    val onBackground: () -> Unit,
    val onDownload: (VideoItem) -> Unit,
)

@Composable
fun WatchScreen(fullscreen: Boolean, callbacks: WatchCallbacks) {
    val container = LocalContext.current.appContainer
    val controller = container.player
    val state by controller.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val ui = rememberPlayerUiState(controller.player)
    var showSettings by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val playerBox: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            PlayerSurface(controller.player, Modifier.fillMaxSize(), keepScreenOn = ui.isPlaying)
            val title = state.details?.title ?: state.item?.title.orEmpty()
            if (state.error != null && state.details != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), color = Color.White, modifier = Modifier.padding(16.dp))
                        PillButton("Retry", onClick = controller::retry)
                    }
                }
            } else {
                PlayerControls(
                    player = controller.player,
                    ui = ui,
                    title = title,
                    isLive = state.isLive,
                    fullscreen = fullscreen,
                    seekSeconds = settings.seekSeconds,
                    hasNext = state.details?.related?.any { it is VideoItem } == true,
                    onCollapse = callbacks.onCollapse,
                    onToggleFullscreen = callbacks.onToggleFullscreen,
                    onSettings = { showSettings = true },
                    onNext = controller::playNext,
                )
            }
            if (state.loading && state.details == null) {
                // Show the thumbnail while the streams load, like YouTube does.
                AsyncImage(
                    model = state.item?.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (fullscreen) {
        playerBox(Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize().background(Yt.colors.background)) {
            Spacer(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding())
            playerBox(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .pointerInput(Unit) {
                        // Swipe the player down to drop into the mini player.
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > 120f) callbacks.onCollapse()
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                            onVerticalDrag = { _, dy -> dragOffset = (dragOffset + dy).coerceAtLeast(0f) },
                        )
                    }
            )
            WatchDetails(state, callbacks)
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(state, controller) { showSettings = false }
    }
}

@Composable
private fun WatchDetails(state: WatchState, callbacks: WatchCallbacks) {
    val details = state.details
    val controller = LocalContext.current.appContainer.player
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when {
            details != null -> {
                item(key = "title") { TitleBlock(details) { showDescription = true } }
                item(key = "channel") { ChannelBlock(details) }
                item(key = "actions") { ActionsRow(details, callbacks) }
                item(key = "comments") { CommentsTeaser { showComments = true } }
                items(details.related, key = { "rel:" + it.url }) { FeedItemView(it) }
            }
            state.error != null -> item {
                ErrorBox(state.error.orEmpty(), onRetry = controller::retry, Modifier.padding(top = 24.dp))
            }
            else -> item {
                Column(Modifier.padding(16.dp)) {
                    Text(state.item?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (it == 2) 0.6f else 1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Yt.colors.chip),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showDescription && details != null) {
        DescriptionSheet(details) { showDescription = false }
    }
    if (showComments && details != null) {
        CommentsSheet(details.url) { showComments = false }
    }
}

@Composable
private fun TitleBlock(details: VideoDetails, onMore: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onMore)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
    ) {
        Text(
            details.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        val meta = Format.joinDot(
            if (details.isLive) Format.watching(details.viewCount) else Format.views(details.viewCount),
            Format.ago(details.uploadedAt, details.uploadedText),
        )
        Text(
            buildString {
                append(meta)
                details.tags.take(2).forEach { append("  #").append(it.replace(" ", "")) }
                append("  ...more")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Yt.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelBlock(details: VideoDetails) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val library by container.library.data.collectAsState()
    val subscribed = library.subscriptions.any {
        com.imad.itube.data.store.LibraryStore.sameChannel(it.url, details.channelUrl)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openChannel(details.channelUrl) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(details.channelAvatar, 36.dp)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                details.channelName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (details.channelVerified) {
                Spacer(Modifier.width(4.dp))
                VerifiedBadge()
            }
            if (details.channelSubscribers >= 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    Format.compact(details.channelSubscribers),
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        PillButton(if (subscribed) "Subscribed" else "Subscribe", inverted = !subscribed) {
            container.library.toggleSubscription(
                ChannelItem(
                    url = details.channelUrl,
                    name = details.channelName,
                    avatar = details.channelAvatar,
                    subscriberCount = details.channelSubscribers,
                    verified = details.channelVerified,
                )
            )
        }
    }
}

@Composable
private fun ActionsRow(details: VideoDetails, callbacks: WatchCallbacks) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val library by container.library.data.collectAsState()
    val liked = library.liked.any { it.url == details.url }
    val saved = library.watchLater.any { it.url == details.url }
    var disliked by remember(details.url) { mutableStateOf(false) }
    val video = details.toVideoItem()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Yt.colors.chip),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clickable {
                            container.library.toggleLiked(video)
                            if (!liked) disliked = false
                        }
                        .padding(start = 14.dp, end = 12.dp)
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", Modifier.size(20.dp))
                    val likes = details.likeCount.takeIf { it >= 0 }?.let { it + if (liked) 1 else 0 }
                    if (likes != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(Format.compact(likes), style = MaterialTheme.typography.labelLarge)
                    }
                }
                VerticalDivider(Modifier.height(20.dp), color = Yt.colors.divider)
                Box(
                    Modifier
                        .clickable {
                            disliked = !disliked
                            if (disliked && liked) container.library.toggleLiked(video)
                        }
                        .padding(horizontal = 14.dp)
                        .height(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", Modifier.size(20.dp))
                }
            }
        }
        item { ActionChip(Icons.Outlined.Share, "Share") { actions.share(details.title, details.url) } }
        item { ActionChip(Icons.Outlined.Download, "Download") { callbacks.onDownload(video) } }
        item {
            ActionChip(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (saved) "Saved" else "Save") {
                container.library.toggleWatchLater(video)
            }
        }
        item { ActionChip(Icons.Outlined.Headphones, "Background", onClick = callbacks.onBackground) }
        item { ActionChip(Icons.Outlined.PictureInPictureAlt, "Popup", onClick = callbacks.onEnterPip) }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(Yt.colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommentsTeaser(onClick: () -> Unit) {
    Column(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Yt.colors.chip)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text("Comments", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text("Tap to read what people are saying", style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
    }
}

/** The collapsed player that sits above the bottom navigation bar. */
@Composable
fun MiniPlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val controller = container.player
    val state by controller.state.collectAsState()
    val ui = rememberPlayerUiState(controller.player)
    val item = state.item ?: return

    Column(
        modifier
            .fillMaxWidth()
            .background(Yt.colors.miniPlayer)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy -> if (dy < -12) onExpand() }
            },
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Yt.colors.divider)) {
            val fraction = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(YtRed))
        }
        Row(Modifier.height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerSurface(controller.player, Modifier.width(110.dp).aspectRatio(16f / 9f), keepScreenOn = ui.isPlaying)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.details?.title ?: item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.details?.channelName ?: item.channelName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                if (controller.player.playWhenReady) controller.player.pause() else controller.player.play()
            }) {
                Icon(
                    if (ui.playWhenReady) Icons.Filled.Pause
                    else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                )
            }
            IconButton(onClick = controller::close) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
    }
}
