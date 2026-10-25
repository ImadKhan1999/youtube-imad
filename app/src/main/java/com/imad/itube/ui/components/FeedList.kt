package com.imad.itube.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imad.itube.ui.theme.YtRed

/**
 * An infinite, pull-to-refresh list of feed items. [header] is placed above the items
 * (chips, channel header, ...).
 */
@Composable
fun FeedList(
    state: PagedState,
    onLoadMore: () -> Unit,
    onRefresh: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    emptyText: String = "Nothing here yet",
    header: (LazyListScope.() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = { BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets, foldables and TVs get a grid of cards, like YouTube on big screens.
        val columns = if (compact) 1 else (maxWidth / 340.dp).toInt().coerceIn(1, 4)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            header?.invoke(this)
            when {
                state.loading && state.items.isEmpty() -> item { LoadingBox(Modifier.padding(top = 120.dp)) }
                state.error != null && state.items.isEmpty() -> item {
                    ErrorBox(state.error.orEmpty(), onRetry, Modifier.padding(top = 40.dp))
                }
                state.loaded && state.items.isEmpty() -> item { EmptyBox(emptyText, modifier = Modifier.padding(top = 60.dp)) }
                else -> {
                    if (columns == 1) {
                        items(state.items, key = { it.url }) { FeedItemView(it, compact) }
                    } else {
                        val rows = state.items.chunked(columns)
                        items(rows, key = { it.first().url }) { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                row.forEach { Box(Modifier.weight(1f).padding(horizontal = 4.dp)) { FeedItemView(it, compact) } }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = YtRed, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                }
            }
        }
    } }

    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.next) {
        if (shouldLoadMore && state.next != null) onLoadMore()
    }

    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
            content()
        }
    } else {
        Box(modifier.fillMaxSize()) { content() }
    }
}
