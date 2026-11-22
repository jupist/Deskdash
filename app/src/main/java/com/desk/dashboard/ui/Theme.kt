package com.desk.dashboard.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.desk.dashboard.R
import java.io.File

/** One full set of colour tokens. Every screen reads these through [Dash]. */
@Immutable
data class Palette(
    val dark: Boolean,
    val frame: Color,
    val tile: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** Fill for highlighted tiles and primary buttons. */
    val accent: Color,
    /** Accent used as text or a thin mark on [tile]. */
    val accentText: Color,
    val onAccent: Color,
    /** Secondary fill (the sand tile in the original design). */
    val alt: Color,
    val onAlt: Color,
)

class ThemeFamily(val id: String, val name: String, val dark: Palette, val light: Palette)

private fun c(hex: Long) = Color(0xFF000000 or hex)

object Palettes {
    val slate = ThemeFamily(
        "slate", "Slate",
        Palette(true, c(0x111114), c(0x1C1C1F), c(0xEEECE5), c(0x8B8B88), c(0x2E2E33), c(0x4E6187), c(0x8EA3CC), c(0xF3F1EA), c(0xAEADA3), c(0x1C1C1F)),
        Palette(false, c(0xE8E6E0), c(0xF5F3EE), c(0x1D1D20), c(0x74726C), c(0xD5D2CA), c(0x4E6187), c(0x3E5480), c(0xF5F3EE), c(0x2B2B2F), c(0xEEECE5)),
    )
    val forest = ThemeFamily(
        "forest", "Forest",
        Palette(true, c(0x0F1311), c(0x19201C), c(0xE9EDE6), c(0x87918A), c(0x2A332E), c(0x3E6B55), c(0x8CC2A5), c(0xEEF3EC), c(0xB3AF98), c(0x19201C)),
        Palette(false, c(0xE6E9E2), c(0xF3F5EF), c(0x1A211D), c(0x6E7870), c(0xCFD6CC), c(0x3E6B55), c(0x2F5A45), c(0xF3F5EF), c(0x263029), c(0xE9EDE6)),
    )
    val ember = ThemeFamily(
        "ember", "Ember",
        Palette(true, c(0x141110), c(0x1F1A18), c(0xEFE8E2), c(0x948A83), c(0x352C28), c(0x9A4E36), c(0xE59C80), c(0xF6EEE8), c(0xBDB2A3), c(0x1F1A18)),
        Palette(false, c(0xEDE7E1), c(0xF8F4EF), c(0x241C18), c(0x7B706A), c(0xDCD2C9), c(0x9A4E36), c(0x8A3F28), c(0xF8F4EF), c(0x2E2622), c(0xEFE8E2)),
    )
    val graphite = ThemeFamily(
        "graphite", "Graphite",
        Palette(true, c(0x0E0E0E), c(0x1A1A1A), c(0xEDEDED), c(0x8A8A8A), c(0x2C2C2C), c(0x3A3A3A), c(0xD6D6D6), c(0xF2F2F2), c(0xCFCFCF), c(0x1A1A1A)),
        Palette(false, c(0xE7E7E5), c(0xF7F7F5), c(0x161616), c(0x727272), c(0xD4D4D2), c(0x222222), c(0x222222), c(0xF7F7F5), c(0x3A3A3A), c(0xF2F2F2)),
    )
    const val DYNAMIC = "dynamic"

    val builtIn = listOf(slate, forest, ember, graphite)

    /** Material You colours from the wallpaper (Android 12+); null on older versions. */
    fun dynamic(context: Context): ThemeFamily? {
        if (Build.VERSION.SDK_INT < 31) return null
        val r = context.resources
        fun k(id: Int) = Color(r.getColor(id, null))
        return ThemeFamily(
            DYNAMIC, "Wallpaper",
            Palette(
                true, k(android.R.color.system_neutral1_900), k(android.R.color.system_neutral1_800), k(android.R.color.system_neutral1_50), k(android.R.color.system_neutral2_400),
                k(android.R.color.system_neutral1_700), k(android.R.color.system_accent1_700), k(android.R.color.system_accent1_200), k(android.R.color.system_accent1_50),
                k(android.R.color.system_accent2_200), k(android.R.color.system_accent2_900),
            ),
            Palette(
                false, k(android.R.color.system_neutral2_100), k(android.R.color.system_neutral1_10), k(android.R.color.system_neutral1_900), k(android.R.color.system_neutral2_600),
                k(android.R.color.system_neutral2_200), k(android.R.color.system_accent1_600), k(android.R.color.system_accent1_700), k(android.R.color.system_neutral1_10),
                k(android.R.color.system_accent2_800), k(android.R.color.system_accent2_50),
            ),
        )
    }

    fun all(context: Context): List<ThemeFamily> = builtIn + listOfNotNull(dynamic(context))
}

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

enum class Corners(val label: String, val radius: Dp) { SQUARE("Square", 0.dp), SOFT("Soft", 10.dp), ROUND("Round", 20.dp) }

enum class Spacing(val label: String, val gap: Dp, val padH: Dp, val padV: Dp) {
    COMPACT("Compact", 8.dp, 14.dp, 12.dp), NORMAL("Normal", 12.dp, 20.dp, 18.dp), AIRY("Airy", 16.dp, 24.dp, 22.dp)
}

/** A display face (numbers, clock, titles) and a text face (labels, body). */
class FontSet(val id: String, val name: String, val display: FontFamily, val text: FontFamily, val displayWeight: FontWeight? = null)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object Fonts {
    val technical = FontSet(
        "technical", "Technical",
        FontFamily(Font(R.font.share_tech_mono)), FontFamily(Font(R.font.space_mono)),
    )
    val grotesk = FontSet(
        "grotesk", "Grotesk",
        FontFamily(Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))),
        FontFamily(Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400)))),
    )
    val editorial = FontSet(
        "editorial", "Editorial",
        FontFamily(Font(R.font.dm_serif_display)),
        FontFamily(Font(R.font.dm_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(420)))),
    )
    const val CUSTOM = "custom"
    val builtIn = listOf(technical, grotesk, editorial)

    /** User-picked font files (copied into app storage); a missing one falls back to Technical. */
    fun custom(context: Context): FontSet {
        fun load(name: String, fallback: FontFamily) =
            File(context.filesDir, "fonts/$name").takeIf { it.exists() }
                ?.let { runCatching { FontFamily(Font(it)) }.getOrNull() } ?: fallback
        return FontSet(CUSTOM, "Custom", load("display", technical.display), load("text", technical.text))
    }

    fun customFile(context: Context, which: String) = File(context.filesDir, "fonts/$which")
}

/** Appearance choices as stored. */
data class Appearance(
    val family: String = Palettes.slate.id,
    val mode: ThemeMode = ThemeMode.DARK,
    val pureBlack: Boolean = false,
    val corners: Corners = Corners.SQUARE,
    val spacing: Spacing = Spacing.NORMAL,
    val fonts: String = Fonts.technical.id,
    val textScale: Float = 1f,
    /** Bumped when a custom font file changes, so it's reloaded. */
    val fontStamp: Long = 0,
)

/**
 * The live look. Values are snapshot state, so anything reading [Dash] or [Type] during
 * composition recomposes when the user changes the theme; nothing needs restarting.
 */
object Look {
    var palette by mutableStateOf(Palettes.slate.dark)
        private set
    /** The theme's dark variant: the night screen is always dark, whatever the mode. */
    var nightPalette by mutableStateOf(Palettes.slate.dark)
        private set
    var fonts by mutableStateOf(Fonts.technical)
        private set
    var corners by mutableStateOf(Corners.SQUARE)
        private set
    var spacing by mutableStateOf(Spacing.NORMAL)
        private set
    var textScale by mutableFloatStateOf(1f)
        private set
    var appearance by mutableStateOf(Appearance())
        private set

    private var loadedStamp = -1L
    private var customFonts: FontSet? = null

    fun apply(context: Context, a: Appearance) {
        appearance = a
        val family = Palettes.all(context).firstOrNull { it.id == a.family } ?: Palettes.slate
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = when (a.mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark }
        var p = if (dark) family.dark else family.light
        if (dark && a.pureBlack) p = p.copy(frame = Color.Black, tile = Color(0xFF0B0B0C), line = Color(0xFF222225))
        palette = p
        nightPalette = family.dark
        corners = a.corners
        spacing = a.spacing
        textScale = a.textScale
        fonts = if (a.fonts == Fonts.CUSTOM) {
            if (customFonts == null || loadedStamp != a.fontStamp) {
                customFonts = Fonts.custom(context)
                loadedStamp = a.fontStamp
            }
            customFonts!!
        } else {
            Fonts.builtIn.firstOrNull { it.id == a.fonts } ?: Fonts.technical
        }
    }

    /** Re-evaluates System mode and wallpaper colours after a configuration change. */
    fun refresh(context: Context) = apply(context, appearance)
}

/** Colour and size tokens, read from [Look]. */
object Dash {
    val frame get() = Look.palette.frame
    val tile get() = Look.palette.tile
    val ink get() = Look.palette.ink
    val muted get() = Look.palette.muted
    val line get() = Look.palette.line
    val accent get() = Look.palette.accent
    val accentText get() = Look.palette.accentText
    val onAccent get() = Look.palette.onAccent
    val onAccentMuted get() = Look.palette.onAccent.copy(alpha = 0.7f)
    val alt get() = Look.palette.alt
    val onAlt get() = Look.palette.onAlt
    val danger get() = if (Look.palette.dark) Color(0xFFE07A6B) else Color(0xFFB3412F)
    val scrim get() = Color(0xCC000000)
    val isDark get() = Look.palette.dark

    val gap get() = Look.spacing.gap
    val topBar = 48.dp
    val tilePadV get() = Look.spacing.padV
    val tilePadH get() = Look.spacing.padH
    val radius get() = Look.corners.radius
    val shape: Shape get() = RoundedCornerShape(Look.corners.radius)
    /** Smaller radius for buttons and fields inside tiles. */
    val smallShape: Shape get() = RoundedCornerShape(Look.corners.radius / 2)
}

val Display: FontFamily get() = Look.fonts.display
val Mono: FontFamily get() = Look.fonts.text

private fun sz(v: Float): TextUnit = (v * Look.textScale).sp

object Type {
    /** Tile labels: uppercase (apply .uppercase() to the text), 0.1 em tracking. */
    val label get() = TextStyle(fontFamily = Mono, fontSize = sz(11f), letterSpacing = 0.1.em, lineHeight = sz(14f))
    val meta get() = TextStyle(fontFamily = Mono, fontSize = sz(10f), letterSpacing = 0.06.em, lineHeight = sz(13f))
    val small get() = TextStyle(fontFamily = Mono, fontSize = sz(9f), letterSpacing = 0.08.em, lineHeight = sz(12f))
    val body get() = TextStyle(fontFamily = Mono, fontSize = sz(13f), letterSpacing = 0.04.em, lineHeight = sz(17f))
    val bodySmall get() = TextStyle(fontFamily = Mono, fontSize = sz(12f), letterSpacing = 0.04.em, lineHeight = sz(16f))
    val title get() = TextStyle(fontFamily = Mono, fontSize = sz(15f), letterSpacing = 0.02.em, lineHeight = sz(20f))

    val clock get() = TextStyle(fontFamily = Display, fontSize = 104.sp, lineHeight = 94.sp, letterSpacing = (-0.02).em)
    val big get() = TextStyle(fontFamily = Display, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = (-0.01).em)
    val secondary get() = TextStyle(fontFamily = Display, fontSize = sz(28f), lineHeight = sz(30f))
    val display16 get() = TextStyle(fontFamily = Display, fontSize = sz(16f), lineHeight = sz(20f))
    val display20 get() = TextStyle(fontFamily = Display, fontSize = sz(20f), lineHeight = sz(22f))
}

/** Colours for content inside one tile, which may be plain, accent or alt filled. */
@Immutable
data class TileColors(val fill: Color, val ink: Color, val muted: Color, val line: Color, val accent: Color, val accentFill: Color, val onAccentFill: Color)

enum class TileStyle(val label: String) { PLAIN("Plain"), ACCENT("Accent"), ALT("Alt") }

fun tileColors(style: TileStyle): TileColors = when (style) {
    TileStyle.PLAIN -> TileColors(Dash.tile, Dash.ink, Dash.muted, Dash.line, Dash.accentText, Dash.accent, Dash.onAccent)
    TileStyle.ACCENT -> TileColors(
        Dash.accent, Dash.onAccent, Dash.onAccentMuted, Dash.onAccent.copy(alpha = 0.22f), Dash.onAccent,
        Dash.onAccent, Dash.accent,
    )
    TileStyle.ALT -> TileColors(
        Dash.alt, Dash.onAlt, Dash.onAlt.copy(alpha = 0.65f), Dash.onAlt.copy(alpha = 0.2f), Dash.onAlt,
        Dash.onAlt, Dash.alt,
    )
}

val LocalTileColors = compositionLocalOf<TileColors?> { null }

/** Colours for the tile being drawn (plain outside a tile). */
val tc: TileColors
    @Composable get() = LocalTileColors.current ?: tileColors(TileStyle.PLAIN)
