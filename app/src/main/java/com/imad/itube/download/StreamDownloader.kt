package com.imad.itube.download

import com.imad.itube.data.net.NewPipeDownloader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads one googlevideo stream to a file in 8 MB slices, using the `range=` parameter the
 * same way the player does (whole-file requests get throttled hard by YouTube).
 */
class StreamDownloader(private val client: OkHttpClient) {

    suspend fun download(
        url: String,
        knownLength: Long,
        target: File,
        onBytes: (Long) -> Unit,
    ) {
        val ua = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            NewPipeDownloader.USER_AGENT
        }
        var position = 0L
        val total = knownLength
        var rn = 0
        FileOutputStream(target).use { out ->
            while (total <= 0 || position < total) {
                currentCoroutineContext().ensureActive()
                val end = position + CHUNK - 1
                val req = Request.Builder()
                    .url("$url&range=$position-$end&rn=${rn++}")
                    .header("User-Agent", ua)
                    .post(byteArrayOf(0x78, 0).toRequestBody("application/x-protobuf".toMediaType()))
                    .build()
                var read = 0L
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} while downloading")
                    val body = resp.body ?: throw IOException("Empty response")
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            onBytes(n.toLong())
                        }
                    }
                }
                position += read
                if (read < CHUNK) break // last slice
            }
        }
        if (total > 0 && position < total) throw IOException("Download ended early ($position of $total bytes)")
    }

    private companion object {
        const val CHUNK = 8L * 1024 * 1024
    }
}
