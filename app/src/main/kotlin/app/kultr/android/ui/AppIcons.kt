package app.kultr.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.kultr.android.R

/**
 * The app icons to choose from. Each is a launcher entry of its own (an
 * activity-alias in the manifest), and exactly one is switched on.
 */
enum class AppIcon(private val alias: String, val label: String, val background: Long, @param:DrawableRes val foreground: Int) {
    CREAM("MainActivity", "Cream", 0xFFF6F0E5, R.drawable.ic_launcher_foreground),
    EMBER("IconEmber", "Ember", 0xFFE8590C, R.drawable.ic_launcher_foreground_ember),
    NIGHT("IconNight", "Night", 0xFF141414, R.drawable.ic_launcher_foreground_night),
    CLASSIC("IconClassic", "Classic", 0xFF252525, R.drawable.ic_launcher_foreground_classic),
    ;

    private fun component(context: Context) = ComponentName(context.packageName, "app.kultr.android.$alias")

    private fun isOn(context: Context): Boolean = when (context.packageManager.getComponentEnabledSetting(component(context))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
        // As the manifest has it: Cream on, the others off.
        else -> this == CREAM
    }

    companion object {
        fun current(context: Context): AppIcon = entries.firstOrNull { it.isOn(context) } ?: CREAM

        /** Switch to [icon]: its entry on first, so the launcher is never left without Kultr. */
        fun use(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
