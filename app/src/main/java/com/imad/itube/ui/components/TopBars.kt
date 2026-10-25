package com.imad.itube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imad.itube.ui.theme.Yt

/** A Scaffold that leaves bottom insets to the app's bottom bar. */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        containerColor = Yt.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

/** The home-style bar: logo on the left, cast / notifications / search on the right. */
@Composable
fun YtTopBar() {
    val actions = LocalAppActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Yt.colors.background)
            .statusBarsPadding()
            .height(52.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ITubeLogo()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { actions.toast("Casting isn't supported yet") }) {
            Icon(Icons.Outlined.Cast, contentDescription = "Cast")
        }
        IconButton(onClick = { actions.toast("No new notifications") }) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
        }
        IconButton(onClick = actions.openSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search")
        }
    }
}

@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Yt.colors.background)
            .statusBarsPadding()
            .height(52.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
