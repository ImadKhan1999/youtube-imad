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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imad.itube.data.model.Chapter
import com.imad.itube.ui.theme.Yt
import com.imad.itube.util.Format

/** The chapter list, like YouTube's: the playing chapter is highlighted, tap one to jump there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    chapters: List<Chapter>,
    fallbackThumbnail: String?,
    positionMs: Long,
    onPick: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { list.scrollToItem((current - 1).coerceAtLeast(0)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Yt.colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(state = list, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(chapters) { i, chapter ->
                val playing = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (playing) Yt.colors.textPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onPick(chapter) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                        AsyncImage(
                            model = chapter.thumbnail ?: fallbackThumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
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
