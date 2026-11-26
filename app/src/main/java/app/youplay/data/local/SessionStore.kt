package app.youplay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.youplay.domain.AccountState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.authStore: DataStore<Preferences> by preferencesDataStore("youplay_auth")

data class RawSession(val cookie: String, val visitorData: String?, val dataSyncId: String?)

/** One saved YouTube login plus its cached profile. */
@Serializable
data class StoredAccount(
    val id: String,
    val cookie: String,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    val avatar: String? = null,
    val addedAt: Long = 0L,
) {
    fun toState() = AccountState(signedIn = true, id = id, name = name, email = email, handle = handle, avatarUrl = avatar)
    fun toRaw() = RawSession(cookie, visitorData, dataSyncId)
}

private class Snapshot(val accounts: List<StoredAccount>, val activeId: String?) {
    val active: StoredAccount? get() = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
}

/** Stable id for a login: the YouTube data-sync id when known, otherwise a hash of the SAPISID cookie. */
fun accountIdOf(cookie: String, dataSyncId: String?): String {
    dataSyncId?.takeIf { it.isNotBlank() }?.let { return it }
    val sap = cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("SAPISID=") }?.substringAfter("=") ?: cookie
    val d = MessageDigest.getInstance("SHA-1").digest(sap.toByteArray())
    return "c" + d.joinToString("") { "%02x".format(it) }.take(16)
}

/**
 * Persists every signed-in YouTube account (cookie + ids + cached profile) and which one is active.
 * Older single-account installs are migrated on first read.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        // legacy single-account keys
        val cookie = stringPreferencesKey("cookie")
        val visitor = stringPreferencesKey("visitor_data")
        val sync = stringPreferencesKey("data_sync_id")
        val name = stringPreferencesKey("name")
        val email = stringPreferencesKey("email")
        val handle = stringPreferencesKey("handle")
        val avatar = stringPreferencesKey("avatar")
        // multi-account keys
        val accounts = stringPreferencesKey("accounts_json")
        val active = stringPreferencesKey("active_account")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSer = ListSerializer(StoredAccount.serializer())

    private fun Preferences.snapshot(): Snapshot {
        val list = this[K.accounts]?.let { s -> runCatching { json.decodeFromString(listSer, s) }.getOrNull() }.orEmpty().toMutableList()
        val legacy = this[K.cookie]?.takeIf { it.isNotBlank() }
        if (list.isEmpty() && legacy != null) {
            list += StoredAccount(
                id = accountIdOf(legacy, this[K.sync]), cookie = legacy, visitorData = this[K.visitor], dataSyncId = this[K.sync],
                name = this[K.name], email = this[K.email], handle = this[K.handle], avatar = this[K.avatar],
                addedAt = System.currentTimeMillis(),
            )
        }
        return Snapshot(list, this[K.active])
    }

    /** Applies [block] to the account list; it returns the id that should be active afterwards (null = signed out). */
    private suspend fun mutate(block: (MutableList<StoredAccount>, String?) -> String?) {
        context.authStore.edit { p ->
            val snap = p.snapshot()
            val list = snap.accounts.toMutableList()
            val active = block(list, snap.active?.id)
            p[K.accounts] = json.encodeToString(listSer, list)
            if (active != null && list.any { it.id == active }) p[K.active] = active else p.remove(K.active)
            listOf(K.cookie, K.visitor, K.sync, K.name, K.email, K.handle, K.avatar).forEach { p.remove(it) }
        }
    }

    /** The active account (or a signed-out state). */
    val account: Flow<AccountState> =
        context.authStore.data.map { it.snapshot().active?.toState() ?: AccountState() }.distinctUntilChanged()

    /** Every saved account, active one included. */
    val accounts: Flow<List<AccountState>> =
        context.authStore.data.map { p -> p.snapshot().accounts.map { it.toState() } }.distinctUntilChanged()

    suspend fun raw(): RawSession? = context.authStore.data.first().snapshot().active?.toRaw()

    /** Adds (or refreshes) a login and makes it the active one. Returns its id. */
    suspend fun save(raw: RawSession): String {
        val id = accountIdOf(raw.cookie, raw.dataSyncId)
        mutate { list, _ ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) {
                list[i] = list[i].copy(cookie = raw.cookie, visitorData = raw.visitorData ?: list[i].visitorData, dataSyncId = raw.dataSyncId ?: list[i].dataSyncId)
            } else {
                list += StoredAccount(id, raw.cookie, raw.visitorData, raw.dataSyncId, addedAt = System.currentTimeMillis())
            }
            id
        }
        return id
    }

    suspend fun saveProfile(a: AccountState) = mutate { list, active ->
        val i = list.indexOfFirst { it.id == active }
        if (i >= 0) list[i] = list[i].copy(
            name = a.name ?: list[i].name, email = a.email ?: list[i].email,
            handle = a.handle ?: list[i].handle, avatar = a.avatarUrl ?: list[i].avatar,
        )
        active
    }

    /** Makes [id] the active account. Returns its session, or null if there is no such account. */
    suspend fun switchTo(id: String): RawSession? {
        var found: RawSession? = null
        mutate { list, active ->
            val hit = list.firstOrNull { it.id == id }
            found = hit?.toRaw()
            if (hit != null) id else active
        }
        return found
    }

    /** Signs the active account out. Returns the session that became active (another saved account), or null. */
    suspend fun removeActive(): RawSession? {
        var next: RawSession? = null
        mutate { list, active ->
            list.removeAll { it.id == active }
            val n = list.firstOrNull()
            next = n?.toRaw()
            n?.id
        }
        return next
    }

    suspend fun removeAll() = mutate { list, _ -> list.clear(); null }
}
