package com.imad.itube.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.imad.itube.ui.channel.ChannelScreen
import com.imad.itube.ui.components.AppActions
import com.imad.itube.ui.components.EmptyBox
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.BackTopBar
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
import com.imad.itube.ui.watch.MiniPlayer
import com.imad.itube.ui.watch.PlayerSurface
import com.imad.itube.ui.watch.WatchCallbacks
import com.imad.itube.ui.watch.WatchScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
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

    val actions = remember(nav) {
        AppActions(
            openVideo = { v -> controller.play(v); ui.expand() },
            openChannel = { url -> ui.collapse(); nav.navigate("channel/${Uri.encode(url)}") },
            openPlaylist = { url -> ui.collapse(); nav.navigate("playlist/${Uri.encode(url)}") },
            openSearch = { nav.navigate("search") },
            toggleWatchLater = container.library::toggleWatchLater,
            isInWatchLater = { url -> container.library.data.value.watchLater.any { it.url == url } },
            share = host.share,
            toast = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
        )
    }

    val deepLink by ui.deepLink.collectAsState()
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Channel -> { ui.collapse(); nav.navigate("channel/${Uri.encode(link.url)}") }
            is DeepLink.Playlist -> { ui.collapse(); nav.navigate("playlist/${Uri.encode(link.url)}") }
            is DeepLink.Search -> { ui.collapse(); nav.navigate("search") }
            null -> {}
        }
        if (deepLink != null) ui.consumeDeepLink()
    }

    if (isInPip) {
        PlayerSurface(controller.player, Modifier.fillMaxSize())
        return
    }

    LaunchedEffect(hasVideo) { if (!hasVideo) ui.collapse() }
    val fullscreen = hasVideo && expanded && isLandscape
    LaunchedEffect(fullscreen) { host.onFullscreenChanged(fullscreen) }
    LaunchedEffect(hasVideo && expanded) { if (!(hasVideo && expanded)) host.releaseOrientation() }

    BackHandler(enabled = hasVideo && expanded) {
        if (fullscreen) host.requestFullscreen(false) else ui.collapse()
    }

    val watchCallbacks = WatchCallbacks(
        onCollapse = ui::collapse,
        onToggleFullscreen = { host.requestFullscreen(!fullscreen) },
        onEnterPip = host.enterPip,
        onBackground = {
            host.moveToBackground()
        },
        onDownload = { actions.toast("Downloads arrive in the next iTube update") },
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalAppActions provides actions) {
        Box(Modifier.fillMaxSize().background(Yt.colors.background)) {
            Scaffold(
                containerColor = Yt.colors.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (hasVideo && !expanded) MiniPlayer(onExpand = ui::expand)
                        BottomBar(nav)
                    }
                },
            ) { padding ->
                NavGraph(nav, ui, Modifier.padding(padding))
            }

            AnimatedVisibility(
                visible = hasVideo && expanded,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                WatchScreen(fullscreen = fullscreen, callbacks = watchCallbacks)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Column {
        HorizontalDivider(color = Yt.colors.divider)
        NavigationBar(containerColor = Yt.colors.background, tonalElevation = 0.dp) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                    label = { Text(tab.label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = Yt.colors.textPrimary,
                        selectedTextColor = Yt.colors.textPrimary,
                        unselectedIconColor = Yt.colors.textPrimary,
                        unselectedTextColor = Yt.colors.textPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NavGraph(nav: NavHostController, ui: MainUiState, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen() }
        composable("explore") { ExploreScreen(onOpenKiosk = { nav.navigate("kiosk/${it.name}") }) }
        composable("subscriptions") { SubscriptionsScreen() }
        composable("you") {
            LibraryScreen(
                onOpenList = { nav.navigate("list/${it.name}") },
                onOpenDownloads = { nav.navigate("downloads") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("search") { SearchScreen(onBack = back) }
        composable("channel/{url}") { e ->
            ChannelScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back)
        }
        composable("playlist/{url}") { e ->
            PlaylistScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back)
        }
        composable("kiosk/{name}") { e ->
            val kiosk = runCatching { Kiosk.valueOf(e.arguments?.getString("name").orEmpty()) }.getOrDefault(Kiosk.Music)
            KioskScreen(kiosk, onBack = back)
        }
        composable("list/{name}") { e ->
            val list = runCatching { VideoList.valueOf(e.arguments?.getString("name").orEmpty()) }.getOrDefault(VideoList.History)
            VideoListScreen(list, onBack = back)
        }
        composable("downloads") {
            ScreenScaffold(topBar = { BackTopBar("Downloads", back) }) { p ->
                EmptyBox(
                    "Downloads are on the way",
                    "Saving videos and audio for offline viewing arrives in the next update.",
                    Modifier.padding(p),
                )
            }
        }
        composable("settings") { SettingsScreen(onBack = back) }
    }
}
