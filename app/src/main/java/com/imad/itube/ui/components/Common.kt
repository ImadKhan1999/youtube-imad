package com.imad.itube.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.imad.itube.appContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed

/** The iTube emblem (ImadLabs HexaCore cube with a play core) plus the word iTube. */
@Composable
fun ITubeLogo(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.imad.itube.R.drawable.ic_itube_emblem),
            contentDescription = null,
            modifier = Modifier.size(height * 1.25f),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "iTube",
            fontSize = (height.value * 1.05f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            color = Yt.colors.textPrimary,
        )
    }
}

/**
 * A round channel avatar. Shows the channel's initial while loading or when YouTube gave us no
 * picture; when [channelUrl] is set and [url] is missing, the avatar is looked up on demand.
 */
@Composable
fun Avatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    name: String? = null,
    channelUrl: String? = null,
) {
    val cache = LocalContext.current.appContainer.avatars
    val resolved by produceState(initialValue = url ?: channelUrl?.let(cache::cached), url, channelUrl) {
        if (value == null && channelUrl != null) value = cache.get(channelUrl)
    }
    var failed by remember(resolved) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (initial != null) {
            Text(initial.toString(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
        }
        if (resolved != null && !failed) {
            AsyncImage(
                model = resolved,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFF8D6E63),
)

@Composable
private fun avatarColor(name: String?): Color =
    if (name.isNullOrBlank()) Yt.colors.chip else AVATAR_COLORS[(name.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = "Verified",
        tint = Yt.colors.textSecondary,
        modifier = modifier.size(12.dp),
    )
}

/** Horizontally scrolling filter chips, YouTube-style (selected chip is inverted). */
@Composable
fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        labels.forEachIndexed { i, label ->
            YtChip(label, i == selected) { onSelect(i) }
        }
    }
}

@Composable
fun YtChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Yt.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.chipSelected else c.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.chipSelectedText else c.textPrimary,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = YtRed, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = Yt.colors.textSecondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = Yt.colors.textSecondary)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            PillButton("Retry", onClick = onRetry)
        }
    }
}

@Composable
fun EmptyBox(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Yt.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** The rounded "Subscribe"-style button. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Yt.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inverted) c.subscribeBg else c.chip,
            contentColor = if (inverted) c.subscribeText else c.textPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Draws nothing; handy as a list spacer that keeps content above the mini player. */
@Composable
fun BottomSpacer(height: Dp = 16.dp) = Spacer(Modifier.height(height))
