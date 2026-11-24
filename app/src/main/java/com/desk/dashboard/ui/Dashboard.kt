package com.desk.dashboard.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.board.BoardStore
import com.desk.dashboard.board.BoardView
import com.desk.dashboard.board.DashActions
import com.desk.dashboard.board.Grid
import com.desk.dashboard.board.GridLayout
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.board.Module
import com.desk.dashboard.board.Modules
import com.desk.dashboard.board.TileScope
import com.desk.dashboard.board.TileSpec
import com.desk.dashboard.data.DisplayConfig
import com.desk.dashboard.system.rememberBattery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The dashboard: top bar (or the edit bar while editing) above the tile board. The board's grid
 * is sized to the screen, and the active preset supplies its layout for that grid.
 */
@Composable
fun DashboardScreen(
    display: DisplayConfig,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    /** Size the grid as if the top bar were showing (the screensaver hides it but should match the app's layout). */
    gridAsWithBar: Boolean = false,
) {
    val context = LocalContext.current
    val actions = LocalDashActions.current!!
    val overlay = LocalOverlay.current
    val boards by BoardStore.boards.collectAsStateWithLifecycle()
    val b = boards ?: return
    val preset = b.active
    val undo = remember { mutableStateListOf<GridLayout>() }
    var hidden by remember { mutableStateOf<List<TileSpec>>(emptyList()) }

    Column(
        Modifier.fillMaxSize().background(Dash.frame).padding(Dash.gap),
        verticalArrangement = Arrangement.spacedBy(Dash.gap),
    ) {
        AnimatedContent(
            editing to display.topBar,
            transitionSpec = { (fadeIn() + slideInVertically { -it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
            label = "bar",
        ) { (edit, bar) ->
            when {
                edit -> EditBar(
                    presetName = preset.name,
                    hidden = hidden,
                    onPutBack = { putBack(context, hidden, undo) },
                    canUndo = undo.isNotEmpty(),
                    onUndo = { undo.removeLastOrNull()?.let { BoardStore.saveLayout(it) } },
                    onAdd = { overlay { ModulePicker(onClose = { overlay(null) }) { m -> overlay(null); addTile(context, m, undo) } } },
                    onDone = { onEditing(false) },
                )
                bar -> TopBar(display, actions)
                else -> Spacer(Modifier.height(0.dp))
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val gridH = if (gridAsWithBar) maxHeight - Dash.topBar - Dash.gap else maxHeight
            val grid = Grid.sizeFor(maxWidth.value, gridH.value)
            val layout = remember(preset, grid) { BoardStore.layoutFor(preset, grid.w, grid.h) }
            CurrentGrid.value = layout
            // So a phone can draw this board to scale (the screensaver reports the same grid).
            val info = com.desk.dashboard.remote.ScreenInfo(grid.w, grid.h, maxWidth.value, gridH.value)
            SideEffect { com.desk.dashboard.remote.RemoteHost.reportScreen(info) }
            // Tiles from the preset's other screen sizes that don't fit this one.
            hidden = remember(preset, layout) {
                val here = layout.tiles.map { it.id }.toSet()
                val source = preset.lastKey?.let { preset.layouts[it] }
                    ?: if (grid.w >= grid.h) com.desk.dashboard.board.Defaults.landscape else com.desk.dashboard.board.Defaults.portrait
                source.tiles.filter { it.id !in here && Modules.get(it.type) != null }
            }
            BoardView(
                layout = layout,
                editing = editing,
                onStartEditing = { onEditing(true) },
                onChange = { new ->
                    undo += layout
                    if (undo.size > 30) undo.removeAt(0)
                    BoardStore.saveLayout(new)
                },
                onTileSettings = { spec -> overlay { TileSettingsSheet(spec.id, onClose = { overlay(null) }, undo = undo) } },
                onTileConfig = { spec, cfg -> updateTile(spec.id) { it.copy(config = cfg) } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The layout on screen right now (what tile edits apply to). */
private object CurrentGrid { var value: GridLayout? = null }

/**
 * Changes one tile. Style and settings are shared by every saved size of the preset; if this
 * screen's size hasn't been saved yet, it's saved now so the change sticks.
 */
private fun updateTile(id: String, block: (TileSpec) -> TileSpec) {
    val preset = BoardStore.boards.value?.active ?: return
    val layout = CurrentGrid.value ?: return
    if (preset.layouts[layout.key] == null) {
        BoardStore.saveLayout(layout.copy(tiles = layout.tiles.map { if (it.id == id) block(it) else it }))
    }
    BoardStore.updateTileEverywhere(id, block)
}

private fun addTile(context: android.content.Context, m: Module, undo: MutableList<GridLayout>) {
    val layout = CurrentGrid.value ?: return
    // Default size if it fits, else the largest size (by area) between default and minimum that does.
    val dw = m.defaultSize.w.coerceAtMost(layout.cols)
    val dh = m.defaultSize.h.coerceAtMost(layout.rows)
    val sizes = (m.minSize.w..dw).flatMap { w -> (m.minSize.h..dh).map { h -> com.desk.dashboard.board.Size(w, h) } }
        .sortedWith(compareByDescending<com.desk.dashboard.board.Size> { it.w * it.h }.thenByDescending { it.w })
    for (s in sizes) {
        val spot = Grid.firstFree(layout.tiles, s.w, s.h, layout.cols, layout.rows) ?: continue
        val cfg = JsonObject(m.initialConfig().mapValues { (_, v) ->
            when (v) {
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                is Boolean -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        })
        val tile = TileSpec(BoardStore.newId(), m.type, spot.first, spot.second, s.w, s.h, m.defaultStyle.name, cfg)
        undo += layout
        BoardStore.saveLayout(layout.copy(tiles = layout.tiles + tile))
        return
    }
    Toast.makeText(context, "No room for ${m.name}: shrink or remove a tile first", Toast.LENGTH_LONG).show()
}

/** Places tiles that didn't fit this screen: at their size if possible, else their minimum. */
private fun putBack(context: android.content.Context, tiles: List<TileSpec>, undo: MutableList<GridLayout>) {
    val layout = CurrentGrid.value ?: return
    val placed = layout.tiles.toMutableList()
    var missed = 0
    for (t in tiles) {
        val min = Modules.minSize(t.type)
        val spot = listOf(t.w.coerceAtMost(layout.cols) to t.h.coerceAtMost(layout.rows), min.w to min.h).firstNotNullOfOrNull { (w, h) ->
            Grid.firstFree(placed, w, h, layout.cols, layout.rows)?.let { Triple(it.first, it.second, w to h) }
        }
        if (spot == null) { missed++; continue }
        placed += t.copy(x = spot.first, y = spot.second, w = spot.third.first, h = spot.third.second)
    }
    if (placed.size > layout.tiles.size) {
        undo += layout
        BoardStore.saveLayout(layout.copy(tiles = placed))
    }
    if (missed > 0) Toast.makeText(context, "$missed didn't fit: shrink or remove a tile, then try again", Toast.LENGTH_LONG).show()
}

@Composable
private fun TopBar(display: DisplayConfig, actions: DashActions) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(Dash.topBar)) {
        val roomy = maxWidth > 760.dp
        // Phones in landscape: shorter slider tracks, so the bar keeps space around them.
        val narrow = maxWidth < 940.dp
        Row(Modifier.fillMaxSize().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Dot icon: the list of installed apps (how to reach other apps in home-app mode).
            Box(Modifier.clip(Dash.smallShape).pressable(actions.openApps).padding(horizontal = 8.dp, vertical = 12.dp)) { DotGrid() }
            if (display.barBattery) {
                HSpace(16.dp)
                Box(Modifier.width(1.dp).height(26.dp).background(Dash.line))
                HSpace(20.dp)
                BatteryStat()
            }
            Spacer(Modifier.weight(1f))
            if (display.barSliders && roomy) {
                VolumeSlider(if (narrow) Modifier.width(190.dp) else Modifier, fill = narrow)
                HSpace(16.dp)
                BrightnessSlider(if (narrow) Modifier.width(190.dp) else Modifier, fill = narrow)
                Spacer(Modifier.weight(1f))
            }
            IconButton(DashIcons.grid, onClick = actions.editLayout)
            HSpace(10.dp)
            if (roomy) OutlineButton("Settings", DashIcons.gear) { actions.openSettings(null) }
            else IconButton(DashIcons.gear) { actions.openSettings(null) }
            if (display.barLock) {
                HSpace(10.dp)
                if (roomy) OutlineButton("Lock", DashIcons.lock, actions.lock) else IconButton(DashIcons.lock, onClick = actions.lock)
            }
        }
    }
}

/** Own composable so battery updates recompose only this stat. */
@Composable
private fun BatteryStat() {
    val battery = rememberBattery().value
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Txt("BATTERY", Type.small, Dash.muted)
        Txt(battery?.toString() ?: "—", Type.bodySmall, Dash.ink)
    }
}

@Composable
private fun EditBar(presetName: String, hidden: List<TileSpec>, onPutBack: () -> Unit, canUndo: Boolean, onUndo: () -> Unit, onAdd: () -> Unit, onDone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val overlay = LocalOverlay.current
    Row(Modifier.fillMaxWidth().height(Dash.topBar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt("EDITING LAYOUT", Type.small.copy(letterSpacing = Type.label.letterSpacing), Dash.accentText)
            Box {
                Row(Modifier.clickable { menu = true }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(presetName, Type.body, Dash.ink)
                    Txt("  ▾", Type.body, Dash.muted)
                    if (hidden.isNotEmpty()) {
                        HSpace(12.dp)
                        Txt("Didn't fit: ${hidden.mapNotNull { Modules.get(it.type)?.name }.joinToString()}", Type.meta, Dash.muted, Modifier.weight(1f, fill = false))
                        HSpace(8.dp)
                        MiniButton("Put back", Dash.ink, Dash.accent, onPutBack)
                    }
                }
                if (menu) PresetMenu(onDismiss = { menu = false }, onRename = { id, name ->
                    menu = false
                    overlay { RenameDialog(name, onSave = { BoardStore.rename(id, it); overlay(null) }) { overlay(null) } }
                })
            }
        }
        IconButton(DashIcons.undo, if (canUndo) Dash.ink else Dash.line, onClick = { if (canUndo) onUndo() })
        HSpace(10.dp)
        OutlineButton("Add tile", DashIcons.plus, onAdd)
        HSpace(10.dp)
        Row(
            Modifier.clip(Dash.smallShape).background(Dash.accent).pressable(onDone).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(DashIcons.check, Dash.onAccent, 14.dp)
            HSpace(8.dp)
            Txt("DONE", Type.label, Dash.onAccent)
        }
    }
}

@Composable
private fun PresetMenu(onDismiss: () -> Unit, onRename: (String, String) -> Unit) {
    val boards by BoardStore.boards.collectAsStateWithLifecycle()
    val b = boards ?: return
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(300.dp).clip(Dash.shape).background(Dash.tile).border(1.dp, Dash.line, Dash.shape).padding(vertical = 6.dp)) {
            Txt("PRESETS", Type.small, Dash.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            b.presets.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { BoardStore.setActive(p.id); onDismiss() }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(p.name, Type.body, if (p.id == b.activeId) Dash.accentText else Dash.ink, Modifier.weight(1f))
                    if (p.id == b.activeId) Icon(DashIcons.check, Dash.accentText, 14.dp)
                }
            }
            RowDivider()
            MenuItem("New preset (copy of this one)") { BoardStore.create("${b.active.name} copy", copyCurrent = true); onDismiss() }
            MenuItem("New preset (default layout)") { BoardStore.create("Preset ${b.presets.size + 1}", copyCurrent = false); onDismiss() }
            MenuItem("Rename “${b.active.name}”") { onRename(b.activeId, b.active.name) }
            MenuItem("Reset to default layout") { BoardStore.resetActive(); onDismiss() }
            if (b.presets.size > 1) MenuItem("Delete “${b.active.name}”", Dash.danger) { BoardStore.delete(b.activeId); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(text: String, color: Color = Dash.ink, onClick: () -> Unit) =
    Txt(text, Type.body, color, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp))

@Composable
private fun RenameDialog(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    Overlay(onDismiss = onClose, width = 400.dp) {
        PanelHeader("Rename preset", onClose)
        TextInput(name, { name = it }, "Name", 30)
        Spacer(Modifier.height(14.dp))
        Row { Spacer(Modifier.weight(1f)); PrimaryButton("Save") { if (name.isNotBlank()) onSave(name.trim()) } }
    }
}

/** Grid of every module: tap one to add it to the first free space. */
@Composable
private fun ModulePicker(onClose: () -> Unit, onPick: (Module) -> Unit) {
    Overlay(onDismiss = onClose, width = 720.dp) {
        PanelHeader("Add a tile", onClose)
        LazyVerticalGrid(
            GridCells.Adaptive(200.dp), Modifier.fillMaxWidth().heightIn(max = 460.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Modules.all, key = { it.type }) { m ->
                Column(
                    Modifier.clip(Dash.shape).border(1.dp, Dash.line, Dash.shape).pressable({ onPick(m) }).padding(14.dp).heightIn(min = 96.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(Dash.smallShape).background(Dash.accent), contentAlignment = Alignment.Center) {
                            Icon(m.icon, Dash.onAccent, 16.dp)
                        }
                        HSpace(10.dp)
                        Txt(m.name, Type.body, Dash.ink, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Txt(m.description, Type.meta, Dash.muted, maxLines = 3)
                }
            }
        }
    }
}

/** A tile's settings: look, label, its module's own options, and remove. */
@Composable
private fun TileSettingsSheet(id: String, onClose: () -> Unit, undo: MutableList<GridLayout>) {
    val boards by BoardStore.boards.collectAsStateWithLifecycle()
    val spec = boards?.active?.layouts?.values?.firstNotNullOfOrNull { l -> l.tiles.firstOrNull { it.id == id } }
        ?: CurrentGrid.value?.tiles?.firstOrNull { it.id == id } ?: return
    val module = Modules.get(spec.type) ?: return
    val scope = TileScope(spec) { cfg -> updateTile(id) { it.copy(config = cfg) } }
    val labelShown = (spec.config["showLabel"] as? JsonPrimitive)?.content != "false"
    Overlay(onDismiss = onClose, width = 560.dp) {
        PanelHeader(module.name, onClose)
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            SettingRow("Style") {
                Segmented(TileStyle.entries, runCatching { TileStyle.valueOf(spec.style) }.getOrDefault(TileStyle.PLAIN), { it.label }) { s ->
                    updateTile(id) { it.copy(style = s.name) }
                }
            }
            SwitchRow("Show label", "The small caption at the top of the tile", checked = labelShown, onToggle = { scope.set("showLabel", !labelShown) })
            RowDivider()
            module.Settings(scope)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${spec.w} × ${spec.h} cells · drag the corner to resize", Type.meta, Dash.muted, Modifier.weight(1f))
            MiniButton("Remove tile", Dash.danger, Dash.danger) {
                CurrentGrid.value?.let { l ->
                    undo += l
                    BoardStore.saveLayout(l.copy(tiles = l.tiles.filter { it.id != id }))
                }
                onClose()
            }
        }
    }
}
