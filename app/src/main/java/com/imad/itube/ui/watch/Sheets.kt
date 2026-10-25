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
