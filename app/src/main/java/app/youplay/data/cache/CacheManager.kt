package app.youplay.data.cache

import android.content.Context
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * All of the app's caches in one place:
 *  - API cache: gzip'd JSON responses on disk (fresh reads by TTL, stale reads when offline / YouTube fails)
 *  - media cache (Media3) and image cache (Coil) are registered/handled here for size, clear and auto-clean.
 *
 * Auto-clean runs at start and every 6 hours: drops API entries older than 6 hours, keeps the API cache under
 * 40 MB (oldest first), and when the device is short on space (< 500 MB free) also empties media + images.
 */
@Singleton
class CacheManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val apiDir = File(context.cacheDir, "api").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clearHooks = ArrayList<() -> Unit>()

    // ---------- API response cache ----------
    private fun fileFor(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(apiDir, h)
    }

    fun apiGet(key: String, ttlMs: Long): String? {
        val f = fileFor(key)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > ttlMs) return null
        return read(f)
    }

    fun apiGetStale(key: String): String? = fileFor(key).takeIf { it.exists() }?.let { read(it) }

    fun apiPut(key: String, value: String) {
        runCatching {
            val f = fileFor(key)
            val tmp = File(apiDir, f.name + ".tmp")
            GZIPOutputStream(tmp.outputStream()).use { it.write(value.toByteArray()) }
            tmp.renameTo(f)
        }
    }

    private fun read(f: File): String? =
        runCatching { GZIPInputStream(f.inputStream()).use { String(it.readBytes()) } }.getOrElse { f.delete(); null }

    fun apiClear() { apiDir.listFiles()?.forEach { it.delete() } }

    // ---------- registration (e.g. the player registers its media cache) ----------
    @Synchronized fun addClearHook(hook: () -> Unit) { clearHooks += hook }

    // ---------- size / clear / auto-clean ----------
    fun sizeBytes(): Long =
        listOf("api", "media", "coil_cache").sumOf { dirSize(File(context.cacheDir, it)) }

    fun clearAll() {
        apiClear()
        synchronized(this) { clearHooks.toList() }.forEach { runCatching { it() } }
        runCatching { context.imageLoader.diskCache?.clear(); context.imageLoader.memoryCache?.clear() }
    }

    fun autoClean() {
        val now = System.currentTimeMillis()
        val files = apiDir.listFiles()?.toMutableList() ?: return
        files.filter { now - it.lastModified() > 6L * 3600 * 1000 || it.name.endsWith(".tmp") }
            .forEach { it.delete(); files.remove(it) }
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= 40L * 1024 * 1024) break
            total -= f.length(); f.delete()
        }
        if (context.cacheDir.usableSpace < 500L * 1024 * 1024) clearAll()
    }

    /** Called once from the Application: clean now, then every 6 hours. */
    fun start() {
        scope.launch {
            while (true) {
                runCatching { autoClean() }
                delay(6L * 3600 * 1000)
            }
        }
    }

    private fun dirSize(d: File): Long =
        if (!d.exists()) 0L else d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.0f MB".format(b / (1024.0 * 1024))
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}
