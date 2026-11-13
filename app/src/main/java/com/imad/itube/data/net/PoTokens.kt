package com.imad.itube.data.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Mints YouTube "proof of origin" (PO) tokens the way youtube.com does: Google's BotGuard program
 * runs in a hidden WebView, and its output is exchanged for an integrity token that can then mint
 * tokens for any identifier. A player request that carries a PO token for its video id is usually
 * let through where an anonymous one gets "Sign in to confirm you're not a bot", and the stream
 * URLs then need the token minted for the visitor id as `pot=`.
 *
 * Ported from NewPipe's PoTokenWebView / PoTokenProviderImpl (GPL-3.0), without RxJava.
 */
internal class PoTokens(private val context: Context, private val http: OkHttpClient) {

    class Result(val visitorData: String, val playerPot: String, val streamingPot: String)

    private val lock = Any()
    private var generator: Generator? = null
    private var visitorData: String? = null
    private var streamingPot: String? = null

    /** Tokens for [videoId], or an exception saying why there are none. Blocking; never call on the main thread. */
    fun forVideo(videoId: String, freshVisitorData: () -> String): Result {
        return try {
            mint(videoId, freshVisitorData, forceRecreate = false)
        } catch (e: Exception) {
            Log.w(TAG, "PO token failed, retrying with a new BotGuard run", e)
            mint(videoId, freshVisitorData, forceRecreate = true)
        }
    }

    private fun mint(videoId: String, freshVisitorData: () -> String, forceRecreate: Boolean): Result {
        val (gen, vd, spot) = synchronized(lock) {
            val current = generator
            if (current == null || forceRecreate || current.isExpired()) {
                current?.let { old -> main.post { old.close() } }
                val newVisitor = freshVisitorData()
                val created = Generator.create(context, http)
                visitorData = newVisitor
                generator = created
                // The streaming token must be minted (once) before any player token.
                streamingPot = created.mint(newVisitor)
            }
            Triple(generator!!, visitorData!!, streamingPot!!)
        }
        return Result(vd, gen.mint(videoId), spot)
    }

    /** The streaming token for URLs issued to [visitor], if this session minted one. */
    fun streamingPotFor(visitor: String?): String? = synchronized(lock) {
        streamingPot.takeIf { visitor == null || visitor == visitorData }
    }

    val currentStreamingPot: String? get() = synchronized(lock) { streamingPot }

    @SuppressLint("SetJavaScriptEnabled")
    private class Generator private constructor(context: Context, private val http: OkHttpClient) {
        private val webView = WebView(context)
        private val ready = CompletableFuture<Unit>()
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
        @Volatile private var expiresAt = 0L

        init {
            webView.settings.javaScriptEnabled = true
            webView.settings.safeBrowsingEnabled = false
            webView.settings.userAgentString = USER_AGENT
            webView.settings.blockNetworkLoads = true
            webView.addJavascriptInterface(this, JS)
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.message().contains("Uncaught")) fail("WebView JS error: ${m.message()}")
                    return super.onConsoleMessage(m)
                }
            }
        }

        private fun load(context: Context) {
            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(
                "https://www.youtube.com",
                html.replaceFirst("</script>", "\n$JS.downloadAndRunBotguard()</script>"),
                "text/html",
                "utf-8",
                null,
            )
        }

        @JavascriptInterface
        fun downloadAndRunBotguard() {
            Thread {
                try {
                    val body = post("https://www.youtube.com/api/jnn/v1/Create", "[ \"$REQUEST_KEY\" ]")
                    val challenge = parseChallengeData(body)
                    main.post {
                        webView.evaluateJavascript(
                            """try {
                                data = $challenge
                                runBotGuard(data).then(function (result) {
                                    this.webPoSignalOutput = result.webPoSignalOutput
                                    $JS.onRunBotguardResult(result.botguardResponse)
                                }, function (error) {
                                    $JS.onJsError(error + "\n" + error.stack)
                                })
                            } catch (error) {
                                $JS.onJsError(error + "\n" + error.stack)
                            }""",
                            null,
                        )
                    }
                } catch (e: Exception) {
                    fail("BotGuard Create failed: ${e.message}")
                }
            }.start()
        }

        @JavascriptInterface
        fun onJsError(error: String) = fail("BotGuard: $error")

        @JavascriptInterface
        fun onRunBotguardResult(botguardResponse: String) {
            Thread {
                try {
                    val body = post(
                        "https://www.youtube.com/api/jnn/v1/GenerateIT",
                        "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
                    )
                    val arr = Json.parseToJsonElement(body).jsonArray
                    val token = base64ToU8((arr[0] as JsonPrimitive).content)
                    val ttl = (arr[1] as JsonPrimitive).long
                    expiresAt = System.currentTimeMillis() + (ttl - 600).coerceAtLeast(60) * 1000
                    main.post {
                        webView.evaluateJavascript("this.integrityToken = $token") { ready.complete(Unit) }
                    }
                } catch (e: Exception) {
                    fail("BotGuard GenerateIT failed: ${e.message}")
                }
            }.start()
        }

        fun mint(identifier: String): String {
            val future = CompletableFuture<String>()
            pending[identifier] = future
            val u8 = newUint8Array(identifier.toByteArray())
            main.post {
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, $u8)
                        s = ""
                        for (i = 0; i < poTokenU8.length; i++) { if (i != 0) s += ","; s += poTokenU8[i] }
                        $JS.onPoToken(identifier, s)
                    } catch (error) {
                        $JS.onPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
                    null,
                )
            }
            return future.get(TIMEOUT_S, TimeUnit.SECONDS)
        }

        @JavascriptInterface
        fun onPoToken(identifier: String, u8: String) {
            val bytes = u8.split(",").map { it.trim().toInt().toByte() }.toByteArray()
            val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            pending.remove(identifier)?.complete(token)
        }

        @JavascriptInterface
        fun onPoTokenError(identifier: String, error: String) {
            pending.remove(identifier)?.completeExceptionally(IllegalStateException("obtainPoToken: $error"))
        }

        fun isExpired() = System.currentTimeMillis() > expiresAt

        private fun fail(message: String) {
            Log.w(TAG, message)
            val e = IllegalStateException(message)
            ready.completeExceptionally(e)
            pending.values.forEach { it.completeExceptionally(e) }
            pending.clear()
        }

        fun close() {
            runCatching {
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
        }

        private fun post(url: String, data: String): String {
            val req = Request.Builder()
                .url(url)
                .post(data.toRequestBody("application/json+protobuf".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .build()
            http.newCall(req).execute().use { res ->
                if (res.code != 200) throw IllegalStateException("HTTP ${res.code}")
                return res.body?.string().orEmpty()
            }
        }

        companion object {
            /** Loads BotGuard and waits for the integrity token. Blocking; never on the main thread. */
            fun create(context: Context, http: OkHttpClient): Generator {
                val made = CompletableFuture<Generator>()
                main.post {
                    try {
                        val g = Generator(context.applicationContext, http)
                        made.complete(g)
                        g.load(context.applicationContext)
                    } catch (e: Throwable) {
                        made.completeExceptionally(e)
                    }
                }
                val g = made.get(TIMEOUT_S, TimeUnit.SECONDS)
                try {
                    g.ready.get(TIMEOUT_S, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    main.post { g.close() }
                    throw e
                }
                return g
            }
        }
    }

    companion object {
        private const val TAG = "PoTokens"
        private const val TIMEOUT_S = 20L
        private const val JS = "ITubePoToken"
        // Public BotGuard key and request key used by youtube.com (same values NewPipe uses).
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val main by lazy { Handler(Looper.getMainLooper()) }

        private fun parseChallengeData(raw: String): String {
            val scrambled = Json.parseToJsonElement(raw).jsonArray
            val second = scrambled.getOrNull(1) as? JsonPrimitive
            val data: JsonArray = if (scrambled.size > 1 && second?.isString == true) {
                val bytes = base64Bytes(second.content).map { (it + 97).toByte() }.toByteArray()
                Json.parseToJsonElement(bytes.decodeToString()).jsonArray
            } else {
                scrambled[0].jsonArray
            }
            fun str(i: Int) = (data.getOrNull(i) as? JsonPrimitive)?.contentOrNull
            fun firstString(i: Int) = (data.getOrNull(i) as? JsonArray)
                ?.firstOrNull { it is JsonPrimitive && it.isString }?.let { (it as JsonPrimitive).content }
            return buildJsonObject {
                put("messageId", str(0))
                putJsonObject("interpreterJavascript") {
                    put("privateDoNotAccessOrElseSafeScriptWrappedValue", firstString(1))
                    put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", firstString(2))
                }
                put("interpreterHash", str(3))
                put("program", str(4))
                put("globalName", str(5))
                put("clientExperimentsStateBlob", str(7))
            }.toString()
        }

        private fun base64Bytes(s: String): ByteArray =
            Base64.decode(s.replace('-', '+').replace('_', '/').replace('.', '='), Base64.DEFAULT)

        private fun base64ToU8(s: String) = newUint8Array(base64Bytes(s))

        private fun newUint8Array(bytes: ByteArray) =
            "new Uint8Array([" + bytes.joinToString(",") { (it.toInt() and 0xff).toString() } + "])"
    }
}
