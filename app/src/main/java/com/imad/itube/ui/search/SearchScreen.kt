package com.imad.itube.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.SearchFilter
import com.imad.itube.ui.components.ChipRow
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.PagedState
import com.imad.itube.ui.theme.Yt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val library = container.library

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The query whose results are showing; null while the user is typing. */
    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.All)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private var paged: Paged? = null
    private val _results = MutableStateFlow<Paged?>(null)
    val results: StateFlow<Paged?> = _results.asStateFlow()

    val suggestions: StateFlow<List<String>> = _query
        .debounce(200)
        .distinctUntilChanged()
        .mapLatest { q -> if (q.isBlank()) emptyList() else runCatching { repo.suggestions(q) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        _query.value = q
        _submitted.value = null
    }

    fun submit(q: String = _query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        _query.value = text
        _submitted.value = text
        library.addSearch(text)
        runSearch()
    }

    fun setFilter(f: SearchFilter) {
        _filter.value = f
        runSearch()
    }

    fun editAgain() {
        _submitted.value = null
    }

    private fun runSearch() {
        val q = _submitted.value ?: return
        val f = _filter.value
        val p = Paged(viewModelScope, first = { repo.search(q, f) }, more = { repo.searchMore(it) })
        paged = p
        _results.value = p
        p.load()
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsState()
    val submitted by vm.submitted.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val filter by vm.filter.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.library.data.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().background(Yt.colors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Yt.colors.chip)
                    .clickable(enabled = submitted != null) { vm.editAgain() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search iTube", color = Yt.colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = vm::onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Yt.colors.textPrimary),
                        cursorBrush = SolidColor(Yt.colors.textPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            vm.submit()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(20.dp).clickable { vm.onQueryChange(""); focus.requestFocus() },
                    )
                }
            }
        }

        val current = results
        if (submitted != null && current != null) {
            val state by current.state.collectAsState()
            SearchResults(state, filter, vm::setFilter, current)
        } else {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            val rows: List<Pair<String, Boolean>> = if (query.isBlank()) {
                history.searchHistory.map { it to true }
            } else {
                suggestions.map { it to false }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.first + it.second }) { (text, isHistory) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { keyboard?.hide(); vm.submit(text) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isHistory) Icons.Outlined.History else Icons.Outlined.Search,
                            null,
                            tint = Yt.colors.textSecondary,
                        )
                        Spacer(Modifier.width(20.dp))
                        Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(
                            if (isHistory) Icons.Filled.Close else Icons.Outlined.NorthWest,
                            contentDescription = if (isHistory) "Remove" else "Use",
                            tint = Yt.colors.textSecondary,
                            modifier = Modifier.size(20.dp).clickable {
                                if (isHistory) vm.library.removeSearch(text) else vm.onQueryChange("$text ")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(state: PagedState, filter: SearchFilter, onFilter: (SearchFilter) -> Unit, paged: Paged) {
    FeedList(
        state = state,
        onLoadMore = paged::loadMore,
        onRefresh = null,
        onRetry = { paged.load() },
        emptyText = "No results",
        header = {
            item(key = "filters") {
                ChipRow(
                    SearchFilter.entries.map { it.label },
                    SearchFilter.entries.indexOf(filter),
                    onSelect = { onFilter(SearchFilter.entries[it]) },
                )
            }
        },
    )
}
