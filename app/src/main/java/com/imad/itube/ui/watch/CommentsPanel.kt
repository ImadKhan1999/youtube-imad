package com.imad.itube.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/** Comments as a bottom sheet, for Shorts (where YouTube also uses a sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(videoUrl: String, videoId: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Yt.colors.surface, dragHandle = null) {
        CommentsPanel(
            videoUrl,
            videoId,
            onClose = onDismiss,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().imePadding(),
        )
    }
}

/**
 * YouTube's comments panel. It opens under the video in portrait (the video keeps playing in
 * view) and in the side column on tablets.
 */
@Composable
fun CommentsPanel(videoUrl: String, videoId: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
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

    Column(modifier.background(Yt.colors.surface)) {
        Column(Modifier.fillMaxSize()) {
            PanelHeader("Comments", onClose, trailing = total.takeIf { it > 0 }?.let { Format.compact(it.toLong()) })
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
                        is SheetRow.MoreReplies -> Row(
                            Modifier
                                .padding(start = 60.dp, top = 2.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { loadMoreReplies(row) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.SubdirectoryArrowRight, null, Modifier.size(16.dp), tint = LinkBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Show more replies",
                                color = LinkBlue,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            )
                        }
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
                    Avatar(account?.avatar, 32.dp, name = account?.name)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Yt.colors.chip)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Yt.colors.textPrimary),
                        cursorBrush = SolidColor(Yt.colors.textPrimary),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(
                                        if (replyTo != null) "Add a reply…" else "Add a comment…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Yt.colors.textSecondary,
                                    )
                                }
                                inner()
                            }
                        },
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
    var textExpanded by remember(c.id, c.text) { mutableStateOf(false) }
    var overflows by remember(c.id, c.text) { mutableStateOf(false) }
    val secondary = Yt.colors.textSecondary
    Row(Modifier.fillMaxWidth().padding(start = if (depth > 0) 60.dp else 12.dp, end = 4.dp, top = 12.dp)) {
        Avatar(c.authorAvatar, if (depth > 0) 24.dp else 32.dp, name = c.author)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                    Icon(Icons.Outlined.PushPin, null, Modifier.size(12.dp), tint = secondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned", fontSize = 12.sp, color = secondary)
                }
            }
            // "@handle · 2 years ago", with the creator's name in a grey pill like YouTube.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameMod = if (c.isCreator) {
                    Modifier.clip(RoundedCornerShape(50)).background(Yt.colors.chip).padding(horizontal = 6.dp, vertical = 1.dp)
                } else {
                    Modifier
                }
                Row(nameMod, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.author,
                        fontSize = 12.sp,
                        color = if (c.isCreator) Yt.colors.textPrimary else secondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.verified) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.CheckCircle, "Verified", Modifier.size(11.dp), tint = secondary)
                    }
                }
                c.published?.takeIf { it.isNotBlank() }?.let {
                    Text(" · $it", fontSize = 12.sp, color = secondary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                c.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = if (textExpanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!textExpanded) overflows = it.hasVisualOverflow },
            )
            if (overflows || textExpanded) {
                Text(
                    if (textExpanded) "Show less" else "Read more",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondary,
                    modifier = Modifier.clickable { textExpanded = !textExpanded }.padding(top = 4.dp),
                )
            }
            // Like · count · dislike · reply, then the creator's heart, as on YouTube.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                IconButton(onClick = onLike, modifier = Modifier.size(34.dp).padding(end = 0.dp)) {
                    Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", Modifier.size(16.dp))
                }
                val likes = c.likes
                if (!likes.isNullOrBlank()) {
                    Text(likes, fontSize = 12.sp, color = secondary)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = onDislike, modifier = Modifier.size(34.dp)) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", Modifier.size(16.dp))
                }
                IconButton(onClick = onReply, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "Reply", Modifier.size(16.dp))
                }
                if (c.hearted) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(20.dp)) {
                        Icon(Icons.Filled.AccountCircle, null, Modifier.size(18.dp), tint = secondary)
                        Icon(
                            Icons.Filled.Favorite,
                            "Hearted by creator",
                            Modifier.size(11.dp).align(Alignment.BottomEnd),
                            tint = YtRed,
                        )
                    }
                }
            }
            if (depth == 0 && c.replyCount > 0 && c.replies != null) {
                Row(
                    Modifier
                        .offset(x = (-12).dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onToggleReplies)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (repliesOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        null,
                        Modifier.size(18.dp),
                        tint = LinkBlue,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (repliesOpen) "Hide replies" else "${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = LinkBlue,
                    )
                }
            }
        }
    }
}
