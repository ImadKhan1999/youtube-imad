package com.imad.itube.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    )

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
            }
        }
    }
}
