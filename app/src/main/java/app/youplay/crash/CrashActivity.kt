package app.youplay.crash

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Crash screen. Deliberately plain Android views (no Compose / Hilt / AppCompat) and its own process,
 * so it still opens when the crash was caused by one of those.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT) ?: "No crash details available."
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        window.statusBarColor = Color.parseColor("#0A0A0A")
        window.navigationBarColor = Color.parseColor("#0A0A0A")

        fun button(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f
            setTextColor(if (filled) Color.WHITE else Color.parseColor("#F5F5F5"))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                if (filled) setColor(Color.parseColor("#E50914")) else { setColor(Color.TRANSPARENT); setStroke(dp(1), Color.parseColor("#444444")) }
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
            setOnClickListener { onClick() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(dp(16), dp(48), dp(16), dp(16))
        }
        root.addView(ImageView(this).apply {
            runCatching { setImageResource(resources.getIdentifier("ic_logo", "drawable", packageName)) }
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        root.addView(TextView(this).apply {
            text = "YouPlay crashed"; textSize = 22f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Sorry about that. Copy or share the report below so the bug can be fixed, or restart the app."
            textSize = 13f; setTextColor(Color.parseColor("#A3A3A3")); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })
        val trace = TextView(this).apply {
            text = report; textSize = 11f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#F5F5F5")); setTextIsSelectable(true); setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#141414")) }
        }
        root.addView(ScrollView(this).apply { addView(trace) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        row1.addView(button("Copy", false) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", report))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        })
        row1.addView(button("Share", false) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share crash report"))
        })
        row1.addView(button("Restart", true) {
            packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)?.let { startActivity(it) }
            finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid())
        })
        root.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Reset app data", false) {
            AlertDialog.Builder(this)
                .setTitle("Reset app data?")
                .setMessage("This signs you out and deletes downloads, history and settings. Use it if the app keeps crashing on start.")
                .setPositiveButton("Reset") { _, _ -> (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData() }
                .setNegativeButton("Cancel", null).show()
        })
        row2.addView(button("Close", false) { finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid()) })
        root.addView(row2)
        setContentView(root)
    }

    @Deprecated("Back closes the crash screen")
    override fun onBackPressed() { finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid()) }

    companion object { const val EXTRA_REPORT = "report" }
}
