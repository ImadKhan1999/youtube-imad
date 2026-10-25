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
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Volatile
    var youtubeCookies: String = ""

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", USER_AGENT)

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
            val text = response.body?.string()
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
