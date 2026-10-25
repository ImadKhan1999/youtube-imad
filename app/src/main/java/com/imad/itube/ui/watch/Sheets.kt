package com.imad.itube.ui.watch

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.imad.itube.appContainer
import com.imad.itube.data.model.CommentItem
import com.imad.itube.data.model.VideoDetails
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format

@Composable
fun DescriptionSheet(details: VideoDetails, onDismiss: () -> Unit) {
    val colors = Yt.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Description", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(12.dp))
            Text(details.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(if (details.likeCount >= 0) Format.compact(details.likeCount) else "–", "Likes")
                Stat(if (details.viewCount >= 0) Format.compact(details.viewCount) else "–", if (details.isLive) "Watching" else "Views")
                Stat(Format.ago(details.uploadedAt, details.uploadedText).ifBlank { "–" }, "Uploaded")
            }
            Spacer(Modifier.height(16.dp))
            val textColor = colors.textPrimary.toArgb()
            val linkColor = android.graphics.Color.rgb(62, 166, 255)
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        textSize = 14f
                        setLineSpacing(0f, 1.15f)
                    }
                },
                update = { tv ->
                    tv.setTextColor(textColor)
                    tv.setLinkTextColor(linkColor)
                    tv.text = if (details.descriptionIsHtml) {
                        HtmlCompat.fromHtml(details.description, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    } else {
                        details.description
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
    }
}

@Composable
fun CommentsSheet(videoUrl: String, onDismiss: () -> Unit) {
    val repo = LocalContext.current.appContainer.repository
    var comments by remember(videoUrl) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var next by remember(videoUrl) { mutableStateOf<Any?>(null) }
    var loading by remember(videoUrl) { mutableStateOf(true) }
    var message by remember(videoUrl) { mutableStateOf<String?>(null) }
    var total by remember(videoUrl) { mutableStateOf(-1) }

    LaunchedEffect(videoUrl) {
        try {
            val page = repo.comments(videoUrl)
            comments = page.comments
            next = page.next
            total = page.totalCount
            if (page.disabled) message = "Comments are turned off."
            else if (page.comments.isEmpty()) message = "No comments yet."
        } catch (e: Exception) {
            message = "Couldn't load comments."
        }
        loading = false
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, next) {
        val cursor = next
        if (nearEnd && cursor != null && !loading) {
            loading = true
            runCatching { repo.commentsMore(cursor) }.onSuccess {
                comments = comments + it.comments
                next = it.next
            }.onFailure { next = null }
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Yt.colors.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", style = MaterialTheme.typography.titleLarge)
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(Format.compact(total.toLong()), color = Yt.colors.textSecondary)
                }
            }
            HorizontalDivider(color = Yt.colors.divider)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(comments) { CommentRow(it) }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                message?.let { m ->
                    item {
                        Text(m, color = Yt.colors.textSecondary, modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: CommentItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(c.authorAvatar, 28.dp, name = c.author)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PushPin, null, Modifier.size(12.dp), tint = Yt.colors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned", style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                }
            }
            Text(
                Format.joinDot(c.author, c.published),
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, Modifier.size(14.dp), tint = Yt.colors.textSecondary)
                if (!c.likes.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(c.likes, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Filled.Favorite, "Hearted by creator", Modifier.size(14.dp), tint = YtRed)
                }
                if (c.replyCount > 0) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color3EA6FF,
                    )
                }
            }
        }
    }
}

private val Color3EA6FF = androidx.compose.ui.graphics.Color(0xFF3EA6FF)
