package com.imad.itube.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.Kiosk
import com.imad.itube.data.model.ItemPage
import com.imad.itube.ui.components.ChipRow
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.YtTopBar

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val library = container.library
    private val account = container.account

    /** "All" first, then one tab per category. */
    val chips: List<String> = listOf("All") + Kiosk.entries.filter { it != Kiosk.Trending }.map { it.title }

    private val feeds: List<Paged> = buildList {
        add(
            Paged(
                viewModelScope,
                first = {
                    // Signed in: your own recommendations, like the real app.
                    val personal = if (account.isSignedIn) {
                        runCatching { account.home() }.getOrNull()?.takeIf { it.items.isNotEmpty() }
                    } else {
                        null
                    }
                    if (personal != null) {
                        ItemPage(personal.items, personal.next)
                    } else {
                        // "All": recent uploads from your subscriptions mixed into the categories.
                        val subs = library.data.value.subscriptions
                        val fromSubs = if (subs.isNotEmpty()) {
                            runCatching { repo.subscriptionFeed(subs.take(30)).take(20) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        val mix = repo.homeMix()
                        ItemPage(interleave(fromSubs, mix), null)
                    }
                },
                more = { next ->
                    val page = account.home(next as String)
                    ItemPage(page.items, page.next)
                },
            )
        )
        Kiosk.entries.filter { it != Kiosk.Trending }.forEach { k ->
            add(Paged(viewModelScope, first = { repo.kiosk(k) }, more = { repo.kioskMore(it) }))
        }
    }

    fun feed(index: Int): Paged = feeds[index]

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        if (a.isEmpty()) return b
        val out = ArrayList<T>(a.size + b.size)
        val ia = a.iterator()
        b.forEachIndexed { i, item ->
            if (i % 2 == 0 && ia.hasNext()) out += ia.next()
            out += item
        }
        ia.forEachRemaining { out += it }
        return out
    }
}

@Composable
fun HomeScreen() {
    val container = LocalContext.current.appContainer
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val paged = vm.feed(selected)
    val state by paged.state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        paged.ensureLoaded()
        listState.scrollToItem(0)
    }

    ScreenScaffold(topBar = { YtTopBar() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedList(
                state = state,
                onLoadMore = paged::loadMore,
                onRefresh = { paged.load(refresh = true) },
                onRetry = { paged.load() },
                listState = listState,
                header = {
                    item(key = "chips") {
                        ChipRow(vm.chips, selected, onSelect = { selected = it })
                    }
                },
            )
        }
    }
}
