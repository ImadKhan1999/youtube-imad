package com.imad.itube.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.appContainer
import com.imad.itube.data.SearchFilter
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.FeedItem
import com.imad.itube.data.model.PlaylistItem
import com.imad.itube.data.model.VideoItem
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.HiResImage
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.PagedState
import com.imad.itube.ui.home.HomeViewModel
import com.imad.itube.ui.search.SearchViewModel
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.util.Format
import kotlinx.coroutines.launch

internal val TvBg = Color(0xFF0F0F0F)
internal val TvSurface = Color(0xFF272727)
internal val TvTextSecondary = Color(0xFFAAAAAA)

/** Space on the left that the collapsed navigation rail takes. */
internal val RailCollapsed = 88.dp

// ---- Cards --------------------------------------------------------------------------------

@Composable
fun TvFeedCard(item: FeedItem, modifier: Modifier = Modifier) {
    when (item) {
        is VideoItem -> TvVideoCard(item, modifier)
        is ChannelItem -> TvChannelCard(item, modifier)
        is PlaylistItem -> TvPlaylistCard(item, modifier)
    }
}

/** YouTube TV's video tile: a 16:9 thumbnail that lifts and gets a white frame when focused. */
@Composable
fun TvVideoCard(video: VideoItem, modifier: Modifier = Modifier) {
    val actions = LocalAppActions.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.tvClickable(
            shape = RoundedCornerShape(12.dp),
            border = 0.dp,
            onFocusChange = { focused = it },
            onLongClick = {
                actions.toggleWatchLater(video)
                actions.toast(if (actions.isInWatchLater(video.url)) "Saved to Watch later" else "Removed from Watch later")
            },
        ) { actions.openVideo(video) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(TvSurface)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
        ) {
            HiResImage(video.thumbnail, Modifier.fillMaxSize())
            val badge = when {
                video.isLive -> "LIVE"
                video.duration > 0 -> Format.duration(video.duration)
                else -> null
            }
            if (badge != null) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.isLive) YtRed else Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            video.title,
            color = if (focused) Color.White else Color(0xFFE0E0E0),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            video.channelName.orEmpty(),
            color = TvTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            Format.joinDot(
                if (video.isLive) Format.watching(video.viewCount) else Format.views(video.viewCount),
                if (video.isLive) null else Format.ago(video.uploadedAt, video.uploadedText),
            ),
            color = TvTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun TvChannelCard(channel: ChannelItem, modifier: Modifier = Modifier) {
    val actions = LocalAppActions.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .tvClickable(border = 0.dp, onFocusChange = { focused = it }) { actions.openChannel(channel.url) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .padding(4.dp),
            ) { Avatar(channel.avatar, 104.dp, name = channel.name, channelUrl = channel.url) }
        }
        Spacer(Modifier.height(10.dp))
        Text(channel.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (channel.subscriberCount >= 0) {
            Text(Format.subscribers(channel.subscriberCount), color = TvTextSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun TvPlaylistCard(playlist: PlaylistItem, modifier: Modifier = Modifier) {
    val actions = LocalAppActions.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.tvClickable(border = 0.dp, onFocusChange = { focused = it }) { actions.openPlaylist(playlist.url) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(TvSurface)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
        ) {
            HiResImage(playlist.thumbnail, Modifier.fillMaxSize())
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                if (playlist.videoCount >= 0) Text(" ${playlist.videoCount} videos", color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(playlist.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(playlist.uploaderName.orEmpty(), color = TvTextSecondary, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
    }
}

/** A rounded pill (topic chips, filters, buttons) that turns white when focused, like YouTube TV. */
@Composable
fun TvPill(
    text: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color(0xFF3F3F3F)
        else -> TvSurface
    }
    val fg = if (focused) Color(0xFF0F0F0F) else Color.White
    Row(
        modifier
            .tvClickable(shape = RoundedCornerShape(50), focusedScale = 1.08f, border = 0.dp, onFocusChange = { focused = it }, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (selected && !focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ---- Grid ---------------------------------------------------------------------------------

/**
 * A grid of cards (four across, like YouTube TV) that loads more as focus nears the end.
 * The first card takes focus once the first page arrives.
 */
@Composable
fun TvFeedGrid(
    state: PagedState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    emptyText: String = "Nothing here yet",
    focusFirst: Boolean = true,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(!focusFirst) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 40.dp, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header?.invoke(this)
        when {
            state.loading && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { TvLoading() }
            state.error != null && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                TvMessage(state.error.orEmpty(), "Retry", onRetry)
            }
            state.loaded && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { TvMessage(emptyText) }
            else -> {
                itemsIndexed(state.items, key = { _, it -> it.url }) { i, item ->
                    TvFeedCard(item, if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
                if (state.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { TvLoading(Modifier.height(80.dp)) }
            }
        }
    }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (!focusedOnce && state.items.isNotEmpty()) {
            focusedOnce = true
            runCatching { first.requestFocus() }
        }
    }
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, state.next) { if (nearEnd && state.next != null) onLoadMore() }
}

@Composable
fun TvLoading(modifier: Modifier = Modifier.height(320.dp)) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = YtRed, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
    }
}

@Composable
fun TvMessage(text: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White, fontSize = 18.sp)
        if (button != null) {
            Spacer(Modifier.height(16.dp))
            TvPill(button, onClick = onClick)
        }
    }
}

@Composable
fun TvScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 24.dp, top = 28.dp, bottom = 12.dp),
    )
}

/** A titled horizontal row of cards. */
@Composable
fun TvShelf(title: String, items: List<FeedItem>, modifier: Modifier = Modifier, firstFocus: FocusRequester? = null) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 24.dp, end = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.url }) { i, item ->
                TvFeedCard(item, Modifier.width(280.dp).then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier))
            }
        }
    }
}

// ---- Screens ------------------------------------------------------------------------------

/** Home: topic chips along the top and a grid of videos, as on YouTube TV. */
@Composable
fun TvHomeScreen() {
    val container = LocalContext.current.appContainer
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val paged = vm.feed(selected)
    val state by paged.state.collectAsState()
    val grid = rememberLazyGridState()
    LaunchedEffect(selected) {
        paged.ensureLoaded()
        grid.scrollToItem(0)
    }
    TvFeedGrid(
        state = state,
        onLoadMore = paged::loadMore,
        onRetry = { paged.load() },
        gridState = grid,
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(vm.chips) { i, chip ->
                        TvPill(chip, selected = i == selected) { selected = i }
                    }
                }
            }
        },
    )
}

@Composable
fun TvSubscriptionsScreen() {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val feed = container.subscriptionFeed
    val library by container.library.data.collectAsState()
    val feedState by feed.state.collectAsState()
    val subsKey = library.subscriptions.joinToString { it.url }
    LaunchedEffect(subsKey) { if (library.subscriptions.isNotEmpty()) feed.load(library.subscriptions) }

    if (library.subscriptions.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TvScreenTitle("Subscriptions")
            TvMessage("Subscribe to channels and their latest uploads will show up here.")
        }
        return
    }
    val state = PagedState(
        items = feedState.items,
        loading = feedState.loading,
        refreshing = feedState.refreshing,
        error = feedState.error,
        loaded = feedState.loaded,
    )
    TvFeedGrid(
        state = state,
        onLoadMore = {},
        onRetry = { feed.load(library.subscriptions, force = true) },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") { TvScreenTitle("Subscriptions", Modifier.padding(start = 0.dp)) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "channels") {
                LazyRow(
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(library.subscriptions, key = { it.url }) { ch ->
                        var focused by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .width(96.dp)
                                .tvClickable(border = 0.dp, onFocusChange = { focused = it }) { actions.openChannel(ch.url) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .padding(3.dp),
                            ) { Avatar(ch.avatar, 64.dp, name = ch.name, channelUrl = ch.url) }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                ch.name,
                                color = if (focused) Color.White else TvTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Library: account, then shelves for history, Watch later and liked videos. */
@Composable
fun TvLibraryScreen(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val data by container.library.data.collectAsState()
    val account by container.accountStore.account.collectAsState()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "account") {
            Row(Modifier.padding(start = 24.dp, top = 28.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                val a = account
                if (a != null) {
                    Avatar(a.avatar, 72.dp, name = a.name ?: "Y")
                } else {
                    Icon(Icons.Outlined.AccountCircle, null, tint = TvTextSecondary, modifier = Modifier.size(72.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(a?.name ?: "Library", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (a != null) a.handle ?: "Signed in with Google" else "Your library is stored on this device",
                        color = TvTextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
            Row(
                Modifier.padding(start = 24.dp, top = 20.dp, end = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (account == null) {
                    TvPill("Sign in", icon = Icons.AutoMirrored.Outlined.Login, modifier = Modifier.focusRequester(first), onClick = onSignIn)
                } else {
                    TvPill(if (syncing) "Syncing…" else "Sync subscriptions", icon = Icons.Outlined.Sync, modifier = Modifier.focusRequester(first)) {
                        if (syncing) return@TvPill
                        syncing = true
                        scope.launch {
                            val r = container.syncSubscriptions()
                            syncing = false
                            actions.toast(r.fold({ "Synced $it subscriptions" }, { "Couldn't sync: ${it.message}" }))
                        }
                    }
                    TvPill("Sign out", icon = Icons.AutoMirrored.Outlined.Logout) {
                        container.signOut()
                        actions.toast("Signed out")
                    }
                }
                TvPill("Downloads", icon = Icons.Outlined.Download, onClick = onOpenDownloads)
                TvPill("Settings", icon = Icons.Outlined.Settings, onClick = onOpenSettings)
            }
        }
        item(key = "history") { TvShelf("History", data.history.take(30)) }
        item(key = "later") { TvShelf("Watch later", data.watchLater) }
        item(key = "liked") { TvShelf("Liked videos", data.liked) }
        if (data.history.isEmpty() && data.watchLater.isEmpty() && data.liked.isEmpty()) {
            item(key = "empty") { TvMessage("Videos you watch, save or like will show up here.") }
        }
    }
}

/** Search: a text box (the TV's own keyboard opens on OK), suggestions and a grid of results. */
@Composable
fun TvSearchScreen() {
    val container = LocalContext.current.appContainer
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsState()
    val submitted by vm.submitted.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val filter by vm.filter.collectAsState()
    val results by vm.results.collectAsState()
    val library by vm.library.data.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val boxFocus = remember { FocusRequester() }
    var boxFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (submitted == null) runCatching { boxFocus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .padding(start = 24.dp, end = 40.dp, top = 28.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (boxFocused) Color.White else TvSurface)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (boxFocused) Color(0xFF0F0F0F) else Color.White
            Icon(Icons.Outlined.Search, null, tint = fg)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search YouTube", color = if (boxFocused) Color(0xFF606060) else TvTextSecondary, fontSize = 18.sp)
                BasicTextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 18.sp),
                    cursorBrush = SolidColor(fg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(boxFocus)
                        .onFocusChanged { boxFocused = it.isFocused },
                )
            }
        }

        // Suggestions while typing, recent searches when the box is empty.
        val words = if (query.isBlank()) library.searchHistory.take(12) else suggestions.take(12)
        if (submitted == null || words.isNotEmpty() && submitted != query) {
            LazyRow(
                contentPadding = PaddingValues(start = 24.dp, end = 40.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(words) { w -> TvPill(w, icon = Icons.Outlined.Search) { keyboard?.hide(); vm.submit(w) } }
            }
        }

        val current = results
        if (submitted != null && current != null) {
            val state by current.state.collectAsState()
            TvFeedGrid(
                state = state,
                onLoadMore = current::loadMore,
                onRetry = { current.load() },
                emptyText = "No results",
                focusFirst = false,
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                        LazyRow(
                            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(SearchFilter.entries) { f -> TvPill(f.label, selected = f == filter) { vm.setFilter(f) } }
                        }
                    }
                },
            )
        }
    }
}
