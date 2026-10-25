package com.imad.itube.ui.brand

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imad.itube.R
import com.imad.itube.appContainer
import com.imad.itube.ui.theme.Yt

/** ImadLabs brand constants (from the ImadLabs Graphics repo's brand guidelines). */
object ImadLabs {
    const val TELEGRAM_URL = "https://t.me/ImadLabs"
    val CyberMint = Color(0xFF00F5A0)
    val ElectricCobalt = Color(0xFF0066FF)
    val DeepVoid = Color(0xFF070B14)

    fun openTelegram(context: Context) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** The HexaCore symbol with the "ImadLabs" wordmark: "Imad" in the text colour, "Labs" in Cyber Mint. */
@Composable
fun ImadLabsMark(symbolSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.imadlabs_symbol), contentDescription = null, modifier = Modifier.size(symbolSize))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Yt.colors.textPrimary)) { append("Imad") }
                withStyle(SpanStyle(color = ImadLabs.CyberMint)) { append("Labs") }
            },
            fontSize = (symbolSize.value * 0.55f).sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** "Join ImadLabs on Telegram" button. */
@Composable
fun TelegramButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF229ED9))
            .clickable { ImadLabs.openTelegram(context) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Join ImadLabs on Telegram", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** Shown once per launch until the user ticks "Don't show this again". */
private object WelcomeSession { var shown = false }

@Composable
fun WelcomeHost() {
    val container = LocalContext.current.appContainer
    var show by remember { mutableStateOf(false) }
    var dontShow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Read the stored value (the settings flow starts with defaults before it loads).
        if (!WelcomeSession.shown && !container.settings.current().welcomeDismissed) {
            kotlinx.coroutines.delay(2300) // let the opening animation finish first

            WelcomeSession.shown = true
            show = true
        }
    }
    if (!show) return
    fun close() {
        if (dontShow) container.settings.update { it.copy(welcomeDismissed = true) }
        show = false
    }
    AlertDialog(
        onDismissRequest = ::close,
        containerColor = Yt.colors.surface,
        title = null,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ImadLabsMark(symbolSize = 48.dp)
                Spacer(Modifier.height(16.dp))
                Text("Welcome to iTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "iTube is made by ImadLabs. Join the Telegram channel for updates, new apps and support.",
                    textAlign = TextAlign.Center,
                    color = Yt.colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                TelegramButton()
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { dontShow = !dontShow }.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontShow,
                        onCheckedChange = { dontShow = it },
                        colors = CheckboxDefaults.colors(checkedColor = ImadLabs.CyberMint, checkmarkColor = ImadLabs.DeepVoid),
                    )
                    Text("Don't show this again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text("Close", color = Yt.colors.textPrimary) } },
    )
}
