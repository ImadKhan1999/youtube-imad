package com.imad.itube.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.imad.itube.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val page: String? = null,
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
    )

    val version: String get() = tag.removePrefix("v")
    val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks GitHub Releases for a newer iTube and installs it. */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Version this build was released as. Debug builds carry a "-debug" suffix. */
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    private var dismissed: String? = null

    /** Looks for a newer release. [manual] reports "up to date" and errors instead of staying quiet. */
    suspend fun check(manual: Boolean) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = runCatching { latest() }
        _state.value = result.fold(
            onSuccess = { r ->
                when {
                    r == null || r.apk == null || !isNewer(r.version, currentVersion) ->
                        if (manual) UpdateState.UpToDate else UpdateState.Idle
                    !manual && r.version == dismissed -> UpdateState.Idle
                    else -> UpdateState.Available(r)
                }
            },
            onFailure = { e -> if (manual) UpdateState.Failed(e.message ?: "Couldn't check for updates") else UpdateState.Idle },
        )
    }

    fun dismiss() {
        (_state.value as? UpdateState.Available)?.let { dismissed = it.release.version }
        _state.value = UpdateState.Idle
    }

    private suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return@use null
            if (!res.isSuccessful) throw IOException("GitHub returned HTTP ${res.code}")
            json.decodeFromString<Release>(res.body?.string().orEmpty())
        }
    }

    /** Downloads the APK and hands it to the system installer. */
    suspend fun install(release: Release) {
        val asset = release.apk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = UpdateState.Available(release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val file = runCatching { download(release, asset) }.getOrElse { e ->
            _state.value = UpdateState.Failed("Download failed: ${e.message}")
            return
        }
        _state.value = UpdateState.Idle
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun download(release: Release, asset: Release.Asset): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "iTube-${release.version}.apk")
        http.newCall(Request.Builder().url(asset.url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: asset.size
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(release, done.toFloat() / total)
                    }
                }
            }
        }
        out
    }

    companion object {
        const val REPO = "ImadKhan1999/youtube-imad"

        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
