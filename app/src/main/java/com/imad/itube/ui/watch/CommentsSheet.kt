package com.imad.itube.ui.watch

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.data.model.CommentItem
import com.imad.itube.data.model.CommentsPage
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format
import kotlinx.coroutines.launch

private val LinkBlue = Color(0xFF3EA6FF)

/** One row in the sheet: a top-level comment, a reply, or a "show more replies" link. */
private sealed interface SheetRow {
    data class Comment(val item: CommentItem, val depth: Int, val parentKey: String?) : SheetRow
    data class MoreReplies(val parentKey: String, val cursor: Any) : SheetRow
}

@Composable
fun CommentsSheet(videoUrl: String, videoId: String, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val repo = container.repository
    val accountApi = container.accountComments
    val account by container.accountStore.account.collectAsState()
    val signedIn = account != null
    val scope = rememberCoroutineScope()

    val rows = remember(videoUrl) { mutableStateListOf<SheetRow>() }
    val expanded = remember(videoUrl) { mutableStateMapOf<String, Boolean>() }
    var next by remember(videoUrl) { mutableStateOf<Any?>(null) }
    var loading by remember(videoUrl) { mutableStateOf(true) }
    var message by remember(videoUrl) { mutableStateOf<String?>(null) }
    var total by remember(videoUrl) { mutableStateOf(-1) }
    var postParams by remember(videoUrl) { mutableStateOf<String?>(null) }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // Signed in, comments come through the account so they can be liked and replied to.
    suspend fun loadFirst(): CommentsPage =
        if (signedIn) runCatching { accountApi.comments(videoId) }.getOrElse { repo.comments(videoUrl) }
        else repo.comments(videoUrl)

    suspend fun loadMore(cursor: Any): CommentsPage =
        if (accountApi.isCursor(cursor)) accountApi.more(cursor) else repo.commentsMore(cursor)

    fun keyOf(c: CommentItem) = c.id ?: (c.author + c.text.take(40))

    LaunchedEffect(videoUrl, signedIn) {
        rows.clear()
        loading = true
        message = null
        try {
            val page = loadFirst()
            rows += page.comments.map { SheetRow.Comment(it, 0, null) }
            next = page.next
            total = page.totalCount
            postParams = page.postParams
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
            runCatching { loadMore(cursor) }.onSuccess { p ->
                rows += p.comments.map { SheetRow.Comment(it, 0, null) }
                next = p.next
            }.onFailure { next = null }
            loading = false
        }
    }

    fun insertReplies(parentKey: String, afterIndex: Int, page: CommentsPage) {
        val add = buildList<SheetRow> {
            page.comments.forEach { add(SheetRow.Comment(it, 1, parentKey)) }
            page.next?.let { add(SheetRow.MoreReplies(parentKey, it)) }
        }
        rows.addAll(afterIndex + 1, add)
    }

    fun toggleReplies(c: CommentItem) {
        val key = keyOf(c)
        val index = rows.indexOfFirst { it is SheetRow.Comment && it.depth == 0 && keyOf(it.item) == key }
        if (index < 0) return
        if (expanded[key] == true) {
            expanded[key] = false
            rows.removeAll { (it is SheetRow.Comment && it.parentKey == key) || (it is SheetRow.MoreReplies && it.parentKey == key) }
            return
        }
        val cursor = c.replies ?: return
        expanded[key] = true
        scope.launch {
            runCatching { loadMore(cursor) }
                .onSuccess { insertReplies(key, index, it) }
                .onFailure { expanded[key] = false; actions.toast("Couldn't load replies") }
        }
    }

    fun loadMoreReplies(row: SheetRow.MoreReplies) {
        val index = rows.indexOf(row)
        if (index < 0) return
        rows.removeAt(index)
        scope.launch {
            runCatching { loadMore(row.cursor) }.onSuccess { insertReplies(row.parentKey, index - 1, it) }
        }
    }

    fun rate(index: Int, like: Boolean) {
        val row = rows.getOrNull(index) as? SheetRow.Comment ?: return
        val a = row.item.actions
        if (!signedIn || a == null) {
            actions.toast("Sign in to like and reply to comments")
            return
        }
        val token = when {
            like && a.liked -> a.unlike
            like -> a.like
            a.disliked -> a.undislike
            else -> a.dislike
        } ?: return actions.toast("YouTube doesn't allow rating this comment")
        val newA = when {
            like -> a.copy(liked = !a.liked, disliked = false)
            else -> a.copy(disliked = !a.disliked, liked = false)
        }
        rows[index] = row.copy(item = row.item.copy(actions = newA))
        scope.launch {
            runCatching { accountApi.perform(token) }.onFailure {
                val current = rows.getOrNull(index) as? SheetRow.Comment
                if (current != null) rows[index] = current.copy(item = current.item.copy(actions = a))
                actions.toast("Couldn't rate the comment")
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        val target = replyTo
        val params = if (target != null) target.actions?.replyParams else postParams
        if (params == null) {
            actions.toast("YouTube doesn't allow commenting here")
            return
        }
        sending = true
        scope.launch {
            runCatching {
                if (target != null) accountApi.reply(params, text) else accountApi.post(params, text)
            }.onSuccess {
                val mine = CommentItem(
                    author = account?.handle ?: account?.name ?: "You",
                    authorAvatar = account?.avatar,
                    text = text,
                    likes = null,
                    published = "now",
                    replyCount = 0,
                    pinned = false,
                    hearted = false,
                )
                if (target == null) {
                    rows.add(0, SheetRow.Comment(mine, 0, null))
                    message = null
                } else {
                    val key = keyOf(target)
                    val idx = rows.indexOfFirst { it is SheetRow.Comment && it.depth == 0 && keyOf(it.item) == key }
                    if (idx >= 0) rows.add(idx + 1, SheetRow.Comment(mine, 1, key))
                }
                draft = ""
                replyTo = null
                actions.toast(if (target == null) "Comment posted" else "Reply posted")
            }.onFailure { actions.toast("Couldn't post: ${it.message}") }
            sending = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Yt.colors.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", style = MaterialTheme.typography.titleLarge)
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(Format.compact(total.toLong()), color = Yt.colors.textSecondary)
                }
            }
            HorizontalDivider(color = Yt.colors.divider)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(rows) { index, row ->
                    when (row) {
                        is SheetRow.Comment -> CommentRow(
                            c = row.item,
                            depth = row.depth,
                            repliesOpen = expanded[keyOf(row.item)] == true,
                            onLike = { rate(index, true) },
                            onDislike = { rate(index, false) },
                            onReply = {
                                if (!signedIn) {
                                    actions.toast("Sign in to like and reply to comments")
                                } else {
                                    // Replies to a reply go to its thread, like on YouTube.
                                    val parent = if (row.depth == 0) row.item else rows
                                        .filterIsInstance<SheetRow.Comment>()
                                        .firstOrNull { it.depth == 0 && keyOf(it.item) == row.parentKey }?.item
                                    replyTo = parent
                                    if (row.depth > 0) draft = "@${row.item.author.removePrefix("@")} "
                                }
                            },
                            onToggleReplies = { toggleReplies(row.item) },
                        )
                        is SheetRow.MoreReplies -> Text(
                            "Show more replies",
                            color = LinkBlue,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier
                                .clickable { loadMoreReplies(row) }
                                .padding(start = 68.dp, top = 6.dp, bottom = 10.dp),
                        )
                    }
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                message?.let { m ->
                    item { Text(m, color = Yt.colors.textSecondary, modifier = Modifier.padding(24.dp)) }
                }
            }
            if (signedIn && (postParams != null || replyTo != null)) {
                HorizontalDivider(color = Yt.colors.divider)
                replyTo?.let { r ->
                    Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Replying to ${r.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Yt.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { replyTo = null; draft = "" }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Cancel reply", Modifier.size(16.dp))
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(account?.avatar, 28.dp, name = account?.name)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(if (replyTo != null) "Add a reply…" else "Add a comment…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yt.colors.textPrimary,
                            unfocusedBorderColor = Yt.colors.divider,
                            cursorColor = Yt.colors.textPrimary,
                        ),
                    )
                    IconButton(onClick = ::send, enabled = draft.isNotBlank() && !sending) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = YtRed, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    c: CommentItem,
    depth: Int,
    repliesOpen: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onReply: () -> Unit,
    onToggleReplies: () -> Unit,
) {
    val liked = c.actions?.liked == true
    val disliked = c.actions?.disliked == true
    Row(Modifier.fillMaxWidth().padding(start = if (depth > 0) 56.dp else 16.dp, end = 8.dp, top = 10.dp)) {
        Avatar(c.authorAvatar, if (depth > 0) 24.dp else 28.dp, name = c.author)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PushPin, null, Modifier.size(12.dp), tint = Yt.colors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned", style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                }
            }
            Text(Format.joinDot(c.author, c.published), style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                    Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", Modifier.size(16.dp))
                }
                val likes = c.likes
                if (!likes.isNullOrBlank()) {
                    Text(likes, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDislike, modifier = Modifier.size(36.dp)) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", Modifier.size(16.dp))
                }
                if (c.hearted) {
                    Icon(Icons.Filled.Favorite, "Hearted by creator", Modifier.size(14.dp), tint = YtRed)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "Reply",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.clickable(onClick = onReply).padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            if (depth == 0 && c.replyCount > 0 && c.replies != null) {
                Text(
                    if (repliesOpen) "Hide replies" else "${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = LinkBlue,
                    modifier = Modifier.clickable(onClick = onToggleReplies).padding(vertical = 6.dp),
                )
            }
        }
    }
}
