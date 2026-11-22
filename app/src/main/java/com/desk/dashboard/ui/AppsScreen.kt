package com.desk.dashboard.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.system.Apps
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Alphabetical list of launchable apps (home-app mode's way to reach everything else). */
@Composable
fun AppsScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    // Cached list shows at once; a background refresh picks up any changes since.
    LaunchedEffect(Unit) { Apps.refresh(context) }
    val apps by Apps.list.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Dash.frame).padding(Dash.gap), verticalArrangement = Arrangement.spacedBy(Dash.gap)) {
        Row(
            Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Apps", Type.secondary, Dash.ink, Modifier.weight(1f))
            OutlineButton("Done", onClick = onDone)
        }
        val list = apps
        if (list == null) {
            Txt("Loading…", Type.bodySmall, Dash.muted, Modifier.padding(8.dp))
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(8),
                Modifier.fillMaxWidth().weight(1f).background(Dash.tile),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(list, key = { it.component.flattenToString() }) { app ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            )
                            onDone()
                        }.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(app.icon, app.label, Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Txt(app.label, Type.meta.copy(textAlign = TextAlign.Center), Dash.ink, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
