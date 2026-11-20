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
    private const val HEART = "M12 20.5s-7.5-4.6-9.3-9.4A4.9 4.9 0 0 1 12 6.4a4.9 4.9 0 0 1 9.3 4.7c-1.8 4.8-9.3 9.4-9.3 9.4z"
    val Heart = icon("heart", HEART)
    val HeartBold = icon("heart-bold", HEART, stroke = 2.1f)
    val Bookmark = icon("bookmark", "M6 4h12v17l-6-4-6 4z")
    val BookmarkFilled = icon("bookmark-filled", "M6 4h12v17l-6-4-6 4z", filled = true)
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Zoom = icon("zoom", circle(11f, 11f, 7f), "M20 20l-3.5-3.5M8 11h6M11 8v6")
    val Pause = icon("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z", filled = true)
    val Back30 = icon("back-30", "M4 12a8 8 0 1 0 2.5-5.8", "M4 3v4h4")
    val Forward30 = icon("forward-30", "M20 12a8 8 0 1 1-2.5-5.8", "M20 3v4h-4")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")
    val More = icon("more", circle(12f, 5f, 1.6f), circle(12f, 12f, 1.6f), circle(12f, 19f, 1.6f), filled = true)
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Contents = icon("contents", "M9 6h11M9 12h11M9 18h11", circle(4.5f, 6f, 1f), circle(4.5f, 12f, 1f), circle(4.5f, 18f, 1f))
    val ChapterPrev = icon("chapter-prev", "M6 5v14", "M18 5L9 12l9 7z")
    val ChapterNext = icon("chapter-next", "M18 5v14", "M6 5l9 7-9 7z")
    val SunSmall = icon("sun-small", circle(12f, 12f, 4f), "M12 3v1.5M12 19.5V21M3 12h1.5M19.5 12H21")
    val Sun = icon("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val SpacingTight = icon("spacing-tight", "M4 8h16M4 12h16M4 16h16")
    val SpacingNormal = icon("spacing-normal", "M4 6h16M4 12h16M4 18h16")
    val SpacingLoose = icon("spacing-loose", "M4 4h16M4 12h16M4 20h16")
    val MarginNarrow = icon("margin-narrow", "M3 4v16M21 4v16M6 8h12M6 12h12M6 16h12")
    val MarginNormal = icon("margin-normal", "M3 4v16M21 4v16M8 8h8M8 12h8M8 16h8")
    val MarginWide = icon("margin-wide", "M3 4v16M21 4v16M10 8h4M10 12h4M10 16h4")
}
