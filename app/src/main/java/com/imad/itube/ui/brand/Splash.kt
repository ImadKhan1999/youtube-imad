package com.imad.itube.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Sky = Color(0xFF38BDF8)
private val Cobalt = Color(0xFF0066FF)
private val Quantum = Color(0xFF00FFC2)
private val MintDeep = Color(0xFF00D285)

/**
 * The opening animation, like the YouTube app's: the iTube mark pops in, the play button and the
 * mint bar draw in, the quantum dot pulses, then the name appears and everything fades into the app.
 * Drawn from the iTube icon in the ImadLabs Graphics repo (apps/yt), in its 1024-unit space.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val play = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
            delay(180)
            launch { play.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            delay(160)
            launch { bar.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch {
                dot.animateTo(1.35f, tween(220))
                dot.animateTo(1f, spring(dampingRatio = 0.4f))
            }
            delay(240)
            async { name.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }.await()
            delay(420)
            fade.animateTo(0f, tween(320))
        }
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(ImadLabs.DeepVoid),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val s = 0.6f + 0.4f * pop.value
                        scaleX = s
                        scaleY = s
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
            ) {
                // Map the icon's 1024 space onto the canvas, centred on the artwork (x 232..844, y 206..816).
                val k = size.minDimension / 640f
                translate(left = size.width / 2 - 538f * k, top = size.height / 2 - 511f * k) {
                    scale(k, pivot = Offset.Zero) {
                        drawRoundRect(
                            Brush.linearGradient(listOf(Sky, Cobalt), start = Offset(300f, 250f), end = Offset(740f, 760f)),
                            topLeft = Offset(232f, 330f),
                            size = Size(560f, 380f),
                            cornerRadius = CornerRadius(110f),
                        )
                        // Play button grows out from its centre.
                        val p = play.value
                        if (p > 0f) {
                            val tri = Path().apply {
                                moveTo(465f, 430f); lineTo(465f, 610f); lineTo(615f, 520f); close()
                            }
                            scale(p, pivot = Offset(515f, 520f)) {
                                drawPath(tri, Color.White, style = Fill)
                                drawPath(tri, Color.White, style = Stroke(width = 36f, join = StrokeJoin.Round))
                            }
                        }
                        // Mint bar draws in from the left.
                        val b = bar.value
                        if (b > 0f) {
                            val w = 280f * b
                            drawRoundRect(Quantum.copy(alpha = 0.2f * b), Offset(360f, 758f), Size(w + 24f, 58f), CornerRadius(29f))
                            drawRoundRect(
                                Brush.linearGradient(listOf(Quantum, MintDeep), start = Offset(280f, 250f), end = Offset(760f, 820f)),
                                topLeft = Offset(372f, 770f),
                                size = Size(w.coerceAtLeast(34f), 34f),
                                cornerRadius = CornerRadius(17f),
                            )
                        }
                        // Quantum dot pulses in.
                        val d = dot.value
                        if (d > 0f) {
                            drawCircle(Quantum.copy(alpha = 0.22f), radius = 64f * d, center = Offset(780f, 270f))
                            drawCircle(Quantum, radius = 46f * d, center = Offset(780f, 270f))
                            drawCircle(Color.White, radius = 20f * d, center = Offset(780f, 270f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.graphicsLayer {
                    alpha = name.value
                    translationY = (1f - name.value) * 24.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("iTube", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF94A3B8))) { append("by Imad") }
                        withStyle(SpanStyle(color = ImadLabs.CyberMint)) { append("Labs") }
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
