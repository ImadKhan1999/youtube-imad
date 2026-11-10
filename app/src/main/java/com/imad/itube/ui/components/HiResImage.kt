package com.imad.itube.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.imad.itube.util.Thumbs

/**
 * A video thumbnail loaded at the sharpest size YouTube has for it (hq720 / maxres), falling
 * back to smaller ones when a size doesn't exist. Non-YouTube URLs load as they are.
 */
@Composable
fun HiResImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val candidates = remember(url) { Thumbs.candidates(url) }
    val index = remember(url) { mutableIntStateOf(Thumbs.knownIndex(url).coerceIn(0, candidates.lastIndex)) }
    val i = index.intValue
    AsyncImage(
        model = candidates[i],
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onSuccess = { Thumbs.remember(url, i) },
        onError = {
            if (index.intValue == i && i < candidates.lastIndex) index.intValue = i + 1
        },
    )
}
