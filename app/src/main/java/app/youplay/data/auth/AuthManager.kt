package app.youplay.data.auth

import app.youplay.data.local.RawSession
import app.youplay.data.local.SessionStore
import app.youplay.domain.AccountState
import app.youplay.util.JsonWalk
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Owns the signed-in YouTube session and applies it to the shared [InnerTube] client. */
@Singleton
class AuthManager @Inject constructor(
    private val store: SessionStore,
    private val innerTube: InnerTube,
) {
    val account: Flow<AccountState> = store.account
    val accounts: Flow<List<AccountState>> = store.accounts
    private var applied = false

    suspend fun isSignedIn(): Boolean = store.account.first().signedIn

    /** Call before any network request so a saved login is active after app restart. */
    suspend fun ensure() {
        if (applied) return
        store.raw()?.let(::apply)
        applied = true
    }

    private fun apply(r: RawSession) =
        innerTube.replaceSession(r.cookie, r.visitorData, r.dataSyncId?.substringBefore("||"), "0", true)

    /** Adds the login (or refreshes a known one), makes it active and returns its account id. */
    suspend fun signIn(cookie: String, visitorData: String?, dataSyncId: String?): String {
        val raw = RawSession(cookie, visitorData, dataSyncId)
        val id = store.save(raw)
        apply(raw)
        applied = true
        refreshProfile()
        return id
    }

    suspend fun activeId(): String? = store.account.first().id

    /** Makes another saved account the active one. False if it no longer exists. */
    suspend fun switchTo(id: String): Boolean {
        val raw = store.switchTo(id) ?: return false
        apply(raw)
        applied = true
        return true
    }

    suspend fun refreshProfile() {
        runCatching {
            val text = innerTube.accountMenu(YouTubeClient.WEB).bodyAsText()
            JsonWalk.profile(JsonWalk.parseObject(text))?.let { store.saveProfile(it) }
        }
    }

    /** Signs out the active account; another saved account (if any) takes over. Returns true if one did. */
    suspend fun signOut(): Boolean {
        val next = store.removeActive()
        if (next != null) apply(next) else guest()
        applied = true
        return next != null
    }

    suspend fun signOutAll() {
        store.removeAll()
        guest()
        applied = true
    }

    private fun guest() = innerTube.replaceSession(null, null, null, "0", false)
}
