package com.imad.itube.ui.watch

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Where the ambient glow reads video frames from: the player's SurfaceView, set by [PlayerSurface]. */
class AmbientFrames {
    var surface: SurfaceView? = null
}

/** Blur is native from Android 12; before that a tinier frame, smoothly upscaled, gives the same soft look. */
private val nativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * YouTube's ambient mode: a tiny copy of the current video frame, stretched and blurred behind the
 * player, cross-fading to the next sample about once a second and fading out towards the bottom.
 */
@Composable
fun AmbientGlow(frames: AmbientFrames, playing: Boolean, videoKey: Any?, modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val isPlaying by rememberUpdatedState(playing)

    LaunchedEffect(frames, videoKey) {
        frame = null
        while (true) {
            frames.surface?.let { sv -> sample(sv)?.let { frame = it } }
            // Keep sampling when paused too (seeks change the frame), just less often.
            delay(if (isPlaying) 1000 else 2500)
        }
    }

    Crossfade(
        targetState = frame,
        animationSpec = tween(durationMillis = 900),
        label = "ambient",
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = 0.6f
            }
            .drawWithContent {
                drawContent()
                // Fade the glow out towards the bottom so it melts into the page.
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.45f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize().then(if (nativeBlur) Modifier.blur(48.dp) else Modifier),
            )
        }
    }
}

private val mainHandler = Handler(Looper.getMainLooper())

/** Copies the current video frame, scaled down to a few pixels, or null if the surface has none yet. */
private suspend fun sample(sv: SurfaceView): ImageBitmap? {
    if (!sv.isAttachedToWindow || sv.width == 0 || sv.height == 0 || !sv.holder.surface.isValid) return null
    val (w, h) = if (nativeBlur) 32 to 18 else 12 to 7
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val ok = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(sv, bmp, { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) }, mainHandler)
        } catch (_: IllegalArgumentException) {
            cont.resume(false) // The surface went away between the check and the copy.
        }
    }
    if (!ok) {
        bmp.recycle()
        return null
    }
    return bmp.asImageBitmap()
}
