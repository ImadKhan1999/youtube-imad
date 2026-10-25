package com.imad.itube.ui.watch

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format
import kotlinx.coroutines.delay

/** Snapshot of the player that Compose can observe. */
@Stable
class PlayerUiState {
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var playbackState by mutableIntStateOf(Player.STATE_IDLE)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    val buffering: Boolean get() = playbackState == Player.STATE_BUFFERING
    val ended: Boolean get() = playbackState == Player.STATE_ENDED
}

@Composable
fun rememberPlayerUiState(player: Player): PlayerUiState {
    val state = remember(player) { PlayerUiState() }
    DisposableEffect(player) {
        fun sync() {
            state.isPlaying = player.isPlaying
            state.playWhenReady = player.playWhenReady
            state.playbackState = player.playbackState
            state.duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            state.position = player.currentPosition
            state.buffered = player.bufferedPosition
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        sync()
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            state.position = player.currentPosition
            state.buffered = player.bufferedPosition
            state.duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(250)
        }
    }
    return state
}

/** The video surface. Only one is composed at a time (full player, mini player or PiP). */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier, keepScreenOn: Boolean = false) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.keepScreenOn = keepScreenOn
        },
        onRelease = { it.player = null },
    )
}

/**
 * YouTube-style controls drawn over the video: tap to show/hide, double-tap a side to seek,
 * big centre play/pause, time + fullscreen at the bottom, red scrubber.
 */
@Composable
fun PlayerControls(
    player: Player,
    ui: PlayerUiState,
    title: String,
    isLive: Boolean,
    fullscreen: Boolean,
    seekSeconds: Int,
    hasNext: Boolean,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSettings: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // (forward, seconds)
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    // Auto-hide after 3 s of playing without interaction.
    LaunchedEffect(visible, ui.isPlaying, interactionTick, scrubbing) {
        if (visible && ui.isPlaying && scrubbing == null) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(seekSeconds) {
                detectTapGestures(
                    onTap = { visible = !visible; interactionTick++ },
                    onDoubleTap = { offset ->
                        val forward = offset.x > size.width / 2
                        val delta = seekSeconds * 1000L
                        val target = if (forward) player.currentPosition + delta else player.currentPosition - delta
                        player.seekTo(target.coerceIn(0L, (player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)))
                        val prev = seekFeedback
                        seekFeedback = forward to (if (prev != null && prev.first == forward) prev.second + seekSeconds else seekSeconds)
                    },
                )
            },
    ) {
        // Double-tap feedback, shown on the tapped half.
        seekFeedback?.let { (forward, secs) ->
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth / 2)
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                        null,
                        tint = Color.White,
                    )
                    Text("$secs seconds", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (ui.buffering && !visible) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp).align(Alignment.Center),
            )
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                // Top row
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = if (fullscreen) onToggleFullscreen else onCollapse) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = Color.White)
                    }
                    if (fullscreen) {
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = { onSettings(); interactionTick++ }) {
                        Icon(Icons.Outlined.Settings, "Settings", tint = Color.White)
                    }
                }

                // Centre: previous / play-pause / next
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(Icons.Filled.SkipPrevious, "Restart", 40.dp, enabled = !isLive) {
                        player.seekTo(0); interactionTick++
                    }
                    if (ui.buffering) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                        }
                    } else {
                        val icon = when {
                            ui.ended -> Icons.Filled.Replay
                            ui.playWhenReady -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        }
                        RoundControl(icon, "Play/Pause", 64.dp) {
                            when {
                                ui.ended -> { player.seekTo(0); player.play() }
                                player.playWhenReady -> player.pause()
                                else -> player.play()
                            }
                            interactionTick++
                        }
                    }
                    RoundControl(Icons.Filled.SkipNext, "Next", 40.dp, enabled = hasNext) {
                        onNext(); interactionTick++
                    }
                }

                // Bottom: time + fullscreen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 4.dp, bottom = if (fullscreen) 28.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLive) {
                        Box(
                            Modifier.clip(RoundedCornerShape(2.dp)).background(YtRed).padding(horizontal = 4.dp, vertical = 1.dp),
                        ) { Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        val shown = scrubbing?.let { (it * ui.duration).toLong() } ?: ui.position
                        Text(
                            "${Format.position(shown)} / ${Format.position(ui.duration)}",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            "Full screen",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (!isLive) {
            SeekBar(
                position = ui.position,
                buffered = ui.buffered,
                duration = ui.duration,
                showThumb = visible,
                onScrub = { scrubbing = it; visible = true },
                onSeek = { fraction ->
                    scrubbing = null
                    player.seekTo((fraction * ui.duration).toLong())
                    interactionTick++
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (fullscreen) 16.dp else 0.dp, start = if (fullscreen) 12.dp else 0.dp, end = if (fullscreen) 12.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(enabled) { if (enabled) detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** Thin red progress bar that grows a thumb while the controls are visible. */
@Composable
fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    showThumb: Boolean,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val playedFraction = dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current

    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(duration) {
                widthPx = size.width.toFloat().coerceAtLeast(1f)
                detectTapGestures(onTap = { o -> if (duration > 0) onSeek((o.x / widthPx).coerceIn(0f, 1f)) })
            }
            .pointerInput(duration) {
                widthPx = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragFraction = (o.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragFraction!!)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragFraction!!)
                    },
                )
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        val barHeight = if (showThumb || dragFraction != null) 3.dp else 2.dp
        Box(Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(bufferedFraction).height(barHeight).background(Color.White.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(playedFraction).height(barHeight).background(YtRed))
        if (showThumb || dragFraction != null) {
            val thumb = if (dragFraction != null) 16.dp else 12.dp
            val x = with(density) { (playedFraction * widthPx).toDp() } - thumb / 2
            Box(
                Modifier
                    .offset(x = x, y = (thumb - barHeight) / 2)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(YtRed),
            )
        }
    }
}
