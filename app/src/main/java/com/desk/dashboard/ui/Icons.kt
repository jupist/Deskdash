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
}
