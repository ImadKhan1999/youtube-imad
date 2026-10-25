package com.imad.itube.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.imad.itube.account.ChatMessage
import com.imad.itube.appContainer
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import kotlinx.coroutines.launch

/** Card under the actions on a live stream. */
@Composable
fun LiveChatTeaser(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Yt.colors.chip)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(YtRed))
        Spacer(Modifier.width(8.dp))
        Text("Live chat", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text("Open", style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
    }
}

@Composable
fun LiveChatSheet(videoId: String, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val chat = container.liveChat
    val messages = remember(videoId) { mutableStateListOf<ChatMessage>() }
    var error by remember(videoId) { mutableStateOf<String?>(null) }
    var canSend by remember(videoId) { mutableStateOf(false) }
    var loading by remember(videoId) { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoId) {
        try {
            chat.messages(videoId).collect { update ->
                loading = false
                canSend = update.canSend
                if (update.messages.isEmpty()) return@collect
                // Stick to the bottom only if the user hasn't scrolled up to read.
                val atBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?.let { it >= messages.size - 2 } ?: true
                messages += update.messages
                if (messages.size > 500) messages.removeRange(0, messages.size - 400)
                if (atBottom) listState.scrollToItem(messages.lastIndex.coerceAtLeast(0))
            }
            error = "The live chat has ended."
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load the live chat"
        }
        loading = false
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        scope.launch {
            runCatching { chat.send(text) }
                .onSuccess { draft = "" }
                .onFailure { actions.toast("Couldn't send: ${it.message}") }
            sending = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Yt.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.62f).navigationBarsPadding().imePadding()) {
            Text("Live chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            HorizontalDivider(color = Yt.colors.divider)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(messages, key = { it.id }) { ChatRow(it) }
                }
                when {
                    loading -> CircularProgressIndicator(
                        color = YtRed,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 3.dp,
                    )
                    error != null && messages.isEmpty() -> Text(
                        error.orEmpty(),
                        color = Yt.colors.textSecondary,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
            HorizontalDivider(color = Yt.colors.divider)
            if (canSend) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 200) draft = it },
                        placeholder = { Text("Chat…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yt.colors.textPrimary,
                            unfocusedBorderColor = Yt.colors.divider,
                            cursorColor = Yt.colors.textPrimary,
                        ),
                    )
                    IconButton(onClick = ::send, enabled = draft.isNotBlank() && !sending) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            } else if (!loading && error == null) {
                Text(
                    if (container.account.isSignedIn) "Chat is read-only for this stream" else "Sign in to chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(m: ChatMessage) {
    val paid = m.amount != null
    val bg = m.amountColor?.let { Color(it.toInt()) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (paid) Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(bg ?: YtRed) else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(m.avatar, 24.dp, name = m.author)
        Spacer(Modifier.width(8.dp))
        val nameColor = when (m.role) {
            "Owner" -> Color(0xFFFFD600)
            "Moderator" -> Color(0xFF5E84F1)
            "Member" -> Color(0xFF2BA640)
            else -> Yt.colors.textSecondary
        }
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (paid) Color.White else nameColor, fontWeight = FontWeight.Medium)) {
                        append(m.author)
                    }
                    if (m.amount != null) {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("  ${m.amount}") }
                    }
                    if (m.text.isNotEmpty()) {
                        append("  ")
                        withStyle(SpanStyle(color = if (paid) Color.White else Yt.colors.textPrimary)) { append(m.text) }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
