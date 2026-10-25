package com.imad.itube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.imad.itube.ui.DeepLink
import com.imad.itube.ui.HostCallbacks
import com.imad.itube.ui.MainScreen
import com.imad.itube.ui.MainUiState
import com.imad.itube.ui.theme.ITubeTheme
import org.schabi.newpipe.extractor.ServiceList

class MainActivity : ComponentActivity() {

    private val ui: MainUiState by viewModels()
    private val container get() = appContainer
    private val inPip = mutableStateOf(false)
    private var leftPipAt = 0L

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        inPip.value = info.isInPictureInPictureMode
        if (!info.isInPictureInPictureMode) leftPipAt = System.currentTimeMillis()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        addOnPictureInPictureModeChangedListener(pipListener)

        val host = HostCallbacks(
            enterPip = { enterPip() },
            moveToBackground = {
                Toast.makeText(this, "Playing in the background", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            },
            requestFullscreen = { fs ->
                requestedOrientation = if (fs) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            },
            onFullscreenChanged = { fs -> applyFullscreen(fs) },
            releaseOrientation = { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED },
            share = { title, url ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(send, "Share"))
            },
        )

        setContent {
            val settings by container.settings.settings.collectAsState()
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            ITubeTheme(settings.theme) {
                MainScreen(ui = ui, isInPip = inPip.value, isLandscape = landscape, host = host)
            }
        }

        if (savedInstanceState == null) handleIntent(intent)
        askForNotifications()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = container.player.player
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            container.settings.settings.value.pipOnHome && ui.watchExpanded.value &&
            player.isPlaying && container.player.state.value.item != null
        ) {
            enterPip()
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.setVideoTrackEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val player = container.player.player
        val closedPip = System.currentTimeMillis() - leftPipAt < 1500
        if (closedPip || !container.settings.settings.value.backgroundPlay) {
            // Swiping the PiP window away, or background play turned off: stop like YouTube does.
            player.pause()
        } else if (player.isPlaying) {
            // Keep the audio going, but stop fetching and decoding video nobody can see.
            container.player.setVideoTrackEnabled(false)
        }
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Popup player isn't supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        if (container.player.state.value.item == null) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    private fun applyFullscreen(fullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_PLAYER -> if (container.player.state.value.item != null) ui.expand()
            ACTION_OPEN_DOWNLOADS -> ui.open(DeepLink.Downloads)
            Intent.ACTION_VIEW -> intent.dataString?.let(::openUrl)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val url = Regex("https?://\\S+").find(text)?.value
                if (url != null) openUrl(url) else ui.open(DeepLink.Search(text))
            }
        }
    }

    private fun openUrl(url: String) {
        val yt = ServiceList.YouTube
        val accepts = { f: org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory ->
            runCatching { f.acceptUrl(url) }.getOrDefault(false)
        }
        when {
            accepts(yt.streamLHFactory) -> {
                container.player.playUrl(url)
                ui.expand()
            }
            accepts(yt.playlistLHFactory) -> ui.open(DeepLink.Playlist(url))
            accepts(yt.channelLHFactory) -> ui.open(DeepLink.Channel(url))
            else -> Toast.makeText(this, "iTube can't open this link", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.imad.itube.OPEN_PLAYER"
        const val ACTION_OPEN_DOWNLOADS = "com.imad.itube.OPEN_DOWNLOADS"
    }
}
