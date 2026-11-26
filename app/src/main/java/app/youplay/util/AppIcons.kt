package app.youplay.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.youplay.R

/**
 * Launcher icons the user can pick from. Each one is an <activity-alias> in the manifest; exactly one is enabled.
 * [Default] is the new YouPlay ribbon logo.
 */
enum class LauncherIcon(val alias: String, val label: String, val preview: Int) {
    Default(".LauncherDefault", "YouPlay", R.drawable.ic_logo),
    Classic(".LauncherClassic", "Classic", R.drawable.ic_logo_classic),
}

object AppIcons {
    private fun component(context: Context, icon: LauncherIcon) =
        ComponentName(context.packageName, "app.youplay" + icon.alias)

    /** The icon that is currently enabled (falls back to [LauncherIcon.Default]). */
    fun current(context: Context): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            val state = runCatching { pm.getComponentEnabledSetting(component(context, icon)) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.Default
                else -> false
            }
        } ?: LauncherIcon.Default
    }

    /** Enables [icon] first, then disables the others, so the app never disappears from the launcher. */
    fun set(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        LauncherIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
