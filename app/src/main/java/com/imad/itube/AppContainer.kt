package com.imad.itube

import android.app.Application
import com.imad.itube.account.AccountStore
import com.imad.itube.account.YouTubeAccount
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
    val avatars = com.imad.itube.data.AvatarCache()
    val downloadStore = com.imad.itube.download.DownloadStore(app, appScope)
    val downloads = com.imad.itube.download.DownloadManager(app, downloadStore, okHttp)
    val sponsorBlock = com.imad.itube.data.SponsorBlock(okHttp)
    val player = PlayerController(app, repository, settings, library, sponsorBlock, appScope)
    val accountStore = AccountStore(app, appScope)
    val account = YouTubeAccount(okHttp, accountStore)
    val accountComments = com.imad.itube.account.AccountComments(account)
    val liveChat = com.imad.itube.account.LiveChat(account)
    val updater = com.imad.itube.update.Updater(app, okHttp)

    /** Fetches the profile and copies the account's subscriptions into the library. */
    fun onSignedIn() {
        appScope.launch {
            runCatching { accountStore.save(account.profile()) }
            syncSubscriptions()
        }
    }

    suspend fun syncSubscriptions(): Result<Int> = runCatching {
        val subs = account.subscriptions()
        library.mergeSubscriptions(subs)
        subs.size
    }

    fun signOut() {
        accountStore.save(null)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

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
