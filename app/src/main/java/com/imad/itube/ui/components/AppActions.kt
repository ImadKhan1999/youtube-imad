package com.imad.itube.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.imad.itube.data.model.VideoItem

/** Navigation and library actions every list item can trigger. Provided by MainScreen. */
class AppActions(
    val openVideo: (VideoItem) -> Unit,
    val playQueue: (items: List<VideoItem>, index: Int, title: String?) -> Unit,
    val openChannel: (String) -> Unit,
    val openPlaylist: (String) -> Unit,
    val openSearch: () -> Unit,
    val openNotifications: () -> Unit,
    val openCast: () -> Unit,
    val toggleWatchLater: (VideoItem) -> Unit,
    val isInWatchLater: (String) -> Boolean,
    val share: (title: String, url: String) -> Unit,
    val download: (VideoItem) -> Unit,
    val toast: (String) -> Unit,
)

val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
