package com.inkpal.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Outline icons matching the mockups (24-unit grid, 1.75-unit strokes). Tint them with `Icon(tint = …)`. */
object InkIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false, stroke: Float = 1.75f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) {
                b.addPath(nodes, fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Search = icon("search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Sort = icon("sort", "M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Library = icon("library", "M4 19V5h4v14M9 19V7h4v12M14.5 7.2l3.6-1 3.4 12.5", "M3 19h18")
    val LibraryBold = icon("library-bold", "M4 19V5h4v14M9 19V7h4v12M14.5 7.2l3.6-1 3.4 12.5", "M3 19h18", stroke = 2.1f)
    val History = icon("history", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val HistoryBold = icon("history-bold", circle(12f, 12f, 9f), "M12 7v5l3 2", stroke = 2.1f)
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val SettingsBold = icon("settings-bold", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f), stroke = 2.1f)
    val Headphones = icon(
        "headphones",
        "M4 15v-3a8 8 0 0 1 16 0v3",
        "M4 14h2a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
        "M18 14h2a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
    )
    val BookOpen = icon("book-open", "M12 6c-2-1.5-5-2-8-2v14c3 0 6 .5 8 2 2-1.5 5-2 8-2V4c-3 0-6 .5-8 2z", "M12 6v14")
    val Check = icon("check", "M5 12l4.5 4.5L19 7", stroke = 2.25f)
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val ChevronRight = icon("chevron-right", "M9 6l6 6-6 6")
    val Folder = icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Refresh = icon("refresh", "M20 11a8 8 0 0 0-14.5-4.5L4 8", "M4 4v4h4", "M4 13a8 8 0 0 0 14.5 4.5L20 16", "M20 20v-4h-4")
    val Reset = icon("reset", "M4 12a8 8 0 1 0 2.5-5.8", "M4 3v4h4")
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Play = icon("play", "M8 5v14l11-7z", filled = true)
}
