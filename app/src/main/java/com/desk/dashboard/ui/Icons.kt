package com.desk.dashboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 16×16 icons lifted from mockup.html. Tint is applied by the caller via ColorFilter. */
object DashIcons {
    private fun stroke(d: String, width: Float = 1.4f) = ImageVector.Builder(
        defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f,
    ).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
    ).build()

    private fun fill(d: String) = ImageVector.Builder(
        defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f,
    ).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = SolidColor(Color.White),
    ).build()

    val gear = stroke(
        "M10.2 8a2.2 2.2 0 1 1-4.4 0a2.2 2.2 0 1 1 4.4 0z" +
            "M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4"
    )
    val lock = stroke("M3 7h10v7H3zM5 7V5a3 3 0 0 1 6 0v2")
    val arrow = stroke("M4 12L12 4M6 4h6v6", 1.5f)
    val prev = fill("M4 3h1.5v10H4zM13 3v10L6.5 8z")
    val next = fill("M10.5 3H12v10h-1.5zM3 3v10l6.5-5z")
    val pause = fill("M4 3h3v10H4zM9 3h3v10H9z")
    val play = fill("M4 2.5v11l9.5-5.5z")

    // Module and editor icons (same 16 px stroke style).
    val clock = stroke("M8 1.8a6.2 6.2 0 1 1 0 12.4a6.2 6.2 0 1 1 0-12.4zM8 4.5V8l2.4 1.6")
    val calendar = stroke("M2.5 3.5h11v10h-11zM2.5 6.5h11M5 2v3M11 2v3")
    val flag = stroke("M3.5 14V2.5M3.5 3h8l-1.8 2.8l1.8 2.7h-8")
    val cloud = stroke("M4.5 12.5h7a2.8 2.8 0 0 0 .3-5.6A3.8 3.8 0 0 0 4.4 7.6a2.5 2.5 0 0 0 .1 4.9z")
    val music = stroke("M6 12.2a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0zM13.5 10.7a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0zM6 12.2V3.5l7.5-1.5v8.7")
    val bell = stroke("M4 11.5V7a4 4 0 0 1 8 0v4.5l1.2 1.2H2.8zM6.5 14h3")
    val waves = stroke("M1.5 6c1.6-1.4 3.2-1.4 4.8 0s3.2 1.4 4.8 0s2.6-1.2 3.4-.4M1.5 10c1.6-1.4 3.2-1.4 4.8 0s3.2 1.4 4.8 0s2.6-1.2 3.4-.4")
    val app = stroke("M2.5 2.5h11v11h-11zM6 10l4-4M7 6h3v3")
    val timer = stroke("M8 3.5a5.2 5.2 0 1 1 0 10.4a5.2 5.2 0 1 1 0-10.4zM6.2 1.5h3.6M8 6v2.8l1.8 1.2M12.4 3.6l1 1")
    val checklist = stroke("M2.5 4l1.2 1.2L6 2.9M2.5 9l1.2 1.2L6 7.9M8 4h5.5M8 9h5.5M2.8 13h.4M8 13h5.5")
    val note = stroke("M3 2.5h7.5l2.5 2.5v8.5H3zM10.5 2.5V5H13M5.5 7.5h5M5.5 10h3.5")
    val globe = stroke("M8 1.8a6.2 6.2 0 1 1 0 12.4a6.2 6.2 0 1 1 0-12.4zM1.8 8h12.4M8 1.8c-2 2-2 10.4 0 12.4M8 1.8c2 2 2 10.4 0 12.4")
    val battery = stroke("M2 5h10.5v6H2zM12.5 7h1.5v2h-1.5M4 7v2M6 7v2")
    val image = stroke("M2 3h12v10H2zM2 11l3.5-3.5L8 10l2-2l4 4M10.8 5.6a.9.9 0 1 1-1.8 0a.9.9 0 1 1 1.8 0z")
    val quote = stroke("M3 11.5V8.2C3 6 4.2 4.6 6.4 4M3 8.2h3.2v3.3H3M9 11.5V8.2C9 6 10.2 4.6 12.4 4M9 8.2h3.2v3.3H9")
    val sliders = stroke("M2 5h7M12 5h2M2 11h2M7 11h7M10.5 3.5v3M5.5 9.5v3")
    val plus = stroke("M8 3v10M3 8h10", 1.6f)
    val close = stroke("M4 4l8 8M12 4l-8 8", 1.6f)
    val grid = stroke("M2.5 2.5h4.5v4.5h-4.5zM9 2.5h4.5v4.5H9zM2.5 9h4.5v4.5h-4.5zM9 9h4.5v4.5H9z")
    val check = stroke("M3 8.5l3 3l7-7", 1.7f)
    val chevronRight = stroke("M6 3.5l4.5 4.5L6 12.5")
    val back = stroke("M10 3.5L5.5 8l4.5 4.5", 1.6f)
    val resize = stroke("M13.5 6.5v7h-7M9.5 13.5l4-4", 1.6f)
    val undo = stroke("M5 3.5L2 6.5l3 3M2 6.5h7.5a3.5 3.5 0 0 1 0 7H7")
    val palette = stroke("M8 1.8a6.2 6.2 0 1 0 0 12.4c1 0 1.3-.8.9-1.6c-.5-.9.1-1.9 1.1-1.9h1.6a2.6 2.6 0 0 0 2.6-2.6C14.2 4.6 11.5 1.8 8 1.8zM4.8 7.6h.1M6.5 4.8h.1M9.8 4.8h.1")
    val type = stroke("M3 4V2.8h10V4M8 2.8v10.4M6 13.2h4")
    val moon = stroke("M13.2 9.6A5.6 5.6 0 0 1 6.4 2.8a5.6 5.6 0 1 0 6.8 6.8z")
    val shield = stroke("M8 1.8l5 2v4c0 3-2.2 5.3-5 6.4C5.2 13.1 3 10.8 3 7.8v-4z")
    val info = stroke("M8 1.8a6.2 6.2 0 1 1 0 12.4a6.2 6.2 0 1 1 0-12.4zM8 7.2v4M8 4.8v.2")
    val phone = stroke("M4.5 1.8h7v12.4h-7zM7 12h2")
    val pin = stroke("M8 14.2s4.5-4.3 4.5-7.7a4.5 4.5 0 0 0-9 0c0 3.4 4.5 7.7 4.5 7.7zM9.6 6.4a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z")
    val refresh = stroke("M13 8a5 5 0 1 1-1.5-3.6M13 2.5v3h-3")
    val trash = stroke("M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 9h5.8l.6-9")
    val link = stroke("M7 9a2.8 2.8 0 0 0 4 0l2-2a2.8 2.8 0 0 0-4-4l-.7.7M9 7a2.8 2.8 0 0 0-4 0l-2 2a2.8 2.8 0 0 0 4 4l.7-.7")
    val file = stroke("M3.5 1.8h6l3 3v9.4h-9zM9.5 1.8v3h3")
    val google = stroke("M13.6 8.2c0 3.2-2.3 5.6-5.6 5.6a5.8 5.8 0 1 1 3.9-10.1M13.6 8.2H8.3")

    // Weather glyphs.
    val sun = stroke("M8 5a3 3 0 1 1 0 6a3 3 0 1 1 0-6zM8 1v1.8M8 13.2V15M1 8h1.8M13.2 8H15M3 3l1.3 1.3M11.7 11.7L13 13M3 13l1.3-1.3M11.7 4.3L13 3")
    val partly = stroke("M6.5 3.2a2.6 2.6 0 0 1 3.9 1.6M5 2V1M2.3 3.3l-.7-.7M1.5 6H.5M5 12.5h6.5a2.5 2.5 0 0 0 .3-5A3.4 3.4 0 0 0 5.2 8a2.3 2.3 0 0 0-.2 4.5z")
    val rain = stroke("M4.5 9.5h7a2.6 2.6 0 0 0 .3-5.2A3.6 3.6 0 0 0 4.4 5a2.3 2.3 0 0 0 .1 4.5zM5.5 11.5l-.8 2M8.5 11.5l-.8 2M11.5 11.5l-.8 2")
    val snow = stroke("M4.5 9h7a2.6 2.6 0 0 0 .3-5.2A3.6 3.6 0 0 0 4.4 4.5a2.3 2.3 0 0 0 .1 4.5zM5 12h.1M8 13.5h.1M11 12h.1M6.5 14.5h.1M9.5 11h.1", 1.8f)
    val storm = stroke("M4.5 9h7a2.6 2.6 0 0 0 .3-5.2A3.6 3.6 0 0 0 4.4 4.5a2.3 2.3 0 0 0 .1 4.5zM8.8 9.5L7 12.2h2.2L7.6 15")
    val fog = stroke("M2 6h12M3.5 9h9M2 12h12")
}
