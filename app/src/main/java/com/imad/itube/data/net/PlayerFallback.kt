package com.imad.itube.data.net

import android.util.Log
import kotlinx.serialization.json.Json
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
import java.security.MessageDigest

/**
 * Backup InnerTube clients for the video player request.
 *
 * NewPipeExtractor asks for a video's streams with a single client (VISIONOS). On many VPN and
 * data-centre IPs YouTube answers that request with LOGIN_REQUIRED "Sign in to confirm you're not
 * a bot", even though browsing works. Other YouTube clients are often still let through on the same
 * IP, so when that happens the same request is sent again as those clients, and the first answer
 * that has playable streams is handed back to the extractor in place of the refusal. The extractor
 * then reads it exactly as it would a VISIONOS answer (it already handles ciphered signatures and
 * the `n` parameter for any client).
 *
 * Order: the Android VR client (no PO token, plain stream URLs), then the TV client with the
 * signed-in account's cookies when there is one (a signed-in request isn't bot-checked), then the
 * TV client anonymously.
 */
internal class PlayerFallback(
    private val http: OkHttpClient,
    private val accountCookies: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private class Client(
        val name: String,
        val id: Int,
        val version: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
        val needsSignatureTimestamp: Boolean = false,
        val signedIn: Boolean = false,
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
        val cookies = accountCookies()?.takeIf { it.contains("SAPISID") }
        val clients = buildList {
            add(ANDROID_VR)
            if (cookies != null) add(TV.copySignedIn())
            add(TV)
        }
        for (client in clients) {
            val text = runCatching { request(client, original, videoId, cookies) }
                .onFailure { Log.w(TAG, "${client.name} player request failed", it) }
                .getOrNull() ?: continue
            if (isPlayable(text, videoId)) {
                Log.i(TAG, "Using ${client.name}${if (client.signedIn) " (signed in)" else ""} for $videoId")
                return text
            }
            Log.w(TAG, "${client.name} was refused too for $videoId")
        }
        return null
    }

    private fun request(client: Client, original: JsonObject, videoId: String, cookies: String?): String? {
        val oldContext = original["context"] as? JsonObject
        val oldClient = oldContext?.get("client") as? JsonObject
        val visitorData = oldClient?.string("visitorData")
        val sts = if (client.needsSignatureTimestamp) {
            runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()
        } else {
            null
        }

        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
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
        }.toString()

        val builder = Request.Builder()
            .url("$ORIGIN/youtubei/v1/player?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.id.toString())
            .header("X-YouTube-Client-Version", client.version)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
        if (visitorData != null) builder.header("X-Goog-Visitor-Id", visitorData)
        if (client.signedIn && cookies != null) {
            builder.header("Cookie", cookies)
            builder.header("X-Goog-AuthUser", "0")
            sapisidHash(cookies)?.let { builder.header("Authorization", it) }
        }
        http.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) return null
            return res.body?.string()
        }
    }

    private fun isPlayable(text: String, videoId: String): Boolean {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        val status = (obj["playabilityStatus"] as? JsonObject)?.string("status")
        if (!status.equals("OK", ignoreCase = true)) return false
        if ((obj["videoDetails"] as? JsonObject)?.string("videoId") != videoId) return false
        val streaming = obj["streamingData"] as? JsonObject ?: return false
        return streaming["adaptiveFormats"] != null || streaming["formats"] != null ||
            streaming["hlsManifestUrl"] != null
    }

    private fun Client.copySignedIn() = Client(name, id, version, userAgent, extra, needsSignatureTimestamp, signedIn = true)

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val TAG = "PlayerFallback"
        private const val ORIGIN = "https://www.youtube.com"

        const val ANDROID_VR_UA =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

        private val ANDROID_VR = Client(
            name = "ANDROID_VR",
            id = 28,
            version = "1.65.10",
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
            name = "TVHTML5",
            id = 7,
            version = "7.20250923.13.00",
            userAgent = TV_UA,
            needsSignatureTimestamp = true,
        )

        /** The User-Agent a googlevideo stream URL must be fetched with, by the client it was issued to. */
        fun userAgentFor(url: String): String = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            url.contains("c=ANDROID_VR") -> ANDROID_VR_UA
            url.contains("c=TVHTML5") -> TV_UA
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
