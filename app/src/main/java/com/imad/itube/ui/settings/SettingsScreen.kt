package com.imad.itube.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imad.itube.BuildConfig
import com.imad.itube.appContainer
import com.imad.itube.data.store.ThemeMode
import com.imad.itube.ui.components.BackTopBar
import com.imad.itube.ui.components.ScreenScaffold
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.ui.watch.SPEEDS
import com.imad.itube.ui.watch.speedLabel

private val QUALITIES = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)

private data class Choice<T>(val title: String, val options: List<Pair<String, T>>, val current: T, val onPick: (T) -> Unit)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val store = container.settings
    val s by store.settings.collectAsState()
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            store.update { it.copy(downloadFolder = uri.toString()) }
        }
    }

    ScreenScaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Video quality preferences")
            ValueRow("On Wi-Fi", "${s.defaultQuality}p") {
                choice = Choice("Video quality on Wi-Fi", QUALITIES.map { "${it}p" to it }, s.defaultQuality) { q ->
                    store.update { it.copy(defaultQuality = q) }
                }
            }
            ValueRow("On mobile networks", "${s.mobileDataQuality}p") {
                choice = Choice("Video quality on mobile networks", QUALITIES.map { "${it}p" to it }, s.mobileDataQuality) { q ->
                    store.update { it.copy(mobileDataQuality = q) }
                }
            }
            ValueRow("Default playback speed", speedLabel(s.defaultSpeed)) {
                choice = Choice("Default playback speed", SPEEDS.map { speedLabel(it) to it }, s.defaultSpeed) { v ->
                    store.update { it.copy(defaultSpeed = v) }
                }
            }
            ValueRow("Double-tap to seek", "${s.seekSeconds} seconds") {
                choice = Choice("Double-tap to seek", listOf(5, 10, 15, 20, 30).map { "$it seconds" to it }, s.seekSeconds) { v ->
                    store.update { it.copy(seekSeconds = v) }
                }
            }

            Section("Playback")
            SwitchRow("Background play", "Keep playing audio when you leave the app or lock the screen", s.backgroundPlay) { v ->
                store.update { it.copy(backgroundPlay = v) }
            }
            SwitchRow("Picture-in-picture", "Shrink the video into a popup when you go home", s.pipOnHome) { v ->
                store.update { it.copy(pipOnHome = v) }
            }
            SwitchRow("Autoplay", "Play the next suggested video when one ends", s.autoplay) { v ->
                store.update { it.copy(autoplay = v) }
            }

            SwitchRow("SponsorBlock", "Skip sponsor segments, intros and self-promotion", s.sponsorBlock) { v ->
                store.update { it.copy(sponsorBlock = v) }
            }

            Section("Notifications")
            SwitchRow("New uploads", "Tell me when channels I'm subscribed to post a video", s.uploadAlerts) { v ->
                store.update { it.copy(uploadAlerts = v) }
            }

            Section("Downloads")
            ValueRow("Default download quality", if (s.downloadQuality == 0) "Audio only" else "${s.downloadQuality}p") {
                choice = Choice(
                    "Default download quality",
                    listOf(1080, 720, 480, 360).map { "${it}p" to it } + ("Audio only" to 0),
                    s.downloadQuality,
                ) { v -> store.update { it.copy(downloadQuality = v) } }
            }
            SwitchRow("Download over Wi-Fi only", "Downloads wait until you're on Wi-Fi", s.downloadWifiOnly) { v ->
                store.update { it.copy(downloadWifiOnly = v) }
            }
            ValueRow("Download folder", folderLabel(s.downloadFolder)) { pickFolder.launch(null) }
            if (s.downloadFolder != null) {
                ValueRow("Use the default folder", "Movies/iTube and Music/iTube") {
                    store.update { it.copy(downloadFolder = null) }
                }
            }

            Section("General")
            ValueRow("Appearance", s.theme.label()) {
                choice = Choice("Appearance", ThemeMode.entries.map { it.label() to it }, s.theme) { v ->
                    store.update { it.copy(theme = v) }
                }
            }
            SwitchRow("Restricted mode", "Hide videos that may be inappropriate", s.restrictedMode) { v ->
                store.update { it.copy(restrictedMode = v) }
            }

            Section("About")
            ValueRow("iTube", "Version ${BuildConfig.VERSION_NAME}") {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    (choice as Choice<Any?>?)?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            confirmButton = { TextButton(onClick = { choice = null }) { Text("Cancel") } },
            title = { Text(c.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    c.options.forEach { (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { c.onPick(value); choice = null }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == c.current,
                                onClick = { c.onPick(value); choice = null },
                                colors = RadioButtonDefaults.colors(selectedColor = YtRed),
                            )
                            Text(label)
                        }
                    }
                }
            },
            containerColor = Yt.colors.surface,
        )
    }
}

/** "Download/iTube" style name for a picked folder. */
private fun folderLabel(tree: String?): String {
    tree ?: return "Movies/iTube and Music/iTube"
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull() ?: return "Custom folder"
    return id.substringAfter(':').ifBlank { "Internal storage" }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> "Use device theme"
    ThemeMode.Light -> "Light theme"
    ThemeMode.Dark -> "Dark theme"
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Yt.colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = YtRed))
    }
}
