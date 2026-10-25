package com.imad.itube.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.imad.itube.player.PlayerController
import com.imad.itube.player.WatchState
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed

private enum class Page { Main, Quality, Speed, Audio }

val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

fun speedLabel(s: Float): String = if (s == 1f) "Normal" else "${s.toString().removeSuffix(".0")}x"

/** The gear-icon menu: Quality, Playback speed, Loop, Audio track. */
@Composable
fun PlayerSettingsSheet(state: WatchState, controller: PlayerController, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(Page.Main) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Yt.colors.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            when (page) {
                Page.Main -> {
                    val q = when {
                        state.isLive -> "Auto"
                        state.selectedQuality == null -> "Auto" + (state.autoQuality?.let { " (${it.label})" } ?: "")
                        else -> state.selectedQuality.label
                    }
                    SettingRow(Icons.Outlined.Tune, "Quality", q) { if (!state.isLive) page = Page.Quality }
                    SettingRow(Icons.Outlined.SlowMotionVideo, "Playback speed", speedLabel(state.speed)) { page = Page.Speed }
                    if (state.audioTracks.size > 1) {
                        val current = state.selectedAudioTrack ?: state.audioTracks.first()
                        SettingRow(Icons.Outlined.Audiotrack, "Audio track", current.label) { page = Page.Audio }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { controller.setLoop(!state.loop) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Repeat, null)
                        Spacer(Modifier.width(20.dp))
                        Text("Loop video", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.loop,
                            onCheckedChange = { controller.setLoop(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = YtRed),
                        )
                    }
                }

                Page.Quality -> {
                    Header("Quality for current video") { page = Page.Main }
                    OptionRow(
                        "Auto" + (state.autoQuality?.let { " (${it.label})" } ?: ""),
                        "Adjusts to give you the best experience",
                        selected = state.selectedQuality == null,
                    ) { controller.selectQuality(null); onDismiss() }
                    state.qualities.forEach { option ->
                        OptionRow(
                            option.label + if (option.height >= 720) "  HD" else "",
                            null,
                            selected = state.selectedQuality?.label == option.label,
                        ) { controller.selectQuality(option); onDismiss() }
                    }
                }

                Page.Speed -> {
                    Header("Playback speed") { page = Page.Main }
                    SPEEDS.forEach { s ->
                        OptionRow(speedLabel(s), null, selected = state.speed == s) {
                            controller.setSpeed(s); onDismiss()
                        }
                    }
                }

                Page.Audio -> {
                    Header("Audio track") { page = Page.Main }
                    val current = state.selectedAudioTrack ?: state.audioTracks.firstOrNull()
                    state.audioTracks.forEach { t ->
                        OptionRow(t.label, null, selected = current?.id == t.id) {
                            controller.selectAudioTrack(t); onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text("  ·  $value", style = MaterialTheme.typography.bodyLarge, color = Yt.colors.textSecondary)
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box24 { if (selected) Icon(Icons.Filled.Check, null) }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun Box24(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
}
