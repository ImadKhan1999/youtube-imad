package com.imad.itube.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.imad.itube.data.net.NewPipeDownloader
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Wraps an [HttpDataSource] and rewrites requests to googlevideo `videoplayback` URLs the way
 * YouTube's own clients do (ported from NewPipe's YoutubeHttpDataSource):
 *
 * - byte ranges are sent as a `range=` query parameter instead of a `Range` header,
 * - each request carries an increasing `rn=` request number,
 * - requests are POSTs with the tiny protobuf body the web player sends,
 * - the User-Agent matches the client the stream URL was issued to.
 *
 * Without this, YouTube throttles or rejects many adaptive streams.
 */
@OptIn(UnstableApi::class)
class YoutubeDataSource private constructor(
    private val upstream: HttpDataSource,
    private val rangeParameterEnabled: Boolean,
    private val rnParameterEnabled: Boolean,
) : DataSource {

    private var requestNumber = 0L

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        val headers = HashMap<String, String>()
        headers["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            NewPipeDownloader.USER_AGENT
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers["Origin"] = YOUTUBE_BASE_URL
            headers["Referer"] = YOUTUBE_BASE_URL
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
        }

        val isVideoPlayback = dataSpec.uri.path?.startsWith("/videoplayback") == true
        if (!isVideoPlayback) {
            return upstream.open(dataSpec.withAdditionalHeaders(headers))
        }

        val builder = StringBuilder(url)
        if (rnParameterEnabled && !url.contains("&rn=")) {
            builder.append("&rn=").append(requestNumber++)
        }

        var spec = dataSpec
        if (rangeParameterEnabled) {
            val position = dataSpec.position
            val length = dataSpec.length
            if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
                builder.append("&range=").append(position).append('-')
                if (length != C.LENGTH_UNSET.toLong()) builder.append(position + length - 1)
                // The range is now in the URL, so ask upstream for the whole (sliced) response.
                spec = spec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build()
            }
        }

        spec = spec.buildUpon()
            .setUri(Uri.parse(builder.toString()))
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .build()
            .withAdditionalHeaders(headers)

        return upstream.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    class Factory(
        private val rangeParameterEnabled: Boolean,
        private val rnParameterEnabled: Boolean,
    ) : DataSource.Factory {
        private val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        override fun createDataSource(): DataSource =
            YoutubeDataSource(httpFactory.createDataSource(), rangeParameterEnabled, rnParameterEnabled)
    }

    private companion object {
        const val YOUTUBE_BASE_URL = "https://www.youtube.com"
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
