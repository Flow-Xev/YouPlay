package app.youplay.data.sync

import app.youplay.data.auth.AuthManager
import app.youplay.data.local.PendingActionDao
import app.youplay.data.local.PendingActionEntity
import app.youplay.data.remote.InnerTubeDataSource
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncResult {
    Synced, Queued, NotSignedIn;

    /** Short user-facing line, e.g. describe("Liked"). */
    fun describe(what: String) = when (this) {
        Synced -> "$what on YouTube"
        Queued -> "$what · will sync when YouTube is reachable"
        NotSignedIn -> "$what on this device · sign in to sync with YouTube"
    }
}

/**
 * Sends likes, dislikes, subscriptions and Watch-later saves to the signed-in YouTube account.
 * Anything that fails (offline, YouTube error) is queued in Room and retried by [flush].
 */
@Singleton
class AccountSync @Inject constructor(
    private val dao: PendingActionDao,
    private val remote: InnerTubeDataSource,
    private val auth: AuthManager,
) {
    private val lock = Mutex()
    val pending: Flow<Int> = dao.count()

    companion object {
        const val REACT = "REACT" // value: like | dislike | none
        const val SUB = "SUB"     // value: on | off
        const val WATCH_LATER = "WL"
    }

    suspend fun send(type: String, target: String, value: String): SyncResult = lock.withLock {
        dao.clearFor(type, target) // newest state wins
        if (!auth.isSignedIn()) return SyncResult.NotSignedIn
        if (runCatching { execute(type, target, value) }.isSuccess) SyncResult.Synced
        else {
            dao.add(PendingActionEntity(type = type, targetId = target, value = value, createdAt = System.currentTimeMillis()))
            SyncResult.Queued
        }
    }

    /** Drops queued changes (they belong to an account that is no longer active). */
    suspend fun clearPending() = lock.withLock { dao.clear() }

    /** Retries everything in the queue. Returns how many went through. */
    suspend fun flush(): Int = lock.withLock {
        if (!auth.isSignedIn()) return 0
        var n = 0
        for (a in dao.all()) {
            if (runCatching { execute(a.type, a.targetId, a.value) }.isSuccess) { dao.delete(a.id); n++ }
        }
        n
    }

    private suspend fun execute(type: String, target: String, value: String) {
        auth.ensure()
        val r: Any? = when (type) {
            REACT -> when (value) {
                "like" -> remote.like(target, true)
                "dislike" -> remote.dislike(target)
                else -> remote.like(target, false) // removes like or dislike
            }
            SUB -> remote.subscribe(target, value == "on")
            WATCH_LATER -> remote.addToPlaylist("WL", target)
            else -> null
        }
        if (r is HttpResponse && !r.status.isSuccess()) throw IOException("HTTP ${r.status.value}")
    }
}
