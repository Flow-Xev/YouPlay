package app.youplay.data.remote

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Small LRU cache with per-read freshness: [get] only returns fresh entries, [getStale] returns anything we still hold. */
class TtlCache<K : Any, V : Any>(private val max: Int = 64) {
    private val map = object : LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<Long, V>>?): Boolean = size > max
    }

    @Synchronized fun get(key: K, ttlMs: Long): V? =
        map[key]?.takeIf { System.currentTimeMillis() - it.first < ttlMs }?.second

    @Synchronized fun getStale(key: K): V? = map[key]?.second
    @Synchronized fun put(key: K, value: V) { map[key] = System.currentTimeMillis() to value }
    @Synchronized fun remove(key: K) { map.remove(key) }
    @Synchronized fun clear() { map.clear() }
}

/** Concurrent callers asking for the same key share one in-flight request (preload + watch screen, quick re-taps). */
class SingleFlight<K : Any, V : Any> {
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val mine = CompletableDeferred<V>()
        val other = inFlight.putIfAbsent(key, mine)
        if (other != null) {
            return try { other.await() } catch (e: CancellationException) {
                // the leader was cancelled, but we were not: take over
                if (currentCoroutineContext().isActive) run(key, block) else throw e
            }
        }
        try {
            val v = block()
            mine.complete(v)
            return v
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, mine)
        }
    }
}

/** Retry transient failures with a short growing delay (never swallows cancellation). */
suspend fun <T> retrying(times: Int = 2, baseDelayMs: Long = 300, block: suspend () -> T): T {
    var last: Throwable? = null
    for (i in 0 until times) {
        try { return block() } catch (e: CancellationException) { throw e } catch (t: Throwable) {
            last = t
            if (i < times - 1) delay(baseDelayMs * (1L shl i) + (0..120).random())
        }
    }
    throw last ?: IllegalStateException("retry failed")
}

/**
 * Hedged request: start [primary]; if it fails or is still running after [hedgeAfterMs], start [backup] as well.
 * The first success wins and the other is cancelled. Fails only when both fail.
 */
suspend fun <T : Any> hedged(hedgeAfterMs: Long, primary: suspend () -> T, backup: suspend () -> T): T = coroutineScope {
    val result = CompletableDeferred<T>()
    val startBackup = CompletableDeferred<Unit>()
    val lock = Any()
    var failures = 0
    var firstError: Throwable? = null
    fun failed(t: Throwable) {
        synchronized(lock) {
            failures++
            if (firstError == null) firstError = t
            if (failures >= 2) result.completeExceptionally(firstError!!)
        }
    }
    val a = launch {
        try { result.complete(primary()) } catch (e: CancellationException) { throw e } catch (t: Throwable) {
            startBackup.complete(Unit); failed(t)
        }
    }
    val b = launch {
        withTimeoutOrNull(hedgeAfterMs) { startBackup.await() }
        try { result.complete(backup()) } catch (e: CancellationException) { throw e } catch (t: Throwable) { failed(t) }
    }
    try { result.await() } finally { a.cancel(); b.cancel() }
}
