package com.imad.itube.ui.watch

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imad.itube.data.model.Chapter
import com.imad.itube.ui.components.HiResImage
import com.imad.itube.ui.theme.Yt
import com.imad.itube.util.Format

/** Header shared by the watch page's side/below panels: title, optional count, close. */
@Composable
fun PanelHeader(title: String, onClose: () -> Unit, trailing: String? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, color = Yt.colors.textSecondary, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }
        HorizontalDivider(color = Yt.colors.divider)
    }
}

/**
 * The chapter list, like YouTube's: shown beside the shrunken video in full screen and below
 * the video in portrait. The playing chapter is highlighted; tap one to jump there.
 */
@Composable
fun ChaptersPanel(
    chapters: List<Chapter>,
    fallbackThumbnail: String?,
    positionMs: () -> Long,
    onPick: (Chapter) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val current by remember(chapters) {
        derivedStateOf { chapters.indexOfLast { it.startMs <= positionMs() }.coerceAtLeast(0) }
    }
    LaunchedEffect(chapters) { list.scrollToItem((current - 1).coerceAtLeast(0)) }
    Column(modifier.background(Yt.colors.surface)) {
        PanelHeader("Chapters", onClose)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(chapters) { i, chapter ->
                val playing = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (playing) Yt.colors.textPrimary.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onPick(chapter) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                        HiResImage(chapter.thumbnail ?: fallbackThumbnail, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Format.position(chapter.startMs),
                            fontSize = 12.sp,
                            color = if (playing) Color(0xFF3EA6FF) else Yt.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item { Spacer(Modifier.size(12.dp)) }
        }
    }
}
