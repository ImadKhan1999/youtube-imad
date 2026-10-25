package com.imad.itube.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { System, Light, Dark }

data class Settings(
    /** Preferred video height for "Auto" quality, e.g. 720. */
    val defaultQuality: Int = 720,
    /** Preferred quality on mobile data. */
    val mobileDataQuality: Int = 480,
    val backgroundPlay: Boolean = true,
    val pipOnHome: Boolean = true,
    val autoplay: Boolean = true,
    val restrictedMode: Boolean = false,
    val defaultSpeed: Float = 1f,
    val theme: ThemeMode = ThemeMode.System,
    val seekSeconds: Int = 10,
    val sponsorBlock: Boolean = true,
    val captionsLanguage: String? = null,
    val checkUpdates: Boolean = true,
    val downloadQuality: Int = 720,
    /** Only download on Wi-Fi (unmetered networks); queued items wait otherwise. */
    val downloadWifiOnly: Boolean = false,
    /** A folder picked with the system file picker (tree URI); null = Movies/iTube and Music/iTube. */
    val downloadFolder: String? = null,
    /** Notify about new uploads from subscriptions. */
    val uploadAlerts: Boolean = true,
    /** Uploads before this time (epoch ms) count as read in Notifications. */
    val notificationsSeenAt: Long = 0,
    /** Uploads before this time already got a system notification. */
    val notifiedAt: Long = 0,
    /** The user ticked "Don't show this again" on the welcome / Telegram popup. */
    val welcomeDismissed: Boolean = false,
)

class SettingsStore(private val context: Context, private val scope: CoroutineScope) {

    private object Keys {
        val quality = intPreferencesKey("default_quality")
        val mobileQuality = intPreferencesKey("mobile_quality")
        val background = booleanPreferencesKey("background_play")
        val pip = booleanPreferencesKey("pip_on_home")
        val autoplay = booleanPreferencesKey("autoplay")
        val restricted = booleanPreferencesKey("restricted_mode")
        val speed = floatPreferencesKey("default_speed")
        val theme = stringPreferencesKey("theme")
        val seek = intPreferencesKey("seek_seconds")
        val sponsor = booleanPreferencesKey("sponsorblock")
        val captions = stringPreferencesKey("captions_language")
        val updates = booleanPreferencesKey("check_updates")
        val dlQuality = intPreferencesKey("download_quality")
        val dlWifi = booleanPreferencesKey("download_wifi_only")
        val dlFolder = stringPreferencesKey("download_folder")
        val uploadAlerts = booleanPreferencesKey("upload_alerts")
        val seenAt = longPreferencesKey("notifications_seen_at")
        val notifiedAt = longPreferencesKey("notified_at")
        val welcomeDismissed = booleanPreferencesKey("welcome_dismissed")
    }

    val settings: StateFlow<Settings> = context.settingsDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    private fun Preferences.toSettings() = Settings(
        defaultQuality = this[Keys.quality] ?: 720,
        mobileDataQuality = this[Keys.mobileQuality] ?: 480,
        backgroundPlay = this[Keys.background] ?: true,
        pipOnHome = this[Keys.pip] ?: true,
        autoplay = this[Keys.autoplay] ?: true,
        restrictedMode = this[Keys.restricted] ?: false,
        defaultSpeed = this[Keys.speed] ?: 1f,
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System,
        seekSeconds = this[Keys.seek] ?: 10,
        sponsorBlock = this[Keys.sponsor] ?: true,
        captionsLanguage = this[Keys.captions]?.takeIf { it.isNotEmpty() },
        checkUpdates = this[Keys.updates] ?: true,
        downloadQuality = this[Keys.dlQuality] ?: 720,
        downloadWifiOnly = this[Keys.dlWifi] ?: false,
        downloadFolder = this[Keys.dlFolder]?.takeIf { it.isNotEmpty() },
        uploadAlerts = this[Keys.uploadAlerts] ?: true,
        notificationsSeenAt = this[Keys.seenAt] ?: 0,
        notifiedAt = this[Keys.notifiedAt] ?: 0,
        welcomeDismissed = this[Keys.welcomeDismissed] ?: false,
    )

    /** The stored settings, read straight from disk (the StateFlow starts with defaults). */
    suspend fun current(): Settings = context.settingsDataStore.data.first().toSettings()

    fun update(transform: (Settings) -> Settings) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                val s = transform(prefs.toSettings())
                prefs[Keys.quality] = s.defaultQuality
                prefs[Keys.mobileQuality] = s.mobileDataQuality
                prefs[Keys.background] = s.backgroundPlay
                prefs[Keys.pip] = s.pipOnHome
                prefs[Keys.autoplay] = s.autoplay
                prefs[Keys.restricted] = s.restrictedMode
                prefs[Keys.speed] = s.defaultSpeed
                prefs[Keys.theme] = s.theme.name
                prefs[Keys.seek] = s.seekSeconds
                prefs[Keys.sponsor] = s.sponsorBlock
                prefs[Keys.captions] = s.captionsLanguage.orEmpty()
                prefs[Keys.updates] = s.checkUpdates
                prefs[Keys.dlQuality] = s.downloadQuality
                prefs[Keys.dlWifi] = s.downloadWifiOnly
                prefs[Keys.dlFolder] = s.downloadFolder.orEmpty()
                prefs[Keys.uploadAlerts] = s.uploadAlerts
                prefs[Keys.seenAt] = s.notificationsSeenAt
                prefs[Keys.notifiedAt] = s.notifiedAt
                prefs[Keys.welcomeDismissed] = s.welcomeDismissed
            }
        }
    }
}
