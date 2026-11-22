package com.desk.dashboard.modules

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Size
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.system.Apps
import com.desk.dashboard.system.LaunchableApp
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashIcons
import com.desk.dashboard.ui.IconButton
import com.desk.dashboard.ui.MiniButton
import com.desk.dashboard.ui.SettingRow
import com.desk.dashboard.ui.Tile
import com.desk.dashboard.ui.Txt
import com.desk.dashboard.ui.Type
import com.desk.dashboard.ui.pressable
import com.desk.dashboard.ui.tc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps as a tappable list; a row of icons when the tile is short. */
@Composable
private fun AppList(apps: List<LaunchableApp>, onAdd: (() -> Unit)? = null) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight < 90.dp) {
            LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                items(apps, key = { it.component.flattenToString() }) { a ->
                    Image(a.icon, a.label, Modifier.size(40.dp).pressable({ launch(context, a) }, 0.9f))
                }
                if (onAdd != null) item {
                    Box(Modifier.size(40.dp).pressable(onAdd, 0.9f), contentAlignment = Alignment.Center) { com.desk.dashboard.ui.Icon(DashIcons.plus, tc.muted, 16.dp) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(apps, key = { it.component.flattenToString() }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { launch(context, a) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(a.icon, null, Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Txt(a.label, Type.body, tc.ink, Modifier.weight(1f))
                    }
                }
                if (onAdd != null) item {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { com.desk.dashboard.ui.Icon(DashIcons.plus, tc.muted, 14.dp) }
                        Spacer(Modifier.width(12.dp))
                        Txt("Add app", Type.body, tc.muted)
                    }
                }
            }
        }
    }
}

private fun launch(context: Context, a: LaunchableApp) = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    )
}

object FavouriteAppsModule : Module(
    "favapps", "Favourite apps", "Your chosen apps, one tap away.",
    DashIcons.star, Size(3, 3), Size(1, 1),
) {
    override fun label(t: TileScope) = t.str("title", "Favourites")

    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        val overlay = LocalOverlay.current
        val all by Apps.list.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { if (all == null) Apps.refresh(context) }
        val pkgs = t.strings("apps")
        val apps = pkgs.mapNotNull { p -> all?.firstOrNull { it.component.packageName == p } }
        val add = { overlay { AppPicker({ a -> t.set("apps", (pkgs + a.component.packageName).distinct()); overlay(null) }) { overlay(null) } } }
        Tile(label(t)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (pkgs.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Txt("No apps yet", Type.body, tc.muted)
                        Spacer(Modifier.height(8.dp))
                        Box { MiniButton("Add apps", tc.ink, tc.accentFill, add) }
                    }
                } else AppList(apps, onAdd = add)
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        val context = LocalContext.current
        val overlay = LocalOverlay.current
        val pkgs = t.strings("apps")
        TitleSetting(t, "Favourites")
        pkgs.forEachIndexed { i, p ->
            SettingRow(appLabel(context, p)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (i > 0) IconButton(DashIcons.back, size = 30.dp, iconSize = 12.dp) {
                        t.set("apps", pkgs.toMutableList().apply { add(i - 1, removeAt(i)) })
                    }
                    IconButton(DashIcons.close, size = 30.dp, iconSize = 12.dp) { t.set("apps", pkgs - p) }
                }
            }
        }
        SettingRow("Add an app") {
            MiniButton("Add", Dash.ink, Dash.accent) {
                overlay { AppPicker({ a -> t.set("apps", (pkgs + a.component.packageName).distinct()); overlay(null) }) { overlay(null) } }
            }
        }
    }
}

object UsageAccess {
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun intent(c: Context) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${c.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Launchable apps by last use in the past week, most recent first (excluding this app and home screens). */
    fun recent(c: Context, apps: List<LaunchableApp>): List<LaunchableApp> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7 * 86_400_000L, now)
        val homes = c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val last = HashMap<String, Long>()
        for (s in stats) if (s.lastTimeUsed > 0 && s.totalTimeInForeground > 0) last.merge(s.packageName, s.lastTimeUsed, ::maxOf)
        return apps.filter { it.component.packageName in last && it.component.packageName != c.packageName && it.component.packageName !in homes }
            .distinctBy { it.component.packageName }
            .sortedByDescending { last[it.component.packageName] }
    }
}

object RecentAppsModule : Module(
    "recentapps", "Recent apps", "The apps you used most recently.",
    DashIcons.clock, Size(3, 3), Size(1, 1),
) {
    override fun label(t: TileScope) = t.str("title", "Recent")

    @Composable
    override fun Content(t: TileScope) {
        val context = LocalContext.current
        var resumes by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumes++
            onPauseOrDispose {}
        }
        val minute by rememberMinute()
        val all by Apps.list.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { if (all == null) Apps.refresh(context) }
        val granted = remember(resumes) { UsageAccess.granted(context) }
        val recent by produceState(emptyList<LaunchableApp>(), all, resumes, minute, granted) {
            value = if (granted && all != null) withContext(Dispatchers.IO) { UsageAccess.recent(context, all!!) }.take(t.int("count", 8)) else emptyList()
        }
        Tile(label(t)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !granted -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Txt("Needs usage access", Type.body, tc.muted, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Box { MiniButton("Allow", tc.ink, tc.accentFill) { context.startActivity(UsageAccess.intent(context)) } }
                    }
                    recent.isEmpty() -> Txt("No recent apps", Type.body.copy(textAlign = TextAlign.Start), tc.muted)
                    else -> AppList(recent)
                }
            }
        }
    }

    @Composable
    override fun Settings(t: TileScope) {
        TitleSetting(t, "Recent")
        SettingRow("How many") {
            com.desk.dashboard.ui.Segmented(listOf(4, 6, 8, 12), t.int("count", 8), { "$it" }) { t.set("count", it) }
        }
    }
}

