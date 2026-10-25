package com.imad.itube.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imad.itube.BuildConfig
import com.imad.itube.appContainer
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed
import com.imad.itube.update.UpdateState
import kotlinx.coroutines.launch

/** Checks for an update once per launch and shows whatever the updater has to say. */
@Composable
fun UpdateHost() {
    val container = LocalContext.current.appContainer
    val updater = container.updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Debug builds have their own package name, so a release APK wouldn't update them.
        if (!BuildConfig.DEBUG) updater.check(manual = false)
    }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("Update available") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("iTube ${s.release.version} is out. You have ${updater.currentVersion}.")
                    val notes = s.release.body?.trim().orEmpty()
                    if (notes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { updater.install(s.release) } }) { Text("Update", color = YtRed) }
            },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text("Later") } },
            containerColor = Yt.colors.surface,
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading iTube ${s.release.version}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, color = YtRed, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            containerColor = Yt.colors.surface,
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("You're up to date") },
            text = { Text("iTube ${updater.currentVersion} is the latest version.") },
            confirmButton = { TextButton(onClick = updater::dismiss) { Text("OK") } },
            containerColor = Yt.colors.surface,
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("Update") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = updater::dismiss) { Text("OK") } },
            containerColor = Yt.colors.surface,
        )
        UpdateState.Idle, UpdateState.Checking -> {}
    }
}
