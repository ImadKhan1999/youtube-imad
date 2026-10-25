package com.imad.itube.ui.watch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
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
 * lifted above the progress bar while the controls are showing. White text with a soft dark
 * outline, no box behind it. The cues' own positions and colours are ignored on purpose, since
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
    val bottom by animateDpAsState(if (lifted) 52.dp else 10.dp, label = "captionLift")
    if (lines.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        // ~13sp in the portrait player, ~20sp full screen on a phone, capped for TVs.
        val size = (maxHeight.value * 0.05f).coerceIn(11f, 24f)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            lines.forEach { line ->
                Text(
                    line,
                    color = Color.White,
                    fontSize = size.sp,
                    lineHeight = (size * 1.3f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    // No box: a soft dark outline keeps white text readable on bright video.
                    style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1.5f), blurRadius = 6f)),
                )
            }
        }
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
        .takeLast(3)
