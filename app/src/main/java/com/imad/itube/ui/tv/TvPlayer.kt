package com.imad.itube.ui.tv

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.imad.itube.appContainer
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.VideoItem
import com.imad.itube.data.model.at
import com.imad.itube.player.PlayerController
import com.imad.itube.player.WatchState
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.ui.watch.CaptionOverlay
import com.imad.itube.ui.watch.PlayerSurface
import com.imad.itube.ui.watch.SPEEDS
import com.imad.itube.ui.watch.rememberPlayerUiState
import com.imad.itube.ui.watch.speedLabel
import com.imad.itube.util.Format
import kotlinx.coroutines.delay

/** Pending seek while the remote's left / right is held; applied once the presses stop. */
@Stable
private class ScrubState {
    var target by mutableStateOf<Long?>(null)
    var tick by mutableLongStateOf(0L)

    fun step(player: Player, forward: Boolean, repeat: Int, seekSeconds: Int) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (!player.isCurrentMediaItemSeekable) return
        // Holding the button speeds up, like YouTube TV: 10 s steps, then 30 s, then a minute.
        val step = when {
            repeat > 24 -> 60_000L
            repeat > 8 -> 30_000L
            else -> seekSeconds.coerceAtLeast(5) * 1000L
        }
        val base = target ?: player.currentPosition
        target = (if (forward) base + step else base - step).coerceIn(0L, duration)
        tick = SystemClock.uptimeMillis()
    }
}

private enum class TvPanel { Main, Quality, LiveQuality, Speed, Captions, Audio }

/**
 * The full-screen player of the TV layout, driven entirely by the remote.
 *
 * Controls hidden: OK plays / pauses, left / right seek, up / down show the controls.
 * Controls shown: the arrows move between the seek bar and the buttons, down again opens the
 * "Up next" row. Back closes whatever is open; with nothing open it stops the video.
 * Media keys (play, pause, fast-forward, rewind, next, previous) work at any time.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvPlayerScreen(onExit: () -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val controller = container.player
    val player = controller.player
    val state by controller.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val library by container.library.data.collectAsState()
    val ui = rememberPlayerUiState(player)

    var controls by remember { mutableStateOf(false) }
    var related by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<TvPanel?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scrub = remember { ScrubState() }

    val root = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }
    val seekBar = remember { FocusRequester() }
    val relatedFirst = remember { FocusRequester() }
    var focusTarget by remember { mutableStateOf<FocusRequester?>(null) }

    val details = state.details
    val title = details?.title ?: state.item?.title.orEmpty()
    val chapters = details?.chapters.orEmpty()
    val relatedVideos = details?.related.orEmpty().filterIsInstance<VideoItem>()

    fun showControls(focus: FocusRequester) {
        controls = true
        focusTarget = focus
        tick++
    }

    fun hideAll() {
        controls = false
        related = false
        focusTarget = root
    }

    fun togglePlay() {
        when {
            ui.ended -> { player.seekTo(0); player.play() }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    // Move focus once the element it's meant for is on screen.
    LaunchedEffect(focusTarget, controls, related) {
        val f = focusTarget ?: return@LaunchedEffect
        delay(30)
        runCatching { f.requestFocus() }
        focusTarget = null
    }
    LaunchedEffect(Unit) { runCatching { root.requestFocus() } }

    // A new video starts with the controls out of the way.
    LaunchedEffect(state.item?.url) {
        panel = null
        hideAll()
    }

    // Hide after 5 s without a key press while playing, like YouTube TV.
    LaunchedEffect(controls, tick, ui.isPlaying, panel, related, scrub.target) {
        if (controls && ui.isPlaying && panel == null && scrub.target == null) {
            delay(if (related) 10_000 else 5_000)
            hideAll()
        }
    }

    // Apply a scrub shortly after the last left / right press.
    LaunchedEffect(scrub.tick) {
        val target = scrub.target ?: return@LaunchedEffect
        delay(650)
        player.seekTo(target)
        scrub.target = null
    }

    BackHandler {
        when {
            panel != null -> { panel = null; showControls(playButton) }
            related -> { related = false; showControls(playButton) }
            controls -> hideAll()
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Focus never wanders out of the player into the screens behind it.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                tick++
                val repeat = e.nativeKeyEvent.repeatCount
                // Media keys work everywhere.
                when (e.key) {
                    Key.MediaPlayPause -> { if (repeat == 0) togglePlay(); if (!controls) showControls(playButton); return@onPreviewKeyEvent true }
                    Key.MediaPlay -> { player.play(); return@onPreviewKeyEvent true }
                    Key.MediaPause -> { player.pause(); showControls(playButton); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { scrub.step(player, true, repeat, settings.seekSeconds); if (!controls) showControls(seekBar); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { scrub.step(player, false, repeat, settings.seekSeconds); if (!controls) showControls(seekBar); return@onPreviewKeyEvent true }
                    Key.MediaNext -> { if (repeat == 0) controller.playNext(); return@onPreviewKeyEvent true }
                    Key.MediaPrevious -> { if (repeat == 0) controller.playPrevious(); return@onPreviewKeyEvent true }
                    Key.MediaStop -> { onExit(); return@onPreviewKeyEvent true }
                    else -> {}
                }
                if (panel != null || controls) return@onPreviewKeyEvent false
                // Controls hidden: the remote drives playback directly.
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (repeat == 0) {
                            togglePlay()
                            showControls(playButton)
                        }
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (state.isLive && !player.isCurrentMediaItemSeekable) {
                            showControls(playButton)
                        } else {
                            scrub.step(player, e.key == Key.DirectionRight, repeat, settings.seekSeconds)
                            showControls(seekBar)
                        }
                        true
                    }
                    Key.DirectionUp -> { showControls(playButton); true }
                    Key.DirectionDown -> {
                        if (relatedVideos.isNotEmpty()) {
                            controls = true
                            related = true
                            focusTarget = relatedFirst
                        } else {
                            showControls(playButton)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(
            player,
            Modifier.fillMaxSize(),
            keepScreenOn = ui.isPlaying,
            cover = details?.thumbnail ?: state.item?.thumbnail,
        )
        CaptionOverlay(player, lifted = controls)

        // Holds focus while the controls are hidden, so the keys above reach the player.
        Box(Modifier.fillMaxSize().focusRequester(root).focusable())

        if (ui.buffering && !controls) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(56.dp).align(Alignment.Center))
        }

        if (state.error != null && details != null) {
            TvPlayerError(state.error.orEmpty(), onRetry = controller::retry, onBack = onExit)
            return@Box
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Dark gradients top and bottom keep the text readable over any video.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))),
                )

                // Title and channel, top left.
                Row(Modifier.padding(start = 48.dp, top = 32.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (details != null) {
                        Avatar(details.channelAvatar, 48.dp, name = details.channelName, channelUrl = details.channelUrl)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column {
                        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = Format.joinDot(
                            details?.channelName ?: state.item?.channelName,
                            details?.let { if (it.isLive) Format.watching(it.viewCount) else Format.views(it.viewCount) },
                            details?.let { if (it.isLive) null else Format.ago(it.uploadedAt, it.uploadedText) },
                        )
                        if (sub.isNotEmpty()) Text(sub, color = Color(0xFFDDDDDD), fontSize = 15.sp, maxLines = 1)
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(bottom = if (related) 16.dp else 36.dp),
                ) {
                    val shown = scrub.target ?: ui.position
                    chapters.takeIf { it.isNotEmpty() && !state.isLive }?.at(shown)?.let { chapter ->
                        Text(
                            chapter.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 6.dp),
                        )
                    }
                    TvSeekBar(
                        player = player,
                        isLive = state.isLive,
                        position = shown,
                        buffered = ui.buffered,
                        duration = ui.duration,
                        scrubbing = scrub.target != null,
                        chapterStarts = chapters.map { it.startMs },
                        focusRequester = seekBar,
                        onStep = { forward, repeat -> scrub.step(player, forward, repeat, settings.seekSeconds) },
                        onOk = { scrub.target?.let { player.seekTo(it); scrub.target = null } ?: togglePlay() },
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                    Spacer(Modifier.height(18.dp))

                    // Buttons: playback on the left, video options on the right.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && relatedVideos.isNotEmpty() && !related) {
                                    related = true
                                    focusTarget = relatedFirst
                                    true
                                } else {
                                    false
                                }
                            },
                        verticalAlignment = Alignment.Top,
                    ) {
                        TvControlButton(Icons.Filled.SkipPrevious, "Previous", enabled = !state.isLive) { controller.playPrevious() }
                        Spacer(Modifier.width(12.dp))
                        val playIcon = when {
                            ui.ended -> Icons.Filled.Replay
                            ui.playWhenReady -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        }
                        TvControlButton(playIcon, if (ui.playWhenReady) "Pause" else "Play", size = 60.dp, modifier = Modifier.focusRequester(playButton)) { togglePlay() }
                        Spacer(Modifier.width(12.dp))
                        TvControlButton(Icons.Filled.SkipNext, "Next", enabled = controller.hasNext) { controller.playNext() }

                        Spacer(Modifier.weight(1f))

                        TvControlButton(
                            if (state.selectedCaption != null) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                            if (state.selectedCaption != null) "Captions on" else "Captions",
                            enabled = state.captions.isNotEmpty(),
                        ) {
                            if (state.captions.isEmpty()) {
                                actions.toast("This video has no captions")
                            } else if (state.selectedCaption != null) {
                                controller.selectCaption(null)
                            } else {
                                val preferred = settings.captionsLanguage
                                val pick = state.captions.firstOrNull { it.languageTag == preferred }
                                    ?: state.captions.firstOrNull { it.languageTag.startsWith(java.util.Locale.getDefault().language) }
                                    ?: state.captions.first()
                                controller.selectCaption(pick)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        TvControlButton(Icons.Outlined.SlowMotionVideo, "Speed ${speedLabel(state.speed)}") { panel = TvPanel.Speed }
                        Spacer(Modifier.width(12.dp))
                        TvControlButton(Icons.Outlined.Settings, "Quality ${qualityLabel(state, controller)}") { panel = TvPanel.Main }
                        if (details != null) {
                            val video = details.toVideoItem()
                            val liked = library.liked.any { it.url == details.url }
                            val saved = library.watchLater.any { it.url == details.url }
                            Spacer(Modifier.width(12.dp))
                            TvControlButton(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (liked) "Liked" else "Like") {
                                container.library.toggleLiked(video)
                            }
                            Spacer(Modifier.width(12.dp))
                            TvControlButton(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (saved) "Saved" else "Watch later") {
                                container.library.toggleWatchLater(video)
                            }
                            Spacer(Modifier.width(12.dp))
                            val subscribed = container.library.isSubscribed(details.channelUrl)
                            TvSubscribeButton(subscribed) {
                                container.library.toggleSubscription(
                                    ChannelItem(
                                        url = details.channelUrl,
                                        name = details.channelName,
                                        avatar = details.channelAvatar,
                                        subscriberCount = details.channelSubscribers,
                                        verified = details.channelVerified,
                                    ),
                                )
                            }
                        }
                    }

                    // "Up next": peeks as a label, opens with down.
                    if (relatedVideos.isNotEmpty()) {
                        Text(
                            if (state.queue.isNotEmpty()) state.queueTitle ?: "Up next" else "Up next",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 10.dp),
                        )
                    }
                    AnimatedVisibility(related, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val upNext = if (state.queue.isNotEmpty()) state.queue.drop(state.queueIndex + 1) + relatedVideos else relatedVideos
                        LazyRow(
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                                    related = false
                                    focusTarget = playButton
                                    true
                                } else {
                                    false
                                }
                            },
                        ) {
                            itemsIndexed(upNext.distinctBy { it.url }.take(30), key = { _, v -> v.url }) { i, v ->
                                TvVideoCard(v, Modifier.width(260.dp).then(if (i == 0) Modifier.focusRequester(relatedFirst) else Modifier))
                            }
                        }
                    }
                }
            }
        }

        // Options panel on the right, like YouTube TV's settings.
        AnimatedVisibility(
            panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val page = panel ?: TvPanel.Main
            TvOptionsPanel(page, state, controller, onPage = { panel = it }, onClose = { panel = null; showControls(playButton) })
        }
    }
}

private fun qualityLabel(state: WatchState, controller: PlayerController): String = when {
    state.isLive -> state.liveHeight?.let { "${it}p" } ?: ("Auto" + (controller.liveCurrentHeight()?.let { " (${it}p)" } ?: ""))
    state.selectedQuality == null -> "Auto" + (state.autoQuality?.let { " (${it.label})" } ?: "")
    else -> state.selectedQuality.label
}

/** A round player button: white when focused, with its name shown underneath. */
@Composable
private fun TvControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 44.dp)) {
        Box(
            modifier
                .size(size)
                .tvClickable(shape = CircleShape, focusedScale = 1.12f, border = 0.dp, onFocusChange = { focused = it }) { if (enabled) onClick() }
                .clip(CircleShape)
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                label,
                tint = when {
                    focused -> Color(0xFF0F0F0F)
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(size * 0.55f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (focused) label else "",
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(18.dp),
        )
    }
}

@Composable
private fun TvSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(top = 6.dp)
            .height(40.dp)
            .tvClickable(shape = RoundedCornerShape(50), focusedScale = 1.08f, border = 0.dp, onFocusChange = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    subscribed -> Color.White.copy(alpha = 0.14f)
                    else -> Color(0xFFF1F1F1).copy(alpha = 0.9f)
                },
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (focused || !subscribed) Color(0xFF0F0F0F) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The red progress bar. Focus it and use left / right to seek; OK applies the seek or pauses. */
@Composable
private fun TvSeekBar(
    player: Player,
    isLive: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    scrubbing: Boolean,
    chapterStarts: List<Long>,
    focusRequester: FocusRequester,
    onStep: (forward: Boolean, repeat: Int) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val seekable = duration > 0 && (!isLive || player.isCurrentMediaItemSeekable)
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (seekable) onStep(e.key == Key.DirectionRight, e.nativeKeyEvent.repeatCount)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (e.nativeKeyEvent.repeatCount == 0) onOk()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val played = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val loaded = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val bar = if (focused) 6.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth(loaded).height(bar).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.5f)))
            Box(Modifier.fillMaxWidth(if (isLive && !seekable) 1f else played).height(bar).clip(RoundedCornerShape(3.dp)).background(YtRed))
            if (duration > 0) {
                chapterStarts.forEach { start ->
                    if (start <= 0 || start >= duration) return@forEach
                    Box(Modifier.offset(x = maxWidth * (start.toFloat() / duration) - 1.dp).width(3.dp).height(bar).background(Color.Black))
                }
            }
            if (seekable && (focused || scrubbing)) {
                val thumb = if (scrubbing) 22.dp else 18.dp
                Box(
                    Modifier
                        .offset(x = maxWidth * played - thumb / 2)
                        .size(thumb)
                        .clip(CircleShape)
                        .background(if (focused) Color.White else YtRed),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isLive) {
                val behind = runCatching { player.currentLiveOffset }.getOrDefault(C.TIME_UNSET)
                val atEdge = behind == C.TIME_UNSET || behind < 20_000 || !seekable
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (atEdge) YtRed else Color.White.copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(Format.position(position), color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(Format.position(duration), color = Color(0xFFCCCCCC), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TvPlayerError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(message) { delay(50); runCatching { retry.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
            Text(message, color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvPill("Retry", modifier = Modifier.focusRequester(retry), onClick = onRetry)
                TvPill("Back", onClick = onBack)
            }
        }
    }
}

/** Quality, speed, captions and audio, as a list sliding in from the right. */
@Composable
private fun TvOptionsPanel(
    page: TvPanel,
    state: WatchState,
    controller: PlayerController,
    onPage: (TvPanel) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(page) { delay(60); runCatching { first.requestFocus() } }
    BackHandler {
        if (page == TvPanel.Main || page == TvPanel.Speed) onClose() else onPage(TvPanel.Main)
    }
    data class Opt(val title: String, val value: String?, val selected: Boolean, val onClick: () -> Unit)

    val rows: List<Opt> = when (page) {
        TvPanel.Main -> buildList {
            if (!state.offline) add(Opt("Quality", qualityLabel(state, controller), false) { onPage(if (state.isLive) TvPanel.LiveQuality else TvPanel.Quality) })
            add(Opt("Playback speed", speedLabel(state.speed), false) { onPage(TvPanel.Speed) })
            add(
                Opt("Captions", if (state.captions.isEmpty()) "Not available" else state.selectedCaption?.label ?: "Off", false) {
                    if (state.captions.isNotEmpty()) onPage(TvPanel.Captions)
                },
            )
            if (state.audioTracks.size > 1) {
                add(Opt("Audio track", (state.selectedAudioTrack ?: state.audioTracks.first()).label, false) { onPage(TvPanel.Audio) })
            }
            add(Opt("Loop video", if (state.loop) "On" else "Off", false) { controller.setLoop(!state.loop) })
        }
        TvPanel.Quality -> buildList {
            add(Opt("Auto" + (state.autoQuality?.let { " (${it.label})" } ?: ""), null, state.selectedQuality == null) { controller.selectQuality(null); onClose() })
            state.qualities.forEach { q ->
                add(Opt(q.label + if (q.height >= 720) "  HD" else "", null, state.selectedQuality?.label == q.label) { controller.selectQuality(q); onClose() })
            }
        }
        TvPanel.LiveQuality -> buildList {
            add(Opt("Auto", null, state.liveHeight == null) { controller.selectLiveQuality(null); onClose() })
            controller.liveQualities().forEach { h ->
                add(Opt("${h}p", null, state.liveHeight == h) { controller.selectLiveQuality(h); onClose() })
            }
        }
        TvPanel.Speed -> SPEEDS.map { s -> Opt(speedLabel(s), null, state.speed == s) { controller.setSpeed(s); onClose() } }
        TvPanel.Captions -> buildList {
            add(Opt("Off", null, state.selectedCaption == null) { controller.selectCaption(null); onClose() })
            state.captions.forEach { c ->
                add(Opt(c.label, null, state.selectedCaption?.languageTag == c.languageTag && state.selectedCaption?.autoGenerated == c.autoGenerated) { controller.selectCaption(c); onClose() })
            }
        }
        TvPanel.Audio -> state.audioTracks.map { a ->
            Opt(a.label, null, (state.selectedAudioTrack ?: state.audioTracks.firstOrNull())?.id == a.id) { controller.selectAudioTrack(a); onClose() }
        }
    }
    val heading = when (page) {
        TvPanel.Main -> "Settings"
        TvPanel.Quality, TvPanel.LiveQuality -> "Quality"
        TvPanel.Speed -> "Playback speed"
        TvPanel.Captions -> "Captions"
        TvPanel.Audio -> "Audio track"
    }
    val startIndex = rows.indexOfFirst { it.selected }.coerceAtLeast(0)

    Column(
        Modifier
            .width(400.dp)
            .fillMaxHeight()
            .background(Color(0xF2212121))
            .padding(vertical = 32.dp),
    ) {
        Text(heading, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, bottom = 16.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(rows) { i, r ->
                var focused by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == startIndex) Modifier.focusRequester(first) else Modifier)
                        .tvClickable(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f, border = 0.dp, onFocusChange = { focused = it }, onClick = r.onClick)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) Color.White else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (focused) Color(0xFF0F0F0F) else Color.White
                    Box(Modifier.width(32.dp)) {
                        if (r.selected) Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(22.dp))
                    }
                    Text(r.title, color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.value != null) {
                        Text(r.value, color = if (focused) Color(0xFF404040) else Color(0xFFAAAAAA), fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
