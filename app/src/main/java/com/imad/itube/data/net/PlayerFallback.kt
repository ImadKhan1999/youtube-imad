package com.imad.itube.data.net

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Backup InnerTube clients for the video player request.
 *
 * NewPipeExtractor asks for a video's streams with a single client (VISIONOS). On many VPN and
 * data-centre IPs YouTube answers that request with LOGIN_REQUIRED "Sign in to confirm you're not
 * a bot", even though browsing works. When that happens the same request is sent again as other
 * clients, and the first answer that has playable streams is handed back to the extractor in place
 * of the refusal. The extractor then reads it as it would a VISIONOS answer (it already handles
 * ciphered signatures and the `n` parameter for any client).
 *
 * Order: Android VR; with an account, the TV client and Safari web client signed in; the TV client
 * anonymously; the web and mobile-web clients with a BotGuard PO token (what youtube.com itself
 * sends); and the iOS client. Every attempt's outcome is kept in [lastReport] so a failure can say
 * what was tried.
 */
internal class PlayerFallback(
    context: Context,
    private val http: OkHttpClient,
    private val accountCookies: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val poTokens = PoTokens(context.applicationContext, http)

    private class Client(
        val label: String,
        val name: String,
        val id: Int,
        val version: () -> String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
        val needsSignatureTimestamp: Boolean = false,
        val signedIn: Boolean = false,
        val poToken: Boolean = false,
        /** Its direct stream URLs need a token we don't have, but its HLS manifest plays. */
        val hlsOnly: Boolean = false,
    )

    /** True for NewPipe's VISIONOS stream request (not the WEB metadata-only one). */
    fun isStreamRequest(url: String, body: ByteArray?): Boolean {
        if (body == null || !url.contains("/youtubei/v1/player") || url.contains("\$fields=")) return false
        return String(body, Charsets.UTF_8).contains("\"VISIONOS\"")
    }

    /** True when the player answer is a refusal another client might not get. */
    fun isBlocked(responseText: String?): Boolean {
        if (responseText.isNullOrEmpty()) return false
        val status = runCatching { json.parseToJsonElement(responseText).jsonObject["playabilityStatus"] as? JsonObject }
            .getOrNull() ?: return false
        val state = status.string("status")?.uppercase() ?: return false
        val reason = (status.string("reason").orEmpty() + " " + status["messages"]?.toString().orEmpty()).lowercase()
        if (reason.contains("private")) return false
        return state == "LOGIN_REQUIRED" || reason.contains("bot")
    }

    /**
     * Tries the backup clients in order. Returns the first answer with playable streams for
     * [originalBody]'s video, or null if none had any.
     */
    fun retry(originalBody: ByteArray): String? {
        val original = runCatching { json.parseToJsonElement(String(originalBody, Charsets.UTF_8)).jsonObject }
            .getOrNull() ?: return null
        val videoId = original.string("videoId") ?: return null
        val cookies = accountCookies()?.takeIf { it.contains("SAPISID") || it.contains("__Secure-3PAPISID") }
        val clients = buildList {
            add(ANDROID_VR)
            if (cookies != null) {
                add(TV.signedIn())
                add(WEB_SAFARI.signedIn())
            }
            add(TV)
            add(WEB_PO)
            add(MWEB_PO)
            add(IOS)
        }
        val report = ArrayList<String>()
        report += "VISIONOS: refused"
        if (accountCookies() != null && cookies == null) report += "account: no SAPISID cookie"
        for (client in clients) {
            val outcome = runCatching { request(client, original, videoId, cookies) }
            val text = outcome.getOrNull()
            val result = when {
                outcome.isFailure -> "error ${outcome.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }}"
                text == null -> "no answer"
                else -> verdict(text, videoId, client.hlsOnly)
            }
            val label = client.label + if (client.signedIn) " (account)" else ""
            report += "$label: $result"
            Log.w(TAG, "$label for $videoId: $result")
            if (result == OK && text != null) {
                lastReport = null
                return text
            }
        }
        lastReport = report.joinToString("; ").take(600)
        return null
    }

    private fun request(client: Client, original: JsonObject, videoId: String, cookies: String?): String? {
        val oldClient = (original["context"] as? JsonObject)?.get("client") as? JsonObject
        val oldVisitorData = oldClient?.string("visitorData")
        val pot: PoTokens.Result? = if (client.poToken) {
            val vd = oldVisitorData ?: throw IllegalStateException("no visitor data")
            poTokens.forVideo(videoId) { vd }
        } else {
            null
        }
        val visitorData: String? = pot?.visitorData ?: oldVisitorData
        val sts = if (client.needsSignatureTimestamp) {
            runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()
        } else {
            null
        }
        val version = client.version()

        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", version)
                    put("hl", oldClient?.string("hl") ?: "en")
                    put("gl", oldClient?.string("gl") ?: "US")
                    oldClient?.get("utcOffsetMinutes")?.let { put("utcOffsetMinutes", it) }
                    if (visitorData != null) put("visitorData", visitorData)
                    client.extra.forEach { (k, v) ->
                        when (v) {
                            is Int -> put(k, v)
                            else -> put(k, v.toString())
                        }
                    }
                }
                putJsonObject("request") { put("useSsl", true) }
                putJsonObject("user") { put("lockedSafetyMode", false) }
            }
            put("videoId", videoId)
            original.string("cpn")?.let { put("cpn", it) }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (sts != null) {
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", sts)
                    }
                }
            }
            if (pot != null) {
                putJsonObject("serviceIntegrityDimensions") { put("poToken", pot.playerPot) }
            }
        }.toString()

        val builder = Request.Builder()
            .url("$ORIGIN/youtubei/v1/player?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.id.toString())
            .header("X-YouTube-Client-Version", version)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
        if (visitorData != null) builder.header("X-Goog-Visitor-Id", visitorData)
        if (client.signedIn && cookies != null) {
            builder.header("Cookie", cookies)
            builder.header("X-Goog-AuthUser", "0")
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
            sapisidHash(cookies)?.let { builder.header("Authorization", it) }
        }
        http.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string()
            if (!res.isSuccessful) {
                val reason = runCatching {
                    json.parseToJsonElement(text.orEmpty()).jsonObject["error"]?.jsonObject?.string("message")
                }.getOrNull()
                throw IllegalStateException("HTTP ${res.code}" + (reason?.let { " $it" } ?: ""))
            }
            if (text == null) return null
            if (client.hlsOnly) return hlsOnly(text)
            if (pot == null) return text
            return withStreamingPot(text, pot.streamingPot)
        }
    }

    /** "ok", or what was wrong with this answer. */
    private fun verdict(text: String, videoId: String, needHls: Boolean): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return "bad JSON"
        val ps = obj["playabilityStatus"] as? JsonObject
        val status = ps?.string("status")
        if (!status.equals("OK", ignoreCase = true)) {
            return "$status" + (ps?.string("reason")?.let { " ($it)" } ?: "")
        }
        if ((obj["videoDetails"] as? JsonObject)?.string("videoId") != videoId) return "wrong video"
        val streaming = obj["streamingData"] as? JsonObject ?: return "no streamingData"
        val formats = ((streaming["adaptiveFormats"] as? JsonArray).orEmpty() + (streaming["formats"] as? JsonArray).orEmpty())
            .filterIsInstance<JsonObject>()
        if (needHls) return if (streaming.string("hlsManifestUrl") != null) OK else "no HLS"
        val usable = formats.any { it["url"] != null || it["signatureCipher"] != null || it["cipher"] != null }
        if (usable || streaming.string("hlsManifestUrl") != null) return OK
        return if (streaming["serverAbrStreamingUrl"] != null) "SABR only" else "no streams"
    }

    /** Keeps only the HLS manifest of an answer that has one, so the player uses it. */
    private fun hlsOnly(text: String): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return text
        val streaming = obj["streamingData"] as? JsonObject ?: return text
        if (streaming.string("hlsManifestUrl") == null) return text
        val trimmed = JsonObject(streaming - "adaptiveFormats" - "formats")
        return JsonObject(obj + ("streamingData" to trimmed)).toString()
    }

    /** Adds `pot=` to every stream URL (plain or inside a signature cipher) of a player answer. */
    private fun withStreamingPot(text: String, streamingPot: String): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return text
        val streaming = obj["streamingData"] as? JsonObject ?: return text
        fun fix(arr: JsonElement?): JsonElement? = (arr as? JsonArray)?.let { a ->
            JsonArray(a.map { f ->
                val format = f as? JsonObject ?: return@map f
                val url = format.string("url")
                val cipherKey = listOf("signatureCipher", "cipher").firstOrNull { format[it] != null }
                when {
                    url != null -> JsonObject(format + ("url" to JsonPrimitive("$url&pot=$streamingPot")))
                    cipherKey != null -> {
                        val parts = format.string(cipherKey).orEmpty().split('&').map { part ->
                            if (part.startsWith("url=")) {
                                val u = URLDecoder.decode(part.removePrefix("url="), "UTF-8") + "&pot=$streamingPot"
                                "url=" + URLEncoder.encode(u, "UTF-8")
                            } else {
                                part
                            }
                        }
                        JsonObject(format + (cipherKey to JsonPrimitive(parts.joinToString("&"))))
                    }
                    else -> format
                }
            })
        }
        val newStreaming = JsonObject(
            streaming + listOfNotNull(
                fix(streaming["adaptiveFormats"])?.let { "adaptiveFormats" to it },
                fix(streaming["formats"])?.let { "formats" to it },
            )
        )
        return JsonObject(obj + ("streamingData" to newStreaming)).toString()
    }

    private fun Client.signedIn() =
        Client(label, name, id, version, userAgent, extra, needsSignatureTimestamp, signedIn = true, poToken = poToken, hlsOnly = hlsOnly)

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val TAG = "PlayerFallback"
        private const val ORIGIN = "https://www.youtube.com"
        private const val OK = "ok"

        /** What the backup clients answered the last time they were all refused (shown with the error). */
        @Volatile
        var lastReport: String? = null
            private set

        const val ANDROID_VR_UA =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
        const val SAFARI_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"
        const val MWEB_UA =
            "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)"
        const val IOS_UA = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

        private fun webVersion(): String =
            runCatching { YoutubeParsingHelper.getClientVersion() }.getOrDefault("2.20251111.01.00")

        private val ANDROID_VR = Client(
            label = "Android VR",
            name = "ANDROID_VR",
            id = 28,
            version = { "1.65.10" },
            userAgent = ANDROID_VR_UA,
            extra = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12L",
            ),
        )

        private val TV = Client(
            label = "TV",
            name = "TVHTML5",
            id = 7,
            version = { "7.20250923.13.00" },
            userAgent = TV_UA,
            needsSignatureTimestamp = true,
        )

        private val WEB_SAFARI = Client(
            label = "Safari",
            name = "WEB",
            id = 1,
            version = ::webVersion,
            userAgent = SAFARI_UA,
            needsSignatureTimestamp = true,
            hlsOnly = true,
        )

        private val WEB_PO = Client(
            label = "Web + PO token",
            name = "WEB",
            id = 1,
            version = ::webVersion,
            userAgent = PoTokens.USER_AGENT,
            needsSignatureTimestamp = true,
            poToken = true,
        )

        private val MWEB_PO = Client(
            label = "Mobile web + PO token",
            name = "MWEB",
            id = 2,
            version = ::webVersion,
            userAgent = MWEB_UA,
            needsSignatureTimestamp = true,
            poToken = true,
        )

        private val IOS = Client(
            label = "iOS",
            name = "IOS",
            id = 5,
            version = { "20.10.4" },
            userAgent = IOS_UA,
            extra = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.3.2.22D82",
            ),
            hlsOnly = true,
        )

        /** The User-Agent a googlevideo stream URL must be fetched with, by the client it was issued to. */
        fun userAgentFor(url: String): String = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            url.contains("c=ANDROID_VR") -> ANDROID_VR_UA
            url.contains("c=TVHTML5") -> TV_UA
            url.contains("c=MWEB") -> MWEB_UA
            url.contains("c=IOS") || url.contains("/c/IOS/") -> IOS_UA
            url.contains("c=WEB") -> PoTokens.USER_AGENT
            else -> NewPipeDownloader.USER_AGENT
        }

        /** YouTube's signed-in request header for the web origin. */
        fun sapisidHash(cookies: String): String? {
            fun cookie(name: String) =
                cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
            val sapisid = cookie("SAPISID") ?: cookie("__Secure-3PAPISID") ?: return null
            val now = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray())
            return "SAPISIDHASH ${now}_" + digest.joinToString("") { "%02x".format(it) }
        }
    }
}
