package com.imad.itube.ui.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.imad.itube.account.Account
import com.imad.itube.appContainer
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.theme.YtRed

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3D%252F"

/**
 * Google's own sign-in page in a WebView. Once it lands back on youtube.com with a session,
 * the cookies are kept and the WebView's copy is wiped. iTube never sees your password.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(onBack: () -> Unit, onSignedIn: () -> Unit) {
    val container = LocalContext.current.appContainer
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    val cookies = remember { CookieManager.getInstance() }

    DisposableEffect(Unit) {
        cookies.removeAllCookies(null)
        onDispose {
            cookies.removeAllCookies(null)
            cookies.flush()
        }
    }

    fun check(url: String?) {
        if (done || url == null) return
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()
        if (!host.endsWith("youtube.com")) return
        val header = cookies.getCookie("https://www.youtube.com") ?: return
        if (!header.contains("SAPISID=")) return
        done = true
        container.accountStore.save(Account(cookies = header))
        container.onSignedIn()
        onSignedIn()
    }

    ScreenScaffold(topBar = { BackTopBar("Sign in", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(color = YtRed, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Google refuses sign-in from anything that looks like an embedded WebView.
                            // Present the same Chrome engine as a normal browser: drop the WebView
                            // markers from the user agent and the X-Requested-With app header.
                            settings.userAgentString = settings.userAgentString
                                .replace("; wv", "")
                                .replace(Regex("""Version/[\d.]+ """), "")
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                            }
                            cookies.setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                    check(url)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    check(url)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    check(request.url.toString())
                                    return done
                                }
                            }
                            loadUrl(LOGIN_URL)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}
