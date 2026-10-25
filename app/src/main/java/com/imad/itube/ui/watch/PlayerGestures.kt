package com.imad.itube.ui.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Whether the full-screen video is zoomed to fill the screen (pinch out) or fitted (pinch in). */
object PlayerZoom {
    var zoomed by mutableStateOf(false)
}

/** What a gesture is changing right now, for the on-screen feedback. */
sealed interface GestureFeedback {
    data class Brightness(val level: Float) : GestureFeedback
    data class Volume(val level: Float) : GestureFeedback
    data class Zoom(val zoomed: Boolean) : GestureFeedback
}

class PlayerGestureState {
    var feedback by mutableStateOf<GestureFeedback?>(null)
    /** Bumped on every change, so the feedback hides a moment after the last one. */
    var tick by mutableStateOf(0)
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen gestures like the YouTube and MX-style players: drag up/down on the left half for
 * brightness, on the right half for volume, and pinch to zoom the video to fill the screen.
 * The top and bottom strips are left alone so the buttons and the seek bar still work.
 */
@Composable
fun Modifier.playerGestures(enabled: Boolean, state: PlayerGestureState): Modifier {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Brightness is only overridden for this window while full screen, like YouTube.
    DisposableEffect(enabled, activity) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    LaunchedEffect(state.tick) {
        if (state.feedback != null) {
            delay(900)
            state.feedback = null
        }
    }
    if (!enabled) return this

    fun currentBrightness(): Float {
        val own = activity?.window?.attributes?.screenBrightness ?: -1f
        if (own >= 0f) return own
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val edge = size.height * 0.15f
            if (down.position.y < edge || down.position.y > size.height - edge) return@awaitEachGesture
            val leftHalf = down.position.x < size.width / 2
            val slop = 24.dp.toPx()
            var vertical = false
            var pinching = false
            var drag = Offset.Zero
            var pinch = 1f
            var brightness = currentBrightness()
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            var volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    pinching = true
                    pinch *= event.calculateZoom()
                    val want = when {
                        pinch > 1.12f -> true
                        pinch < 0.89f -> false
                        else -> null
                    }
                    if (want != null && want != PlayerZoom.zoomed) {
                        PlayerZoom.zoomed = want
                        state.feedback = GestureFeedback.Zoom(want)
                        state.tick++
                    }
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (pinching) continue
                val change = pressed.first()
                val delta = change.positionChange()
                drag += delta
                if (!vertical && abs(drag.y) > slop && abs(drag.y) > abs(drag.x) * 1.5f) vertical = true
                if (!vertical) continue
                change.consume()
                val step = -delta.y / (size.height * 0.75f)
                if (leftHalf) {
                    brightness = (brightness + step).coerceIn(0.01f, 1f)
                    activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
                    state.feedback = GestureFeedback.Brightness(brightness)
                } else {
                    volume = (volume + step).coerceIn(0f, 1f)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volume * maxVolume), 0)
                    state.feedback = GestureFeedback.Volume(volume)
                }
                state.tick++
            }
        }
    }
}

/** The pill shown while a gesture changes brightness, volume or zoom. */
@Composable
fun GestureFeedbackPill(feedback: GestureFeedback?, modifier: Modifier = Modifier) {
    feedback ?: return
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (feedback) {
            is GestureFeedback.Zoom -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (feedback.zoomed) Icons.Filled.Fullscreen else Icons.Filled.FullscreenExit, null, tint = Color.White)
                Text(if (feedback.zoomed) "Zoomed to fill" else "Original", color = Color.White, fontSize = 13.sp)
            }
            is GestureFeedback.Brightness -> LevelBar(Icons.Filled.BrightnessMedium, feedback.level)
            is GestureFeedback.Volume -> LevelBar(Icons.AutoMirrored.Filled.VolumeUp, feedback.level)
        }
    }
}

@Composable
private fun LevelBar(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(Color.White))
        }
        Spacer(Modifier.height(6.dp))
        Text("${Math.round(level * 100)}%", color = Color.White, fontSize = 12.sp)
    }
}
