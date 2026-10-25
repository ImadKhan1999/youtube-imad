package com.imad.itube.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imad.itube.appContainer
import com.imad.itube.ui.theme.Yt
import com.imad.itube.ui.theme.YtRed

/** "Cast to" picker, like YouTube's: nearby Chromecasts and Cast-enabled TVs. */
@Composable
fun CastDialog(onDismiss: () -> Unit) {
    val cast = LocalContext.current.appContainer.cast
    val available by cast.available.collectAsState()
    val connected by cast.connected.collectAsState()
    val deviceName by cast.deviceName.collectAsState()
    val devices by cast.devices.collectAsState()

    DisposableEffect(Unit) {
        cast.startDiscovery()
        onDispose { cast.stopDiscovery() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Yt.colors.surface,
        title = { Text(if (connected) "Casting to ${deviceName ?: "TV"}" else "Cast to") },
        text = {
            Column {
                when {
                    !available -> Text(
                        "Casting needs Google Play services (or microG) on this device.",
                        color = Yt.colors.textSecondary,
                    )
                    connected -> Text(
                        "The video plays on your TV. Use the TV or the cast notification to control it.",
                        color = Yt.colors.textSecondary,
                    )
                    devices.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = YtRed, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Looking for devices on your Wi-Fi…",
                            color = Yt.colors.textSecondary,
                        )
                    }
                    else -> devices.forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { cast.connect(d.id); onDismiss() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Tv, null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                d.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = Yt.colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (connected) {
                TextButton(onClick = { cast.disconnect(); onDismiss() }) { Text("Stop casting", color = YtRed) }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (connected) {
            {
                TextButton(onClick = { cast.castCurrent(); onDismiss() }) { Text("Cast this video") }
            }
        } else null,
    )
}
