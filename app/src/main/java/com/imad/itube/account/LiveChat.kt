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

data class ChatUpdate(val messages: List<ChatMessage>, val canSend: Boolean)

/** YouTube live chat, polled the same way the website does it. Works signed out (read-only). */
class LiveChat(private val api: YouTubeAccount) {

    @Volatile private var sendParams: String? = null

    /** Emits batches of new messages until the stream ends or the collector is cancelled. */
    fun messages(videoId: String): Flow<ChatUpdate> = flow {
        val watch = api.call("next", requireAuth = false) { buildJsonObject { put("videoId", videoId) } }
        val chat = watch.findFirst("liveChatRenderer") ?: throw IOException("Live chat isn't available for this stream")
        sendParams = chat.findFirst("sendLiveChatMessageEndpoint").str("params")
        // The first continuation is "Top chat"; the second, when present, is "Live chat" (everything).
        val menu = chat.at("header", "liveChatHeaderRenderer")?.findAll("continuation")?.mapNotNull { it.str("reloadContinuationData", "continuation") }
        var token = menu?.getOrNull(1)
            ?: chat.findFirst("reloadContinuationData").str("continuation")
            ?: throw IOException("Live chat isn't available for this stream")
        val seen = LinkedHashSet<String>()
        var failures = 0
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
            val batch = (cont.at("actions") as? JsonArray).orEmpty()
                .mapNotNull { a -> a.at("addChatItemAction", "item")?.let(::parse) }
                .filter { seen.add(it.id) }
            if (seen.size > 2000) repeat(seen.size - 1500) { seen.remove(seen.first()) }
            emit(ChatUpdate(batch, sendParams != null && api.isSignedIn))
            val next = (cont.at("continuations") as? JsonArray)?.firstOrNull() ?: break
            val data = next.at("invalidationContinuationData") ?: next.at("timedContinuationData") ?: next.at("reloadContinuationData") ?: break
            token = data.str("continuation") ?: break
            val wait = data.str("timeoutMs")?.toLongOrNull() ?: 4000
            delay(wait.coerceIn(1000, 10_000))
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
