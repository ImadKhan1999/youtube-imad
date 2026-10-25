package com.imad.itube.account

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The signed-in Google account. [cookies] is the youtube.com cookie header captured at sign-in. */
@Serializable
data class Account(
    val cookies: String,
    val name: String? = null,
    val handle: String? = null,
    val avatar: String? = null,
)

/** Keeps the account in the app's private storage (never on shared storage, never in backups). */
class AccountStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.noBackupFilesDir, "account.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _account.value = runCatching {
                if (file.exists()) json.decodeFromString<Account>(file.readText()) else null
            }.getOrNull()
        }
    }

    fun save(account: Account?) {
        _account.value = account
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (account == null) file.delete() else file.writeText(json.encodeToString(Account.serializer(), account))
            }
        }
    }
}
