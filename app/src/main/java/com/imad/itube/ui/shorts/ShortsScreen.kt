package com.imad.itube.ui.shorts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import coil3.compose.AsyncImage
import com.imad.itube.appContainer
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.VideoItem
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.ui.watch.CaptionOverlay
import com.imad.itube.ui.watch.CommentsSheet
import com.imad.itube.ui.watch.DoubleSpeedPill
import com.imad.itube.ui.watch.holdForDoubleSpeed
import com.imad.itube.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** YouTube-style Shorts: a full-screen vertical feed, swipe up for the next one. */
@Composable
fun ShortsScreen(seed: String? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val actions = LocalAppActions.current
    val scope = rememberCoroutineScope()
    val session = container.shorts
    val feedState by session.state.collectAsState()
    val shorts = remember(feedState.items, seed) {
        if (seed == null) feedState.items else listOf(VideoItem(url = seed, title = "")) + feedState.items.filter { it.url != seed }
    }
    val shortsPlayer = remember { ShortsPlayer(context.applicationContext, session, scope) }
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // Shorts take over the sound: the main player pauses while the feed is open, and the
        // feed pauses when the app goes to the background.
        container.player.player.pause()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> shortsPlayer.pause()
                Lifecycle.Event.ON_START -> shortsPlayer.resume()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            shortsPlayer.release()
        }
    }

    LaunchedEffect(Unit) { if (session.state.value.items.isEmpty()) session.loadMore() }

    if (shorts.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (feedState.error == null) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(feedState.error ?: "No Shorts right now", color = Color.White, modifier = Modifier.padding(24.dp))
                    Text(
                        "Try again",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { scope.launch { session.restart() } }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
        return
    }

    val pager = rememberPagerState(initialPage = if (seed == null) session.lastPage.coerceIn(0, shorts.size - 1) else 0) { shorts.size }
    val urls by rememberUpdatedState(shorts.map { it.url })
    LaunchedEffect(pager, shorts.size) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (seed == null) session.lastPage = page
            shortsPlayer.activate(page, urls)
            if (page >= shorts.size - 4) launch { session.loadMore() }
        }
    }
    // While a swipe is under way, start loading the page it's heading to.
    LaunchedEffect(pager) {
        snapshotFlow { pager.targetPage }.distinctUntilChanged().collect { page ->
            urls.getOrNull(page)?.let { shortsPlayer.slot(page).load(it) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { shorts.getOrNull(it)?.url ?: it },
        ) { page ->
            val item = shorts[page]
            ShortPage(
                item = item,
                slot = shortsPlayer.slot(page),
                session = session,
                active = page == pager.settledPage,
                onComments = { commentsFor = it },
                onShare = { actions.share(it.title, it.url) },
                onChannel = { url -> actions.openChannel(url) },
            )
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = actions.openSearch) { Icon(Icons.Filled.Search, "Search", tint = Color.White) }
        }
    }

    commentsFor?.let { v ->
        CommentsSheet(videoUrl = v.url, videoId = videoId(v.url), onDismiss = { commentsFor = null })
    }
}

/**
 * The short's video on a TextureView, which slides with the pager without the lag or black
 * edges a SurfaceView can show mid-swipe. Vertical videos fill the screen, others are letterboxed.
 */
@Composable
private fun ShortVideo(player: ExoPlayer, size: VideoSize, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val ratio = if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 9f / 16f
        val boxRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else ratio
        val crop = ratio < 1f
        val widerThanBox = ratio > boxRatio
        val (w, h) = if (crop == widerThanBox) {
            // Fill the height, crop or letterbox the sides.
            maxHeight * ratio to maxHeight
        } else {
            maxWidth to maxWidth / ratio
        }
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            update = { view ->
                val bound = view.tag as? ExoPlayer
                if (bound !== player) {
                    bound?.clearVideoTextureView(view)
                    player.setVideoTextureView(view)
                    view.tag = player
                }
            },
            onRelease = { view ->
                (view.tag as? ExoPlayer)?.clearVideoTextureView(view)
                view.tag = null
            },
            modifier = Modifier.requiredSize(w, h),
        )
    }
}

private fun videoId(url: String): String =
    Regex("(?:v=|/shorts/|youtu\\.be/)([\\w-]{11})").find(url)?.groupValues?.get(1) ?: url.takeLast(11)

@Composable
private fun ShortPage(
    item: VideoItem,
    slot: ShortsPlayer.Slot,
    session: com.imad.itube.data.ShortsSession,
    active: Boolean,
    onComments: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val player = slot.player
    val details by produceState(initialValue = null as com.imad.itube.data.model.VideoDetails?, item.url) {
        value = runCatching { container.repository.details(session.info(item.url).await()) }.getOrNull()
    }
    val dislikes by produceState<Long?>(null, item.url) { value = container.dislikes.dislikes(videoId(item.url)) }
    val library by container.library.data.collectAsState()
    val liked = library.liked.any { it.url == item.url }
    var paused by remember(item.url) { mutableStateOf(false) }
    var speedBoost by remember(item.url) { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val title = details?.title ?: item.title
    val channelName = details?.channelName ?: item.channelName
    val channelUrl = details?.channelUrl ?: item.channelUrl
    val subscribed = container.library.isSubscribed(channelUrl)
    val mine = slot.url == item.url
    val error = if (active && mine) slot.error else null

    if (active) {
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) paused = false
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(item.url) {
            while (true) {
                val d = player.duration
                progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                delay(100)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(active) {
                detectTapGestures(
                    onPress = { if (active) holdForDoubleSpeed(player, viewConfiguration.longPressTimeoutMillis) { speedBoost = it } },
                    onLongPress = {},
                    onTap = {
                        if (!active) return@detectTapGestures
                        if (player.isPlaying) { player.pause(); paused = true } else { player.play(); paused = false }
                    },
                    onDoubleTap = {
                        if (!liked) container.library.toggleLiked(details?.toVideoItem() ?: item)
                    },
                )
            },
    ) {
        // Each page draws its own player, so the next short is already on screen mid-swipe.
        if (mine) ShortVideo(player, slot.videoSize, Modifier.fillMaxSize())
        // The thumbnail stays on top until the video's first frame is drawn: no black flash.
        if (!(mine && slot.firstFrame)) {
            val thumb = details?.thumbnail ?: item.thumbnail
            AsyncImage(
                model = thumb ?: "https://i.ytimg.com/vi/${videoId(item.url)}/hq720.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (active) {
            CaptionOverlay(player, lifted = true)
            DoubleSpeedPill(speedBoost, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
        }
        // Darken the bottom so the text stays readable.
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        )
        AnimatedVisibility(paused && active, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(44.dp)) }
        }
        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Retry",
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { slot.retry() }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // Right-hand action column.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val likes = details?.likeCount?.takeIf { it >= 0 }
            ShortAction(Icons.Filled.ThumbUp, likes?.let { Format.compact(it + if (liked) 1 else 0) } ?: "Like", highlighted = liked) {
                container.library.toggleLiked(details?.toVideoItem() ?: item)
                container.appScope.launch {
                    runCatching {
                        container.account.rate(videoId(item.url), if (liked) com.imad.itube.account.Rating.None else com.imad.itube.account.Rating.Like)
                    }
                }
            }
            ShortAction(Icons.Filled.ThumbDown, dislikes?.let { Format.compact(it) } ?: "Dislike") {
                container.appScope.launch { runCatching { container.account.rate(videoId(item.url), com.imad.itube.account.Rating.Dislike) } }
            }
            ShortAction(Icons.AutoMirrored.Filled.Comment, "Comments") { onComments(details?.toVideoItem() ?: item) }
            ShortAction(Icons.Filled.Share, "Share") { onShare(details?.toVideoItem() ?: item) }
        }

        // Channel and title, bottom left.
        Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 80.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(details?.channelAvatar ?: item.channelAvatar, 32.dp, name = channelName, channelUrl = channelUrl)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        channelName.orEmpty().let { if (it.startsWith("@")) it else "@" + it.replace(" ", "") },
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (channelUrl != null && channelName != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (subscribed) "Subscribed" else "Subscribe",
                        color = if (subscribed) Color.White else Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (subscribed) Color.White.copy(alpha = 0.2f) else Color.White)
                            .clickable {
                                container.toggleSubscription(
                                    ChannelItem(url = channelUrl, name = channelName, avatar = details?.channelAvatar ?: item.channelAvatar)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (active) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(YtRed))
            }
        }
    }
}

@Composable
private fun ShortAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = if (highlighted) 0.35f else 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
