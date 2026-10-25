package com.imad.itube.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.model.ItemPage
import com.imad.itube.data.model.PlaylistDetails
import com.imad.itube.data.model.VideoItem
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.PillButton
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.Thumbnail
import com.imad.itube.ui.theme.Yt
import com.imad.itube.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistViewModel(container: AppContainer, url: String) : ViewModel() {
    private val repo = container.repository
    private val _details = MutableStateFlow<PlaylistDetails?>(null)
    val details = _details.asStateFlow()

    val paged = Paged(
        viewModelScope,
        first = {
            val d = repo.playlist(url)
            _details.value = d
            ItemPage(d.firstPage.items, d.firstPage.next)
        },
        more = { repo.playlistMore(it) },
    ).also { it.load() }
}

@Composable
fun PlaylistScreen(url: String, onBack: () -> Unit, onDownload: (title: String, url: String) -> Unit) {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val vm: PlaylistViewModel = viewModel(key = "playlist:$url") { PlaylistViewModel(container, url) }
    val details by vm.details.collectAsState()
    val state by vm.paged.state.collectAsState()

    ScreenScaffold(topBar = { BackTopBar(details?.title.orEmpty(), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedList(
                state = state,
                onLoadMore = vm.paged::loadMore,
                onRefresh = null,
                onRetry = { vm.paged.load() },
                compact = true,
                header = {
                    val d = details
                    if (d != null) {
                        item(key = "header") {
                            Column(Modifier.padding(12.dp)) {
                                Thumbnail(d.thumbnail, 0, false, Modifier.fillMaxWidth(), rounded = true)
                                Text(d.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                                Text(
                                    Format.joinDot(d.uploaderName, if (d.videoCount >= 0) "${d.videoCount} videos" else null),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Yt.colors.textSecondary,
                                )
                                val videos = state.items.filterIsInstance<VideoItem>()
                                if (videos.isNotEmpty()) {
                                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PillButton("Play all", Modifier.weight(1f)) { actions.playQueue(videos, 0, d.title) }
                                        PillButton("Shuffle", Modifier.weight(1f), inverted = false) {
                                            actions.playQueue(videos.shuffled(), 0, d.title)
                                        }
                                    }
                                    PillButton("Download", Modifier.fillMaxWidth().padding(top = 8.dp), inverted = false) {
                                        onDownload(d.title, d.url)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
