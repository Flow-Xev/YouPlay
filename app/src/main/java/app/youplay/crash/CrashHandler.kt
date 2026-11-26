package app.youplay.crash

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/** Catches any uncaught exception, saves a report and opens [CrashActivity] (own process) instead of silently closing. */
object CrashHandler {
    fun isCrashProcess(): Boolean =
        runCatching { File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":crash") }.getOrDefault(false)

    fun install(app: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildReport(app, thread, error)
                runCatching { File(app.filesDir, "last_crash.txt").writeText(report) }
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashActivity.EXTRA_REPORT, report.take(100_000)),
                )
                Thread.sleep(400)
            } catch (_: Throwable) {
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun buildReport(app: Application, thread: Thread, error: Throwable): String {
        val version = runCatching {
            val p = app.packageManager.getPackageInfo(app.packageName, 0); "${p.versionName} (${p.longVersionCode})"
        }.getOrDefault("?")
        return buildString {
            appendLine("YouPlay crash report")
            appendLine("Time: ${java.util.Date()}")
            appendLine("App: ${app.packageName} $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(error))
        }
    }
}
