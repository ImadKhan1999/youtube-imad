package com.imad.itube.ui.tv

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.imad.itube.ui.DeepLink
import com.imad.itube.ui.MainUiState
import com.imad.itube.ui.account.SignInScreen
import com.imad.itube.ui.channel.ChannelScreen
import com.imad.itube.ui.components.AppActions
import com.imad.itube.ui.components.ITubeLogo
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.downloads.DownloadsScreen
import com.imad.itube.ui.explore.KioskScreen
import com.imad.itube.ui.library.VideoList
import com.imad.itube.ui.library.VideoListScreen
import com.imad.itube.ui.notifications.NotificationsScreen
import com.imad.itube.ui.playlist.PlaylistScreen
import com.imad.itube.ui.settings.SettingsScreen
import com.imad.itube.ui.shorts.ShortsScreen
import com.imad.itube.ui.update.UpdateHost
import kotlinx.coroutines.launch

private data class TvTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TV_TABS = listOf(
    TvTab("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    TvTab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TvTab("shorts", "Shorts", Icons.Outlined.Slideshow, Icons.Filled.Slideshow),
    TvTab("subscriptions", "Subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    TvTab("library", "Library", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    TvTab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

/**
 * The TV layout: a navigation rail on the left that opens up when focused, the browse screens
 * beside it, and a full-screen player on top while a video plays. Everything works with the
 * remote's arrows, OK and Back. No mini player and no picture-in-picture: Back stops the video.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMainScreen(ui: MainUiState) {
    val context = LocalContext.current
    val container = context.appContainer
    val controller = container.player
    val watch by controller.state.collectAsState()
    val expanded by ui.watchExpanded.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val playing = watch.item != null && expanded

    val actions = remember(nav) {
        AppActions(
            openVideo = { v -> controller.play(v); ui.expand() },
            playQueue = { items, index, title -> controller.playQueue(items, index, title); ui.expand() },
            openChannel = { url -> ui.collapse(); nav.navigate("channel/${Uri.encode(url)}") },
            openPlaylist = { url -> ui.collapse(); nav.navigate("playlist/${Uri.encode(url)}") },
            openSearch = { nav.navigate("search") { launchSingleTop = true } },
            openNotifications = { nav.navigate("notifications") { launchSingleTop = true } },
            openCast = {},
            toggleWatchLater = container.library::toggleWatchLater,
            isInWatchLater = { url -> container.library.data.value.watchLater.any { it.url == url } },
            share = { _, _ -> },
            download = { },
            toast = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
        )
    }

    LaunchedEffect(Unit) { controller.messages.collect { actions.toast(it) } }
    val deepLink by ui.deepLink.collectAsState()
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Channel -> { ui.collapse(); nav.navigate("channel/${Uri.encode(link.url)}") }
            is DeepLink.Playlist -> { ui.collapse(); nav.navigate("playlist/${Uri.encode(link.url)}") }
            is DeepLink.Search -> { ui.collapse(); nav.navigate("search") { launchSingleTop = true } }
            DeepLink.Downloads -> { ui.collapse(); nav.navigate("downloads") { launchSingleTop = true } }
            null -> {}
        }
        if (deepLink != null) ui.consumeDeepLink()
    }

    // Leaving the player (Back) stops the video, like YouTube on TV.
    val stopVideo: () -> Unit = {
        controller.close()
        ui.collapse()
    }
    // Nothing to show any more: drop the player.
    LaunchedEffect(watch.item) { if (watch.item == null) ui.collapse() }

    var voiceRequest by remember { mutableIntStateOf(0) }
    val railFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Back in the browse screens: go back a screen, then to the rail, then leave the app.
    BackHandler(enabled = !playing) {
        when {
            nav.previousBackStackEntry != null && route !in TV_TABS.map { it.route } -> nav.popBackStack()
            !railHasFocus -> runCatching { railFocus.requestFocus() }
            route != "home" -> nav.navigate("home") {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            else -> (context as? Activity)?.finish()
        }
    }

    // When the player closes, put focus back on the screen behind it.
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(playing) {
        if (!playing) runCatching { contentFocus.requestFocus() }
    }

    TvFocusTheme {
        CompositionLocalProvider(LocalAppActions provides actions) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TvBg)
                    // The remote's search / voice button jumps to Search and starts listening.
                    .onPreviewKeyEvent { e ->
                        if (playing || e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent false
                        if (e.key == Key.Search || e.key == Key.VoiceAssist) {
                            nav.navigate("search") { launchSingleTop = true }
                            voiceRequest++
                            true
                        } else {
                            false
                        }
                    },
            ) {
                // Browse screens. Coming back from the player or the rail lands on the card you left.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = RailCollapsed)
                        .focusRequester(contentFocus)
                        .focusRestorer(),
                ) {
                    TvNavGraph(nav, ui, voiceRequest)
                }

                TvNavRail(
                    nav = nav,
                    current = route,
                    focusRequester = railFocus,
                    onFocusChange = { railHasFocus = it },
                )

                AnimatedVisibility(playing, enter = fadeIn(), exit = fadeOut()) {
                    TvPlayerScreen(onExit = stopVideo)
                }

                UpdateHost()
                com.imad.itube.ui.brand.WelcomeHost()
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvNavRail(
    nav: NavHostController,
    current: String?,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (open) 260.dp else RailCollapsed, tween(180), label = "rail")
    val selectedIndex = TV_TABS.indexOfFirst { it.route == current }.takeIf { it >= 0 } ?: 1
    val requesters = remember { TV_TABS.map { FocusRequester() } }

    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (open) {
                    Brush.horizontalGradient(listOf(Color(0xFF0F0F0F), Color(0xF20F0F0F), Color(0xCC0F0F0F)))
                } else {
                    Brush.horizontalGradient(listOf(TvBg, TvBg))
                },
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                open = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            // Entering the rail lands on the tab you're on, like YouTube TV.
            .focusProperties { enter = { requesters[selectedIndex] } }
            .focusGroup()
            .padding(vertical = 28.dp),
    ) {
        Box(Modifier.padding(start = 28.dp, bottom = 28.dp).height(28.dp), contentAlignment = Alignment.CenterStart) {
            // The real iTube emblem; the word "iTube" joins it while the menu is open.
            if (open) {
                ITubeLogo(height = 22.dp)
            } else {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.imad.itube.R.drawable.ic_itube_emblem),
                    contentDescription = "iTube",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        TV_TABS.forEachIndexed { i, tab ->
            val selected = i == selectedIndex
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .focusRequester(requesters[i])
                    .tvClickable(shape = RoundedCornerShape(26.dp), focusedScale = 1f, border = 0.dp, onFocusChange = { focused = it }) {
                        if (current != tab.route) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        when {
                            focused -> Color.White
                            selected && open -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        },
                    )
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = when {
                    focused -> Color(0xFF0F0F0F)
                    selected -> Color.White
                    else -> Color(0xFFAAAAAA)
                }
                Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = fg, modifier = Modifier.size(26.dp))
                if (open) {
                    Spacer(Modifier.width(20.dp))
                    Text(tab.label, color = fg, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TvNavGraph(nav: NavHostController, ui: MainUiState, voiceRequest: Int) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
        composable("home") { TvHomeScreen() }
        composable("search") { TvSearchScreen(voiceRequest) }
        composable("shorts") { ShortsScreen() }
        composable("subscriptions") { TvSubscriptionsScreen() }
        composable("library") {
            TvLibraryScreen(
                onOpenDownloads = { nav.navigate("downloads") },
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onSignIn = { nav.navigate("signin") },
            )
        }
        composable("settings") { SettingsScreen(onBack = back) }
        composable("channel/{url}") { e ->
            ChannelScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back)
        }
        composable("playlist/{url}") { e ->
            PlaylistScreen(Uri.decode(e.arguments?.getString("url").orEmpty()), onBack = back, onDownload = { _, _ -> })
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
        composable("notifications") { NotificationsScreen(onBack = back) }
        composable("signin") { SignInScreen(onBack = back, onSignedIn = back) }
    }
}
