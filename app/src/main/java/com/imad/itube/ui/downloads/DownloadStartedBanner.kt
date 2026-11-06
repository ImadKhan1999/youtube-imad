package com.imad.itube.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imad.itube.appContainer
import com.imad.itube.download.DownloadItem
import com.imad.itube.download.DownloadStatus
import com.imad.itube.ui.brand.ImadLabs
import kotlinx.coroutines.delay

/**
 * The "Download started" banner: slides down from the top as soon as a download is queued, with a
 * bouncing download arrow, the video, and live progress, so it's obvious something is happening.
 * Tap it (or View) to open Downloads. It tucks itself away after a few seconds, or once the file is done.
 */
@Composable
fun DownloadStartedBanner(onOpenDownloads: () -> Unit) {
    val downloads = LocalContext.current.appContainer.downloads
    val items by downloads.items.collectAsState()
    var watched by remember { mutableStateOf<List<String>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        downloads.started.collect { fresh ->
            if (fresh.isEmpty()) return@collect
            watched = fresh.map { it.id }
            shownAt = System.currentTimeMillis()
            visible = true
        }
    }

    val tracked = watched.mapNotNull { id -> items.firstOrNull { it.id == id } }
    val lead = tracked.firstOrNull { it.status == DownloadStatus.Running } ?: tracked.firstOrNull()
    val finished = tracked.isNotEmpty() && tracked.all { it.status == DownloadStatus.Done }
    val failed = lead?.status == DownloadStatus.Failed

    // Stay up ~5 s, then get out of the way; a finished or failed download gets a moment to show it.
    LaunchedEffect(shownAt, finished, failed) {
        if (!visible) return@LaunchedEffect
        delay(if (finished || failed) 2500 else 5000)
        visible = false
    }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && lead != null,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(tween(260)),
        ) {
            lead ?: return@AnimatedVisibility
            BannerCard(lead, tracked.size, finished, failed) {
                visible = false
                onOpenDownloads()
            }
        }
    }
}

@Composable
private fun BannerCard(item: DownloadItem, count: Int, finished: Boolean, failed: Boolean, onOpen: () -> Unit) {
    val progress by animateFloatAsState(item.progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111827))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(finished, failed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        failed -> "Download failed"
                        finished -> if (count > 1) "$count downloads finished" else "Downloaded"
                        item.status == DownloadStatus.Running -> "Downloading… ${Math.round(item.progress * 100)}%"
                        count > 1 -> "$count downloads started"
                        else -> "Download started"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    listOfNotNull(item.qualityLabel, item.video.title).joinToString(" · "),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = item.video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
            )
            Spacer(Modifier.width(10.dp))
            Text("View", color = ImadLabs.CyberMint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        val barColor = if (failed) Color(0xFFFF4E45) else ImadLabs.CyberMint
        if (!finished && !failed && (item.status == DownloadStatus.Queued || item.progress <= 0f)) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = barColor, trackColor = Color(0xFF1E293B))
        } else {
            LinearProgressIndicator(
                progress = { if (finished) 1f else progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = barColor,
                trackColor = Color(0xFF1E293B),
                drawStopIndicator = {},
            )
        }
    }
}

/** A mint circle with the download arrow bouncing in it; turns into a tick when done. */
@Composable
private fun StatusBadge(finished: Boolean, failed: Boolean) {
    val bounce = rememberInfiniteTransition(label = "bounce")
    val dy by bounce.animateFloat(
        initialValue = -3f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(ImadLabs.CyberMint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFFF4E45), modifier = Modifier.size(24.dp))
            finished -> androidx.compose.animation.AnimatedVisibility(visible = true, enter = scaleIn(spring(dampingRatio = 0.4f))) {
                Icon(Icons.Filled.CheckCircle, null, tint = ImadLabs.CyberMint, modifier = Modifier.size(26.dp))
            }
            else -> Icon(
                Icons.Filled.Download,
                null,
                tint = ImadLabs.CyberMint,
                modifier = Modifier.size(24.dp).graphicsLayer { translationY = dy * density },
            )
        }
    }
}
