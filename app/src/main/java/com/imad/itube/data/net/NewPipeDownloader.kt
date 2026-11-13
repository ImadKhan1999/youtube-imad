package com.imad.itube.data.net

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * The HTTP layer NewPipeExtractor uses to talk to YouTube.
 *
 * Extra cookies (restricted mode now, the signed-in account later) are attached to
 * youtube.com requests only.
 */
class NewPipeDownloader(context: android.content.Context, private val client: OkHttpClient) : Downloader() {

    @Volatile
    var youtubeCookies: String = ""

    /** The signed-in account's cookies, used only by the backup player clients. */
    @Volatile
    var accountCookies: () -> String? = { null }

    private val playerFallback = PlayerFallback(context, client) { accountCookies() }

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", if (request.url().contains("googlevideo.com")) PlayerFallback.userAgentFor(request.url()) else USER_AGENT)

        val url = request.url()
        if (youtubeCookies.isNotBlank() && url.contains("youtube.com")) {
            builder.header("Cookie", youtubeCookies)
        }

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha challenge requested", url)
            }
            var text = response.body?.string()
            // YouTube refused the stream request (usually "confirm you're not a bot" on a VPN):
            // ask again as other clients before giving up.
            if (playerFallback.isStreamRequest(url, request.dataToSend()) && playerFallback.isBlocked(text)) {
                playerFallback.retry(request.dataToSend()!!)?.let { text = it }
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
        const val RESTRICTED_MODE_COOKIE = "PREF=f2=8000000"
    }
}
