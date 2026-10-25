package com.imad.itube.account

import com.imad.itube.data.model.CommentActions
import com.imad.itube.data.model.CommentItem
import com.imad.itube.data.model.CommentsPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

/**
 * Comments loaded with the signed-in account, so each one carries the tokens YouTube needs
 * to like, dislike or reply to it. Only used when signed in; otherwise NewPipe loads them.
 */
class AccountComments(private val api: YouTubeAccount) {

    private class Cursor(val token: String)

    suspend fun comments(videoId: String): CommentsPage {
        val watch = api.call("next") { buildJsonObject { put("videoId", videoId) } }
        val section = watch.findAll("itemSectionRenderer")
            .firstOrNull { it.str("sectionIdentifier") == "comment-item-section" }
            ?: return CommentsPage(emptyList(), null, 0, disabled = true)
        val token = section.findFirst("continuationCommand").str("token")
            ?: return CommentsPage(emptyList(), null, 0, disabled = true)
        return page(token, first = true)
    }

    /** Next page of comments, or a page of replies. */
    suspend fun more(cursor: Any): CommentsPage = page((cursor as Cursor).token, first = false)

    fun isCursor(cursor: Any?): Boolean = cursor is Cursor

    private suspend fun page(token: String, first: Boolean): CommentsPage {
        val res = api.call("next") { buildJsonObject { put("continuation", token) } }
        val mutations = (res.at("frameworkUpdates", "entityBatchUpdate", "mutations") as? JsonArray).orEmpty()
        val byKey = HashMap<String, JsonObject>()
        mutations.forEach { m ->
            val key = m.str("entityKey") ?: return@forEach
            (m.at("payload") as? JsonObject)?.let { byKey[key] = it }
        }
        val items = (res.at("onResponseReceivedEndpoints") as? JsonArray).orEmpty().flatMap { ep ->
            ((ep.at("reloadContinuationItemsCommand", "continuationItems")
                ?: ep.at("appendContinuationItemsAction", "continuationItems")) as? JsonArray).orEmpty()
        }
        val header = items.firstNotNullOfOrNull { it.at("commentsHeaderRenderer") }
        val comments = items.mapNotNull { item ->
            val thread = item.at("commentThreadRenderer")
            val vm = thread?.at("commentViewModel", "commentViewModel") ?: item.at("commentViewModel") ?: return@mapNotNull null
            val repliesToken = thread?.at("replies")?.findFirst("continuationCommand").str("token")
            parse(vm, byKey, repliesToken)
        }
        val nextToken = items.lastOrNull()?.at("continuationItemRenderer")?.findFirst("continuationCommand").str("token")
        return CommentsPage(
            comments = comments,
            next = nextToken?.let(::Cursor),
            totalCount = if (first) YouTubeAccount.parseCount(header.text("countText")).toInt() else -1,
            disabled = false,
            postParams = header?.findFirst("createCommentEndpoint").str("createCommentParams"),
        )
    }

    private fun parse(vm: JsonElement, byKey: Map<String, JsonObject>, repliesToken: String?): CommentItem? {
        val entity = byKey[vm.str("commentKey")]?.at("commentEntityPayload") ?: return null
        val state = byKey[vm.str("toolbarStateKey")]?.at("engagementToolbarStateEntityPayload")
        val surface = byKey[vm.str("toolbarSurfaceKey")]?.at("engagementToolbarSurfaceEntityPayload")
        fun action(name: String) = surface?.at(name)?.findFirst("performCommentActionEndpoint").str("action")
        val likeState = state.str("likeState").orEmpty()
        val replyCount = entity.str("toolbar", "replyCount")
        return CommentItem(
            author = entity.str("author", "displayName").orEmpty(),
            authorAvatar = entity.str("author", "avatarThumbnailUrl") ?: entity.at("avatar", "image").lastImage(),
            text = entity.str("properties", "content", "content").orEmpty(),
            likes = entity.str("toolbar", "likeCountNotliked")?.trim()?.takeIf { it.isNotEmpty() && it != "0" },
            published = entity.str("properties", "publishedTime"),
            replyCount = YouTubeAccount.parseCount(replyCount).toInt().coerceAtLeast(0),
            pinned = vm.at("pinnedText") != null,
            hearted = state.str("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            replies = repliesToken?.let(::Cursor),
            id = entity.str("properties", "commentId"),
            actions = CommentActions(
                like = action("likeCommand"),
                unlike = action("unlikeCommand"),
                dislike = action("dislikeCommand"),
                undislike = action("undislikeCommand"),
                replyParams = surface?.at("replyCommand")?.findFirst("createCommentReplyEndpoint").str("createReplyParams"),
                liked = likeState.endsWith("_LIKED"),
                disliked = likeState.endsWith("_DISLIKED"),
            ),
        )
    }

    suspend fun perform(action: String) {
        val res = api.call("comment/perform_comment_action") {
            buildJsonObject { putJsonArray("actions") { add(action) } }
        }
        if (res.str("actionResults", 0, "status") == "STATUS_FAILED") throw IOException("YouTube rejected that")
    }

    suspend fun reply(params: String, text: String) {
        api.call("comment/create_comment_reply") {
            buildJsonObject {
                put("createReplyParams", params)
                put("commentText", text)
            }
        }
    }

    suspend fun post(params: String, text: String) {
        api.call("comment/create_comment") {
            buildJsonObject {
                put("createCommentParams", params)
                put("commentText", text)
            }
        }
    }
}
