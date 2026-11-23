package com.imad.itube.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** True while the TV (remote control) layout is in use. */
val LocalTvLayout = staticCompositionLocalOf { false }

/**
 * A clearly visible focus highlight for every clickable on TV: a light wash and a white outline,
 * like the YouTube TV app, instead of the barely visible phone ripple.
 */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x7f0c05

    private class Node(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private var focused = false
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                var focus = 0
                var press = 0
                source.interactions.collect { i ->
                    when (i) {
                        is FocusInteraction.Focus -> focus++
                        is FocusInteraction.Unfocus -> focus--
                        is PressInteraction.Press -> press++
                        is PressInteraction.Release -> press--
                        is PressInteraction.Cancel -> press--
                    }
                    focused = focus > 0
                    pressed = press > 0
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val radius = CornerRadius(8.dp.toPx())
            if (focused) {
                drawRoundRect(Color.White.copy(alpha = if (pressed) 0.28f else 0.16f), cornerRadius = radius)
                val w = 2.dp.toPx()
                drawRoundRect(
                    Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = radius,
                    style = Stroke(w),
                )
            } else if (pressed) {
                drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = radius)
            }
        }
    }
}

/** Provides the TV focus look to everything inside, including screens shared with the phone. */
@Composable
fun TvFocusTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTvLayout provides true,
        LocalIndication provides TvFocusIndication,
        // Icon buttons, switches and radio buttons draw their own ripple; make its focus state loud.
        LocalRippleConfiguration provides RippleConfiguration(
            color = Color.White,
            rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.45f, hoveredAlpha = 0.08f, pressedAlpha = 0.3f),
        ),
        content = content,
    )
}

/**
 * A focusable, clickable element that grows a little and gets a white outline while focused,
 * the way YouTube TV marks the selected tile. [onFocusChange] reports focus to restyle content.
 */
@Composable
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.06f,
    border: Dp = 3.dp,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(150), label = "tvScale")
    val source = remember { MutableInteractionSource() }
    return this
        .onFocusChanged {
            if (it.isFocused != focused) {
                focused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (focused) Modifier.border(border, Color.White, shape) else Modifier)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
