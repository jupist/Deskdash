package com.desk.dashboard.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.desk.dashboard.ui.Appearance
import com.desk.dashboard.ui.Corners
import com.desk.dashboard.ui.Fonts
import com.desk.dashboard.ui.Spacing
import com.desk.dashboard.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Theme, fonts and density (Settings › Appearance). */
object AppearanceStore {
    private val FAMILY = stringPreferencesKey("theme_family")
    private val MODE = stringPreferencesKey("theme_mode")
    private val BLACK = booleanPreferencesKey("theme_pure_black")
    private val CORNERS = stringPreferencesKey("theme_corners")
    private val SPACING = stringPreferencesKey("theme_spacing")
    private val FONTS = stringPreferencesKey("font_set")
    private val SCALE = floatPreferencesKey("text_scale")
    private val STAMP = longPreferencesKey("font_stamp")

    fun flow(context: Context): Flow<Appearance> = context.prefs.data.map { p ->
        val d = Appearance()
        Appearance(
            family = p[FAMILY] ?: d.family,
            mode = p[MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.mode,
            pureBlack = p[BLACK] ?: false,
            corners = p[CORNERS]?.let { runCatching { Corners.valueOf(it) }.getOrNull() } ?: d.corners,
            spacing = p[SPACING]?.let { runCatching { Spacing.valueOf(it) }.getOrNull() } ?: d.spacing,
            fonts = p[FONTS] ?: d.fonts,
            textScale = p[SCALE] ?: 1f,
            fontStamp = p[STAMP] ?: 0,
        )
    }.distinctUntilChanged()

    suspend fun update(context: Context, block: (Appearance) -> Appearance) {
        context.prefs.edit { p ->
            val cur = Appearance(
                family = p[FAMILY] ?: Appearance().family,
                mode = p[MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                pureBlack = p[BLACK] ?: false,
                corners = p[CORNERS]?.let { runCatching { Corners.valueOf(it) }.getOrNull() } ?: Corners.SQUARE,
                spacing = p[SPACING]?.let { runCatching { Spacing.valueOf(it) }.getOrNull() } ?: Spacing.NORMAL,
                fonts = p[FONTS] ?: Fonts.technical.id,
                textScale = p[SCALE] ?: 1f,
                fontStamp = p[STAMP] ?: 0,
            )
            val n = block(cur)
            p[FAMILY] = n.family
            p[MODE] = n.mode.name
            p[BLACK] = n.pureBlack
            p[CORNERS] = n.corners.name
            p[SPACING] = n.spacing.name
            p[FONTS] = n.fonts
            p[SCALE] = n.textScale
            p[STAMP] = n.fontStamp
        }
    }

    /**
     * Copies a user-picked font file into app storage as the display or text face and switches
     * to the custom set. Returns false if the file isn't a font Android can load.
     */
    suspend fun importFont(context: Context, which: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dest = Fonts.customFile(context, which)
        dest.parentFile?.mkdirs()
        val tmp = java.io.File(dest.parentFile, "$which.tmp")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            // TrueType, OpenType or a collection: check the file signature.
            val magic = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            val tag = String(magic, Charsets.ISO_8859_1)
            tag == "\u0000\u0001\u0000\u0000" || tag == "OTTO" || tag == "true" || tag == "ttcf"
        }.getOrDefault(false)
        if (ok) tmp.renameTo(dest) else tmp.delete()
        if (ok) update(context) { it.copy(fonts = Fonts.CUSTOM, fontStamp = System.currentTimeMillis()) }
        ok
    }

    suspend fun clearFont(context: Context, which: String) {
        Fonts.customFile(context, which).delete()
        update(context) { it.copy(fontStamp = System.currentTimeMillis()) }
    }
}
