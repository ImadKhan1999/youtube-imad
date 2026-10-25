package com.imad.itube.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imad.itube.AppContainer
import com.imad.itube.appContainer
import com.imad.itube.data.Kiosk
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.FeedList
import com.imad.itube.ui.components.Paged
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.components.YtTopBar

private fun Kiosk.icon(): ImageVector = when (this) {
    Kiosk.Trending -> Icons.Outlined.LocalFireDepartment
    Kiosk.Music -> Icons.Outlined.MusicNote
    Kiosk.Gaming -> Icons.Outlined.SportsEsports
    Kiosk.Live -> Icons.Outlined.LiveTv
    Kiosk.Podcasts -> Icons.Outlined.Podcasts
    Kiosk.Movies -> Icons.Outlined.Movie
}

private fun Kiosk.tint(): Color = when (this) {
    Kiosk.Trending -> Color(0xFFE53935)
    Kiosk.Music -> Color(0xFF00897B)
    Kiosk.Gaming -> Color(0xFF8E24AA)
    Kiosk.Live -> Color(0xFFD81B60)
    Kiosk.Podcasts -> Color(0xFF3949AB)
    Kiosk.Movies -> Color(0xFF6D4C41)
}

@Composable
fun ExploreScreen(onOpenKiosk: (Kiosk) -> Unit) {
    ScreenScaffold(topBar = { YtTopBar() }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Kiosk.entries) { k ->
                Row(
                    Modifier
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(k.tint())
                        .clickable { onOpenKiosk(k) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(k.icon(), null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(k.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

class KioskViewModel(container: AppContainer, kiosk: Kiosk) : ViewModel() {
    val paged = Paged(
        viewModelScope,
        first = { container.repository.kiosk(kiosk) },
        more = { container.repository.kioskMore(it) },
    ).also { it.load() }
}

@Composable
fun KioskScreen(kiosk: Kiosk, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: KioskViewModel = viewModel(key = "kiosk:${kiosk.id}") { KioskViewModel(container, kiosk) }
    val state by vm.paged.state.collectAsState()
    ScreenScaffold(topBar = { BackTopBar(kiosk.title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            FeedList(
                state = state,
                onLoadMore = vm.paged::loadMore,
                onRefresh = { vm.paged.load(refresh = true) },
                onRetry = { vm.paged.load() },
            )
        }
    }
}

