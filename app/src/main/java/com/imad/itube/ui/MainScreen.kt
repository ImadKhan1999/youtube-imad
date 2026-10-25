package com.imad.itube.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.imad.itube.util.isTelevision
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.imad.itube.appContainer
import com.imad.itube.data.Kiosk
import com.imad.itube.ui.account.SignInScreen
import com.imad.itube.ui.cast.CastDialog
import com.imad.itube.ui.notifications.NotificationsScreen
import com.imad.itube.ui.channel.ChannelScreen
import com.imad.itube.ui.components.AppActions
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.downloads.DownloadRequest
import com.imad.itube.ui.downloads.DownloadSheet
import com.imad.itube.ui.downloads.DownloadsScreen
import com.imad.itube.ui.downloads.PlaylistDownloadSheet
import com.imad.itube.ui.explore.ExploreScreen
import com.imad.itube.ui.explore.KioskScreen
import com.imad.itube.ui.home.HomeScreen
import com.imad.itube.ui.library.LibraryScreen
import com.imad.itube.ui.library.VideoList
import com.imad.itube.ui.library.VideoListScreen
import com.imad.itube.ui.playlist.PlaylistScreen
import com.imad.itube.ui.search.SearchScreen
import com.imad.itube.ui.settings.SettingsScreen
import com.imad.itube.ui.subscriptions.SubscriptionsScreen
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.update.UpdateHost
import com.imad.itube.ui.watch.MiniPlayer
import com.imad.itube.ui.watch.PlayerSurface
import com.imad.itube.ui.watch.WatchCallbacks
import com.imad.itube.ui.watch.WatchScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("shorts", "Shorts", Icons.Outlined.Slideshow, Icons.Filled.Slideshow),
    Tab("explore", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("subscriptions", "Subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    Tab("you", "You", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
)

/** Activity-level hooks (orientation, PiP, sharing) the UI can't do on its own. */
class HostCallbacks(
    val enterPip: () -> Unit,
    val moveToBackground: () -> Unit,
    /** Rotates into (true) or out of (false) landscape full screen. */
    val requestFullscreen: (Boolean) -> Unit,
    /** Hides or shows the system bars to match the layout. */
    val onFullscreenChanged: (Boolean) -> Unit,
    /** Gives orientation back to the sensor once the watch page is closed. */
    val releaseOrientation: () -> Unit,
    val share: (String, String) -> Unit,
)

@Composable
fun MainScreen(
    ui: MainUiState,
    isInPip: Boolean,
    isLandscape: Boolean,
    host: HostCallbacks,
) {
    val container = LocalContext.current.appContainer
    val controller = container.player
    val watch by controller.state.collectAsState()
    val expanded by ui.watchExpanded.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hasVideo = watch.item != null
    var downloadRequest by remember { mutableStateOf<DownloadRequest?>(null) }
    var showCast by remember { mutableStateOf(false) }

    val actions = remember(nav) {
        AppActions(
            openVideo = { v -> controller.play(v); ui.expand() },
            playQueue = { items, index, title -> controller.playQueue(items, index, title); ui.expand() },
            openChannel = { url -> ui.collapse(); nav.navigate("channel/${Uri.encode(url)}") },
            openPlaylist = { url -> ui.collapse(); nav.navigate("playlist/${Uri.encode(url)}") },
            openSearch = { nav.navigate("search") },
            openNotifications = { nav.navigate("notifications") { launchSingleTop = true } },
            openCast = { showCast = true },
            toggleWatchLater = container.library::toggleWatchLater,
            isInWatchLater = { url -> container.library.data.value.watchLater.any { it.url == url } },
            share = host.share,
            download = { v -> downloadRequest = DownloadRequest.Single(v, emptyList()) },
            toast = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
        )
    }

    LaunchedEffect(Unit) { controller.messages.collect { actions.toast(it) } }
    // Fill the bell's badge in the background.
    LaunchedEffect(Unit) { runCatching { container.uploadAlerts.refresh() } }
    val deepLink by ui.deepLink.collectAsState()
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Channel -> { ui.collapse(); nav.navigate("channel/${Uri.encode(link.url)}") }
            is DeepLink.Playlist -> { ui.collapse(); nav.navigate("playlist/${Uri.encode(link.url)}") }
            is DeepLink.Search -> { ui.collapse(); nav.navigate("search") }
            DeepLink.Downloads -> { ui.collapse(); nav.navigate("downloads") { launchSingleTop = true } }
            null -> {}
        }
        if (deepLink != null) ui.consumeDeepLink()
    }

    if (isInPip) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            PlayerSurface(controller.player, Modifier.fillMaxSize())
            com.imad.itube.ui.watch.CaptionOverlay(controller.player, lifted = false)
        }
        return
    }

    LaunchedEffect(hasVideo) { if (!hasVideo) ui.collapse() }
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isTv = remember { context.isTelevision() }
    // Tablets and foldables keep the watch page in landscape (two panes) and go full screen on request.
    val largeScreen = config.smallestScreenWidthDp >= 600
    var manualFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) manualFullscreen = false }
    val fullscreen = hasVideo && expanded && (isTv || manualFullscreen || (isLandscape && !largeScreen))
    val wideWatch = largeScreen && isLandscape && !fullscreen
    LaunchedEffect(fullscreen) { host.onFullscreenChanged(fullscreen) }
    LaunchedEffect(hasVideo && expanded) { if (!(hasVideo && expanded)) host.releaseOrientation() }

    BackHandler(enabled = hasVideo && expanded) {
        when {
            isTv -> ui.collapse()
            manualFullscreen -> manualFullscreen = false
            fullscreen -> host.requestFullscreen(false)
            else -> ui.collapse()
        }
    }

    val watchCallbacks = WatchCallbacks(
        onCollapse = ui::collapse,
        onToggleFullscreen = {
            when {
                isTv -> {}
                largeScreen -> manualFullscreen = !manualFullscreen
                else -> host.requestFullscreen(!fullscreen)
            }
        },
        onEnterPip = host.enterPip,
        onBackground = {
            host.moveToBackground()
        },
        onDownload = { v -> downloadRequest = DownloadRequest.Single(v, watch.qualities.map { it.height }) },
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalAppActions provides actions) {
        Box(Modifier.fillMaxSize().background(Yt.colors.background)) {
            Scaffold(
                containerColor = Yt.colors.background,
                // Each screen's top bar pads for the status bar itself; padding here too doubled it.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (hasVideo && !expanded) MiniPlayer(onExpand = ui::expand)
                        BottomBar(nav)
                    }
                },
            ) { padding ->
                NavGraph(nav, ui, Modifier.padding(padding), onDownloadPlaylist = { title, url ->
                    downloadRequest = DownloadRequest.Playlist(title, url)
                })
            }

            AnimatedVisibility(
                visible = hasVideo && expanded,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                WatchScreen(fullscreen = fullscreen, wide = wideWatch, callbacks = watchCallbacks)
            }

            UpdateHost()
            com.imad.itube.ui.brand.WelcomeHost()
            if (showCast) CastDialog { showCast = false }

            when (val r = downloadRequest) {
                is DownloadRequest.Single -> DownloadSheet(r) { downloadRequest = null }
                is DownloadRequest.Playlist -> PlaylistDownloadSheet(r) { downloadRequest = null }
                null -> {}
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Column(Modifier.background(Yt.colors.background)) {
        HorizontalDivider(color = Yt.colors.divider, thickness = 0.5.dp)
        // YouTube's bar is much shorter than Material's 80dp NavigationBar.
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = Yt.colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(tab.label, fontSize = 10.sp, color = Yt.colors.textPrimary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NavGraph(
    nav: NavHostController,
    ui: MainUiState,
    modifier: Modifier,
    onDownloadPlaylist: (String, String) -> Unit,
) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen() }
        composable("shorts") { com.imad.itube.ui.shorts.ShortsScreen() }
        composable("explore") { ExploreScreen(onOpenKiosk = { nav.navigate("kiosk/${it.name}") }) }
        composable("subscriptions") { SubscriptionsScreen() }
        composable("you") {
            LibraryScreen(
                onOpenList = { nav.navigate("list/${it.name}") },
                onOpenDownloads = { nav.navigate("downloads") },
                onOpenSettings = { nav.navigate("settings") },
                onSignIn = { nav.navigate("signin") },
            )
        }
        composable("search") { SearchScreen(onBack = back) }
        composable("channel/{url}") { e ->
            ChannelScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back)
        }
        composable("playlist/{url}") { e ->
            PlaylistScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back, onDownload = onDownloadPlaylist)
        }
        composable("kiosk/{name}") { e ->
            val kiosk = runCatching { Kiosk.valueOf(e.arguments?.getString("name").orEmpty()) }.getOrDefault(Kiosk.Music)
            KioskScreen(kiosk, onBack = back)
        }
        composable("list/{name}") { e ->
            val list = runCatching { VideoList.valueOf(e.arguments?.getString("name").orEmpty()) }.getOrDefault(VideoList.History)
            VideoListScreen(list, onBack = back)
        }
        composable("downloads") { DownloadsScreen(onBack = back, onPlayed = ui::expand) }
        composable("settings") { SettingsScreen(onBack = back) }
        composable("notifications") { NotificationsScreen(onBack = back) }
        composable("signin") { SignInScreen(onBack = back, onSignedIn = back) }
    }
}
