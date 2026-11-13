package com.imad.itube.player

import android.net.Uri
import androidx.annotation.OptIn
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.ConcurrentHashMap

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
 *
 * If the edge host in the URL (`rrN---sn-….googlevideo.com`) can't be reached at all (DNS failure,
 * refused or reset connection), the request is retried once through
 * `redirector.googlevideo.com`, which redirects to an edge server that works for this network.
 */
@OptIn(UnstableApi::class)
class YoutubeDataSource private constructor(
    private val upstreamFactory: HttpDataSource.Factory,
    private val rangeParameterEnabled: Boolean,
    private val rnParameterEnabled: Boolean,
) : DataSource {

    private var requestNumber = 0L
    private val listeners = ArrayList<TransferListener>(1)
    private var upstream: HttpDataSource = upstreamFactory.createDataSource()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        val headers = HashMap<String, String>()
        headers["User-Agent"] = com.imad.itube.data.net.PlayerFallback.userAgentFor(url)
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

        return openWithHostFallback(spec)
    }

    private fun openWithHostFallback(spec: DataSpec): Long {
        val host = spec.uri.host.orEmpty()
        val isEdgeHost = EDGE_HOST.matches(host)
        if (isEdgeHost && unreachableHosts.containsKey(host)) return upstream.open(viaRedirector(spec))
        try {
            return upstream.open(spec)
        } catch (e: HttpDataSource.HttpDataSourceException) {
            if (!isEdgeHost || !isConnectionFailure(e)) throw e
            Log.w(TAG, "Can't reach $host, retrying through $REDIRECTOR_HOST", e)
            unreachableHosts[host] = true
            runCatching { upstream.close() }
            upstream = upstreamFactory.createDataSource().also { ds -> listeners.forEach(ds::addTransferListener) }
            try {
                return upstream.open(viaRedirector(spec))
            } catch (retry: HttpDataSource.HttpDataSourceException) {
                retry.addSuppressed(e)
                throw retry
            }
        }
    }

    private fun viaRedirector(spec: DataSpec): DataSpec =
        spec.buildUpon().setUri(spec.uri.buildUpon().authority(REDIRECTOR_HOST).build()).build()

    private fun isConnectionFailure(e: HttpDataSource.HttpDataSourceException): Boolean =
        e !is HttpDataSource.InvalidResponseCodeException &&
            e !is HttpDataSource.InvalidContentTypeException &&
            (e.reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    class Factory(
        private val rangeParameterEnabled: Boolean,
        private val rnParameterEnabled: Boolean,
    ) : DataSource.Factory {
        private val httpFactory = OkHttpDataSource.Factory(StreamHttp.client)

        override fun createDataSource(): DataSource =
            YoutubeDataSource(httpFactory, rangeParameterEnabled, rnParameterEnabled)
    }

    private companion object {
        const val TAG = "YoutubeDataSource"
        const val REDIRECTOR_HOST = "redirector.googlevideo.com"
        val EDGE_HOST by lazy { Regex("""rr\d+---sn-[a-z0-9-]+\.googlevideo\.com""") }

        /** Edge hosts this network couldn't reach; later requests go to the redirector directly. */
        val unreachableHosts = ConcurrentHashMap<String, Boolean>()
        const val YOUTUBE_BASE_URL = "https://www.youtube.com"
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
