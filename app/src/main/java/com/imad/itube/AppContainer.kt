package com.imad.itube

import android.app.Application
import com.imad.itube.data.YouTubeRepository
import com.imad.itube.data.net.NewPipeDownloader
import com.imad.itube.data.store.LibraryStore
import com.imad.itube.data.store.SettingsStore
import com.imad.itube.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container, created once by [ITubeApp]. */
class AppContainer(val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val downloader = NewPipeDownloader(okHttp)

    val settings = SettingsStore(app, appScope)
    val library = LibraryStore(app, appScope)
    val repository = YouTubeRepository()
    val player = PlayerController(app, repository, settings, library, appScope)

    init {
        val locale = Locale.getDefault()
        NewPipe.init(
            downloader,
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "US" }),
        )
        appScope.launch {
            settings.settings.collect { s ->
                downloader.youtubeCookies = if (s.restrictedMode) NewPipeDownloader.RESTRICTED_MODE_COOKIE else ""
            }
        }
    }
}
