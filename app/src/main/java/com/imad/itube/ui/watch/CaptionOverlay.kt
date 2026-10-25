package com.imad.itube.ui.watch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/**
 * Captions drawn the way the YouTube app does: centred near the bottom, sized to the player and
 * lifted above the progress bar while the controls are showing. Netflix-style white text with
 * a thin outline and soft shadow, no box behind it. The cues' own positions and colours are ignored on purpose, since
 * YouTube's auto-generated tracks park them on the left edge.
 */
@Composable
fun CaptionOverlay(player: Player, lifted: Boolean, modifier: Modifier = Modifier) {
    var lines by remember(player) { mutableStateOf(captionLines(player.currentCues.cues)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                lines = captionLines(cueGroup.cues)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (lines.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Netflix-style: fairly large (6% of the player height, ~12-15sp in
        // the portrait player, ~22sp full screen), low in the frame, lifted over the controls.
        val size = (maxHeight.value * 0.06f).coerceIn(12f, 30f)
        val bottom by animateDpAsState(if (lifted) 56.dp else maxHeight * 0.06f, label = "captionLift")
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEach { line -> CaptionLine(line, size) }
        }
    }
}

/** White semi-bold text with a thin black outline and a soft drop shadow, no box. */
@Composable
private fun CaptionLine(text: String, size: Float) {
    val base = TextStyle(
        fontSize = size.sp,
        lineHeight = (size * 1.25f).sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Box(contentAlignment = Alignment.Center) {
        Text(
            text,
            color = Color.Black.copy(alpha = 0.85f),
            style = base.copy(drawStyle = Stroke(width = size * 0.18f, join = StrokeJoin.Round)),
        )
        Text(
            text,
            color = Color.White,
            style = base.copy(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), blurRadius = 8f)),
        )
    }
}

/** Plain text lines of the current cues, top to bottom, without repeats (roll-up captions). */
private fun captionLines(cues: List<Cue>): List<String> =
    cues.asSequence()
        .filter { it.text != null }
        .sortedBy { if (it.line == Cue.DIMEN_UNSET) Float.MAX_VALUE else it.line }
        .flatMap { it.text.toString().split('\n').asSequence() }
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
        .takeLast(2)
