package com.imad.itube.account

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import java.io.IOException

data class ChatMessage(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    /** "Owner", "Moderator" or "Member", when the author has one of those badges. */
    val role: String? = null,
    /** Super Chat amount, e.g. "$5.00". */
    val amount: String? = null,
    val amountColor: Long? = null,
)

/** [notice] is YouTube's own reason when you can't chat, e.g. "Subscribers-only mode". */
data class ChatUpdate(val messages: List<ChatMessage>, val canSend: Boolean, val notice: String? = null)

/** YouTube live chat, polled the same way the website does it. Works signed out (read-only). */
class LiveChat(private val api: YouTubeAccount) {

    @Volatile private var sendParams: String? = null

    /** Emits batches of new messages until the stream ends or the collector is cancelled. */
    fun messages(videoId: String): Flow<ChatUpdate> = flow {
        val watch = api.call("next", requireAuth = false) { buildJsonObject { put("videoId", videoId) } }
        val chat = watch.findFirst("liveChatRenderer") ?: throw IOException("Live chat isn't available for this stream")
        sendParams = chat.findFirst("sendLiveChatMessageEndpoint").str("params")
        // The chat's own continuation lives in liveChatRenderer.continuations; the header's
        // Top chat / Live chat menu tokens are sort switches and get HTTP 400 on their own.
        var token = (chat.at("continuations") as? JsonArray)?.firstNotNullOfOrNull {
            it.str("reloadContinuationData", "continuation")
        } ?: throw IOException("Live chat isn't available for this stream")
        val seen = LinkedHashSet<String>()
        var failures = 0
        var first = true
        var notice: String? = null
        while (true) {
            val res = try {
                api.call("live_chat/get_live_chat", requireAuth = false) { buildJsonObject { put("continuation", token) } }
            } catch (e: IOException) {
                if (++failures >= 5) throw e
                null
            }
            if (res == null) {
                delay(3000)
                continue
            }
            failures = 0
            val cont = res.at("continuationContents", "liveChatContinuation") ?: break
            // The chat box (and with it the send params) comes in the chat's own responses, the
            // first one in particular; when it doesn't, YouTube says why in a restriction notice.
            cont.findFirst("sendLiveChatMessageEndpoint").str("params")?.let { sendParams = it }
            cont.findFirst("liveChatRestrictedParticipationRenderer")?.text("message")?.let { notice = it }
            if (first && sendParams == null && api.isSignedIn) {
                val page = runCatching { api.initialData("/live_chat?is_popout=1&v=$videoId") }.getOrNull()
                page?.findFirst("sendLiveChatMessageEndpoint").str("params")?.let { sendParams = it }
                if (sendParams == null) {
                    page?.findFirst("liveChatRestrictedParticipationRenderer")?.text("message")?.let { notice = it }
                }
            }
            val batch = (cont.at("actions") as? JsonArray).orEmpty()
                .mapNotNull { a -> a.at("addChatItemAction", "item")?.let(::parse) }
                .filter { seen.add(it.id) }
            if (seen.size > 2000) repeat(seen.size - 1500) { seen.remove(seen.first()) }
            val canSend = sendParams != null && api.isSignedIn
            val next = (cont.at("continuations") as? JsonArray)?.firstOrNull()
            val data = next?.let { it.at("invalidationContinuationData") ?: it.at("timedContinuationData") ?: it.at("reloadContinuationData") }
            val wait = (data.str("timeoutMs")?.toLongOrNull() ?: 4000).coerceIn(1000, 6000)
            if (first || batch.size <= 1) {
                // The first batch is the backlog: show it at once.
                emit(ChatUpdate(batch, canSend, notice))
                if (data != null) delay(wait)
            } else {
                // Spread the batch over the wait so messages trickle in like they do on YouTube,
                // instead of arriving in a lump every few seconds.
                val step = wait / batch.size
                batch.forEach { m ->
                    emit(ChatUpdate(listOf(m), canSend, notice))
                    delay(step)
                }
            }
            first = false
            token = data.str("continuation") ?: break
        }
    }

    private fun parse(item: JsonElement): ChatMessage? {
        val text = item.at("liveChatTextMessageRenderer")
        val paid = item.at("liveChatPaidMessageRenderer")
        val member = item.at("liveChatMembershipItemRenderer")
        val r = text ?: paid ?: member ?: return null
        val id = r.str("id") ?: return null
        val badges = (r.at("authorBadges") as? JsonArray).orEmpty().mapNotNull { b ->
            b.str("liveChatAuthorBadgeRenderer", "icon", "iconType")
                ?: if (b.at("liveChatAuthorBadgeRenderer", "customThumbnail") != null) "MEMBER" else null
        }
        val role = when {
            "OWNER" in badges -> "Owner"
            "MODERATOR" in badges -> "Moderator"
            "MEMBER" in badges -> "Member"
            else -> null
        }
        val message = (r.at("message", "runs") as? JsonArray).orEmpty().joinToString("") { run ->
            run.str("text") ?: run.at("emoji")?.let { e ->
                e.str("emojiId")?.takeIf { it.length <= 4 } ?: (e.at("shortcuts") as? JsonArray)?.firstOrNull().str() ?: ""
            } ?: ""
        }.ifBlank { if (member != null) r.text("headerSubtext").orEmpty() else "" }
        return ChatMessage(
            id = id,
            author = r.text("authorName").orEmpty(),
            avatar = r.at("authorPhoto").lastImage(),
            text = message,
            role = role,
            amount = paid?.text("purchaseAmountText"),
            amountColor = paid?.str("bodyBackgroundColor")?.toLongOrNull(),
        )
    }

    suspend fun send(text: String) {
        val params = sendParams ?: throw IOException("You can't chat on this stream")
        api.call("live_chat/send_message") {
            buildJsonObject {
                put("params", params)
                put("clientMessageId", "itube" + System.nanoTime())
                putJsonObject("richMessage") {
                    putJsonArray("textSegments") { addJsonObject { put("text", text) } }
                }
            }
        }
    }
}
