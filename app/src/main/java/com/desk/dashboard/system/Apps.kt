package com.desk.dashboard.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LaunchableApp(val label: String, val component: ComponentName, val icon: ImageBitmap)

/**
 * Installed launchable apps for the app list. Loaded in the background at startup and refreshed
 * whenever the list opens, so it shows at once (the cached list) and catches installs, removals,
 * theme and palette changes a moment later.
 */
object Apps {
    private val _list = MutableStateFlow<List<LaunchableApp>?>(null)
    val list: StateFlow<List<LaunchableApp>?> = _list
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { lock.withLock { _list.value = load(app) } }
    }

    private fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val px = (52 * context.resources.displayMetrics.density).toInt()
        val theme = IconTheme.read(context)
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val icon = ri.loadIcon(pm)
                LaunchableApp(
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    icon = (theme?.apply(icon) ?: icon).toBitmap(px, px).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}

/**
 * Themed ("Apply palette to app icons") icons, drawn the way the launcher does: the icon's
 * monochrome layer on a palette-coloured tile, in the system icon shape. Icons without a
 * monochrome layer keep their colours, as on the launcher.
 */
private class IconTheme(val background: Int, val foreground: Int) {
    fun apply(icon: Drawable): Drawable? {
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome ?: return null
        return AdaptiveIconDrawable(ColorDrawable(background), mono.mutate().apply { setTint(foreground) })
    }

    companion object {
        /** Null when themed icons are off. Samsung stores the switch as colortheme_app_icon. */
        fun read(context: Context): IconTheme? {
            val on = runCatching { Settings.System.getInt(context.contentResolver, "colortheme_app_icon") == 1 }
                .getOrDefault(false)
            if (!on) return null
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val c = context.resources
            return if (dark) {
                IconTheme(c.getColor(android.R.color.system_neutral1_800, null), c.getColor(android.R.color.system_accent1_100, null))
            } else {
                IconTheme(c.getColor(android.R.color.system_accent1_100, null), c.getColor(android.R.color.system_neutral2_700, null))
            }
        }
    }
}
