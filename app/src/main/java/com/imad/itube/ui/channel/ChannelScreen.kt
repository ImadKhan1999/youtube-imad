package com.imad.itube.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.model.ChannelDetails
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.store.LibraryStore
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.ErrorBox
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.LoadingBox
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.PillButton
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.VerifiedBadge
import com.imad.itube.ui.theme.Yt
import com.imad.itube.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelViewModel(private val container: AppContainer, private val url: String) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Loaded(val channel: ChannelDetails, val tabs: List<Pair<String, Paged>>) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val ch = container.repository.channel(url)
                val supported = ch.tabs.filter { it.name in TAB_NAMES }
                    .sortedBy { TAB_NAMES.indexOf(it.name) }
                val tabs = supported.map { tab ->
                    TAB_LABELS.getValue(tab.name) to Paged(
                        viewModelScope,
                        first = { container.repository.channelTab(tab) },
                        more = { container.repository.channelTabMore(it) },
                    )
                }
                tabs.firstOrNull()?.second?.load()
                UiState.Loaded(ch, tabs)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Couldn't load this channel")
            }
        }
    }

    private companion object {
        val TAB_NAMES = listOf("videos", "shorts", "livestreams", "playlists")
        val TAB_LABELS = mapOf("videos" to "Videos", "shorts" to "Shorts", "livestreams" to "Live", "playlists" to "Playlists")
    }
}

@Composable
fun ChannelScreen(url: String, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: ChannelViewModel = viewModel(key = "channel:$url") { ChannelViewModel(container, url) }
    val state by vm.state.collectAsState()
    val title = (state as? ChannelViewModel.UiState.Loaded)?.channel?.name.orEmpty()

    ScreenScaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ChannelViewModel.UiState.Loading -> LoadingBox()
                is ChannelViewModel.UiState.Error -> ErrorBox(s.message, vm::load)
                is ChannelViewModel.UiState.Loaded -> ChannelContent(s.channel, s.tabs)
            }
        }
    }
}

@Composable
private fun ChannelContent(channel: ChannelDetails, tabs: List<Pair<String, Paged>>) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val paged = tabs.getOrNull(selected)?.second
    LaunchedEffect(paged) { paged?.ensureLoaded() }
    val pagedState = paged?.state?.collectAsState()?.value

    if (paged == null || pagedState == null) {
        Column { ChannelHeader(channel) }
        return
    }
    FeedList(
        state = pagedState,
        onLoadMore = paged::loadMore,
        onRefresh = null,
        onRetry = { paged.load() },
        compact = tabs[selected].first == "Playlists",
        header = {
            item(key = "header") { ChannelHeader(channel) }
            item(key = "tabs") {
                ScrollableTabRow(
                    selectedTabIndex = selected,
                    edgePadding = 8.dp,
                    containerColor = Yt.colors.background,
                    contentColor = Yt.colors.textPrimary,
                    indicator = { positions ->
                        if (selected < positions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[selected]),
                                color = Yt.colors.textPrimary,
                            )
                        }
                    },
                ) {
                    tabs.forEachIndexed { i, (label, _) ->
                        Tab(
                            selected = i == selected,
                            onClick = { selected = i },
                            text = { Text(label) },
                            selectedContentColor = Yt.colors.textPrimary,
                            unselectedContentColor = Yt.colors.textSecondary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ChannelHeader(channel: ChannelDetails) {
    val app = LocalContext.current.appContainer
    val library = app.library
    val data by library.data.collectAsState()
    val subscribed = data.subscriptions.any { LibraryStore.sameChannel(it.url, channel.url) }
    Column(Modifier.fillMaxWidth()) {
        if (channel.banner != null) {
            AsyncImage(
                model = channel.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(6.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Yt.colors.chip),
            )
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(channel.avatar, 72.dp, name = channel.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.verified) {
                        Spacer(Modifier.width(6.dp))
                        VerifiedBadge()
                    }
                }
                Text(
                    Format.subscribers(channel.subscriberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Yt.colors.textSecondary,
                )
            }
        }
        channel.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Yt.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        PillButton(
            if (subscribed) "Subscribed" else "Subscribe",
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            inverted = !subscribed,
        ) {
            app.toggleSubscription(
                ChannelItem(
                    url = channel.url,
                    name = channel.name,
                    avatar = channel.avatar,
                    subscriberCount = channel.subscriberCount,
                    verified = channel.verified,
                )
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
