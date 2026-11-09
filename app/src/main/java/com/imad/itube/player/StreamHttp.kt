package com.imad.itube.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.imad.itube.data.net.FallbackDns
import com.imad.itube.data.net.NewPipeDownloader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The HTTP client every player data source shares (one connection pool, DNS fallback). */
@OptIn(UnstableApi::class)
object StreamHttp {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(FallbackDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** For non-googlevideo requests: live HLS playlists, captions. */
    fun plainFactory(): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(client).setUserAgent(NewPipeDownloader.USER_AGENT)
}
