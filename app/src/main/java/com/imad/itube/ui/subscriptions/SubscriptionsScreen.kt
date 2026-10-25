package com.imad.itube.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.model.ItemPage
import com.imad.itube.ui.components.Avatar
import com.imad.itube.ui.components.EmptyBox
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.LocalAppActions
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.YtTopBar
import com.imad.itube.ui.theme.Yt

class SubscriptionsViewModel(container: AppContainer) : ViewModel() {
    private val library = container.library
    val paged = Paged(
        viewModelScope,
        first = {
            val subs = library.data.value.subscriptions
            ItemPage(container.repository.subscriptionFeed(subs), null)
        },
        more = { ItemPage(emptyList(), null) },
    )
}

@Composable
fun SubscriptionsScreen() {
    val container = LocalContext.current.appContainer
    val actions = LocalAppActions.current
    val vm: SubscriptionsViewModel = viewModel { SubscriptionsViewModel(container) }
    val library by container.library.data.collectAsState()
    val state by vm.paged.state.collectAsState()
    val subsKey = library.subscriptions.joinToString { it.url }
    LaunchedEffect(subsKey) { if (library.subscriptions.isNotEmpty()) vm.paged.load() }

    ScreenScaffold(topBar = { YtTopBar() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (library.subscriptions.isEmpty()) {
                EmptyBox(
                    "Don't miss new videos",
                    "Subscribe to channels and their latest uploads will show up here.",
                )
                return@Column
            }
            FeedList(
                state = state,
                onLoadMore = {},
                onRefresh = { vm.paged.load(refresh = true) },
                onRetry = { vm.paged.load() },
                header = {
                    item(key = "channels") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                            items(library.subscriptions, key = { it.url }) { ch ->
                                Column(
                                    Modifier
                                        .width(72.dp)
                                        .clickable { actions.openChannel(ch.url) }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Avatar(ch.avatar, 56.dp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        ch.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Yt.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
