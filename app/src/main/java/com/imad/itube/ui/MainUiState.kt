package com.imad.itube.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A link opened from outside the app that should navigate somewhere. */
sealed interface DeepLink {
    data class Channel(val url: String) : DeepLink
    data class Playlist(val url: String) : DeepLink
    data class Search(val query: String) : DeepLink
}

/** Activity-scoped UI state shared between Compose and [com.imad.itube.MainActivity]. */
class MainUiState : ViewModel() {
    private val _watchExpanded = MutableStateFlow(false)
    val watchExpanded: StateFlow<Boolean> = _watchExpanded.asStateFlow()

    private val _deepLink = MutableStateFlow<DeepLink?>(null)
    val deepLink: StateFlow<DeepLink?> = _deepLink.asStateFlow()

    fun expand() { _watchExpanded.value = true }
    fun collapse() { _watchExpanded.value = false }

    fun open(link: DeepLink) { _deepLink.value = link }
    fun consumeDeepLink() { _deepLink.value = null }
}
