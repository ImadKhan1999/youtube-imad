package com.imad.itube.account

import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.ItemPage
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.security.MessageDigest

enum class Rating { Like, Dislike, None }

class NotSignedInException : IOException("Not signed in")

/**
 * Talks to YouTube's own web API (InnerTube) with the signed-in account's cookies, for the
 * things that need an account: who you are, your subscriptions and your recommendations.
 * Playback never goes through here, so a problem with the account can't break videos.
 */
class YouTubeAccount(private val http: OkHttpClient, private val store: AccountStore) {
    private val json = Json { ignoreUnknownKeys = true }

    val isSignedIn: Boolean get() = store.account.value != null

    @Volatile private var clientVersion: String? = null

    private fun clientVersion(): String = clientVersion
        ?: runCatching { YoutubeParsingHelper.getClientVersion() }.getOrDefault(FALLBACK_VERSION).also { clientVersion = it }

    private fun cookie(cookies: String, name: String): String? =
        cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    private fun sapisidHash(cookies: String): String? {
        val sapisid = cookie(cookies, "SAPISID") ?: cookie(cookies, "__Secure-3PAPISID") ?: return null
        val now = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${now}_" + digest.joinToString("") { "%02x".format(it) }
    }

    /** One InnerTube call. With [requireAuth] false it also works signed out, just anonymously. */
    internal suspend fun call(
        endpoint: String,
        requireAuth: Boolean = true,
        extra: JsonObject.() -> JsonObject = { this },
    ): JsonElement =
        withContext(Dispatchers.IO) {
            val account = store.account.value
            if (account == null && requireAuth) throw NotSignedInException()
            val base = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", clientVersion())
                        put("hl", "en")
                        put("gl", java.util.Locale.getDefault().country.ifBlank { "US" })
                    }
                }
            }
            val body = JsonObject(base + base.extra()).toString()
            val request = Request.Builder()
                .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
                .header("Referer", "$ORIGIN/")
                .header("X-Goog-AuthUser", "0")
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", clientVersion())
                .header("User-Agent", DESKTOP_UA)
                .apply {
                    if (account != null) {
                        header("Cookie", account.cookies)
                        sapisidHash(account.cookies)?.let { header("Authorization", it) }
                    }
                }
                .build()
            http.newCall(request).execute().use { res ->
                if (res.code == 401 || res.code == 403) throw IOException("YouTube didn't accept the sign-in (HTTP ${res.code}). Try signing in again.")
                if (!res.isSuccessful) {
                    val reason = runCatching {
                        json.parseToJsonElement(res.body?.string().orEmpty()).str("error", "message")
                    }.getOrNull()
                    throw IOException("YouTube error ${res.code}" + (reason?.let { ": $it" } ?: ""))
                }
                json.parseToJsonElement(res.body?.string().orEmpty())
            }
        }

    /**
     * Fetches a youtube.com page with the account's cookies and returns its ytInitialData, for
     * the few things (like the live chat input box) that only come with the HTML page.
     */
    internal suspend fun initialData(pathAndQuery: String): JsonElement? =
        withContext(Dispatchers.IO) {
            val account = store.account.value
            val request = Request.Builder()
                .url("$ORIGIN$pathAndQuery")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "en")
                .apply { if (account != null) header("Cookie", account.cookies) }
                .build()
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val html = res.body?.string().orEmpty()
                val match = INITIAL_DATA.find(html) ?: return@withContext null
                runCatching { json.parseToJsonElement(match.groupValues[1]) }.getOrNull()
            }
        }

    private suspend fun browse(browseId: String?, continuation: String?): JsonElement = call("browse", true) {
        buildJsonObject {
            if (continuation != null) put("continuation", continuation) else put("browseId", browseId)
        }
    }

    /** Name, handle and photo of the signed-in account. */
    suspend fun profile(): Account {
        val res = call("account/account_menu")
        val header = res.findFirst("activeAccountHeaderRenderer")
        val current = store.account.value ?: throw NotSignedInException()
        return current.copy(
            name = header.text("accountName") ?: current.name,
            handle = header.text("channelHandle") ?: current.handle,
            avatar = header.at("accountPhoto").lastImage() ?: current.avatar,
        )
    }

    /** Likes, dislikes or clears the rating of a video. Does nothing signed out. */
    suspend fun rate(videoId: String, rating: Rating) {
        if (!isSignedIn) return
        val endpoint = when (rating) {
            Rating.Like -> "like/like"
            Rating.Dislike -> "like/dislike"
            Rating.None -> "like/removelike"
        }
        call(endpoint) { buildJsonObject { putJsonObject("target") { put("videoId", videoId) } } }
    }

    /** Only /channel/UC… URLs carry the id YouTube needs; others are skipped. */
    suspend fun setSubscribed(channelUrl: String, subscribed: Boolean) {
        if (!isSignedIn) return
        val id = Regex("/channel/(UC[\\w-]{22})").find(channelUrl)?.groupValues?.get(1) ?: return
        call(if (subscribed) "subscription/subscribe" else "subscription/unsubscribe") {
            buildJsonObject { putJsonArray("channelIds") { add(id) } }
        }
    }

    /** Every channel the account is subscribed to. */
    suspend fun subscriptions(): List<ChannelItem> {
        val out = LinkedHashMap<String, ChannelItem>()
        var res = browse("FEchannels", null)
        repeat(40) {
            res.findAll("channelRenderer").forEach { c ->
                val id = c.str("channelId") ?: return@forEach
                out.getOrPut(id) {
                    ChannelItem(
                        url = "https://www.youtube.com/channel/$id",
                        name = c.text("title").orEmpty(),
                        avatar = c.at("thumbnail").lastImage(),
                    )
                }
            }
            val token = continuationToken(res) ?: return out.values.toList()
            res = browse(null, token)
        }
        return out.values.toList()
    }

    /** The account's personal home feed. */
    suspend fun home(continuation: String? = null): ItemPage<VideoItem> {
        val res = browse(if (continuation == null) "FEwhat_to_watch" else null, continuation)
        return ItemPage(parseVideos(res), continuationToken(res))
    }

    /** Your history or liked videos, as YouTube has them. */
    suspend fun list(browseId: String, continuation: String? = null): ItemPage<VideoItem> {
        val res = browse(if (continuation == null) browseId else null, continuation)
        return ItemPage(parseVideos(res), continuationToken(res))
    }

    private fun continuationToken(res: JsonElement): String? =
        res.findAll("continuationItemRenderer").lastOrNull()
            ?.findFirst("continuationCommand").str("token")

    private fun parseVideos(res: JsonElement): List<VideoItem> {
        val seen = HashSet<String>()
        val out = ArrayList<VideoItem>()
        // Old-style renderers and the newer "lockup" view models, whichever this response uses.
        (res.findAll("videoRenderer") + res.findAll("playlistVideoRenderer")).forEach { v ->
            parseVideoRenderer(v)?.let { if (seen.add(it.url)) out += it }
        }
        res.findAll("lockupViewModel").forEach { v ->
            parseLockup(v)?.let { if (seen.add(it.url)) out += it }
        }
        return out
    }

    private fun parseVideoRenderer(v: JsonObject): VideoItem? {
        val id = v.str("videoId") ?: return null
        val owner = v.at("ownerText") ?: v.at("shortBylineText") ?: v.at("longBylineText")
        val channelId = owner.str("runs", 0, "navigationEndpoint", "browseEndpoint", "browseId")
        val badges = v.at("badges") as? JsonArray
        val live = badges?.any { it.str("metadataBadgeRenderer", "style") == "BADGE_STYLE_TYPE_LIVE_NOW" } == true
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = v.text("title").orEmpty(),
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelName = owner.text(),
            channelUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = v.at("channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer", "thumbnail").lastImage(),
            viewCount = parseCount(v.text("viewCountText")),
            uploadedText = v.text("publishedTimeText"),
            duration = parseDuration(v.text("lengthText")),
            isLive = live,
        )
    }

    private fun parseLockup(v: JsonObject): VideoItem? {
        if (v.str("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = v.str("contentId") ?: return null
        val meta = v.at("metadata", "lockupMetadataViewModel")
        val avatar = meta.at("image", "decoratedAvatarViewModel")
        val rows = (meta.at("metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty()
            .map { row -> (row.at("metadataParts") as? JsonArray).orEmpty().mapNotNull { it.text("text") } }
        val channelId = avatar?.findFirst("browseEndpoint").str("browseId")
        val badge = v.at("contentImage")?.findFirst("thumbnailBadgeViewModel")
        val badgeText = badge.str("text")
        val stats = rows.getOrNull(1).orEmpty()
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = meta.text("title").orEmpty(),
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelName = rows.firstOrNull()?.firstOrNull(),
            channelUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = avatar.at("avatar", "avatarViewModel", "image").lastImage(),
            viewCount = parseCount(stats.firstOrNull { it.contains("view") || it.contains("watching") }),
            uploadedText = stats.firstOrNull { it.contains("ago") || it.startsWith("Streamed") },
            duration = parseDuration(badgeText),
            isLive = badge.str("badgeStyle") == "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE" || badgeText == "LIVE",
        )
    }

    companion object {
        const val ORIGIN = "https://www.youtube.com"
        private val INITIAL_DATA =
            Regex("""(?:window\\["ytInitialData"]|var ytInitialData)\\s*=\\s*(\\{.*?});\\s*</script>""", RegexOption.DOT_MATCHES_ALL)
        private const val FALLBACK_VERSION = "2.20251020.01.00"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        fun parseDuration(text: String?): Long {
            if (text.isNullOrBlank() || !text.contains(':')) return -1
            return text.trim().split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: return -1) }
        }

        fun parseCount(text: String?): Long {
            if (text.isNullOrBlank()) return -1
            if (text.startsWith("No ")) return 0
            val m = Regex("""([\d.,]+)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(text) ?: return -1
            val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return -1
            val mult = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
            return (n * mult).toLong()
        }
    }
}
