package com.inkpal.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * InkPal's black-and-white palette. Every screen reads colours from here so that more
 * themes can be added later by providing another [InkColors].
 */
@Immutable
data class InkColors(
    val paper: Color,
    val ink: Color,
    /** Secondary text (8.6:1 on paper). */
    val muted: Color,
    /** Tertiary text (5.3:1 on paper). */
    val faint: Color,
    /** Borders and dividers. */
    val rule: Color,
    /** Light row separators and progress tracks. */
    val hairline: Color,
    /** Text on an ink-filled surface. */
    val onInk: Color,
)

val PaperColors = InkColors(
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF111111),
    muted = Color(0xFF4D4D4D),
    faint = Color(0xFF6B6B6B),
    rule = Color(0xFFD4D4D4),
    hairline = Color(0xFFE3E3E3),
    onInk = Color(0xFFFFFFFF),
)

val LocalInkColors = staticCompositionLocalOf { PaperColors }

object Ink {
    val colors: InkColors
        @Composable get() = LocalInkColors.current
}

/** System serif (Noto Serif) for book-ish type, system sans for the interface. */
object InkFonts {
    val serif: FontFamily = FontFamily.Serif
    val sans: FontFamily = FontFamily.SansSerif
}

object InkText {
    val screenTitle = TextStyle(fontFamily = InkFonts.serif, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val bookTitle = TextStyle(fontFamily = InkFonts.serif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val bookTitleLarge = TextStyle(fontFamily = InkFonts.serif, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
    val label = TextStyle(fontFamily = InkFonts.sans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.96.sp)
    val body = TextStyle(fontFamily = InkFonts.sans, fontSize = 14.sp, lineHeight = 20.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = InkFonts.sans, fontSize = 12.sp, lineHeight = 16.sp)
    val button = TextStyle(fontFamily = InkFonts.sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun InkPalTheme(colors: InkColors = PaperColors, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.onInk,
        secondary = colors.ink,
        onSecondary = colors.onInk,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.paper,
        onSurface = colors.ink,
        surfaceVariant = colors.hairline,
        onSurfaceVariant = colors.muted,
        surfaceContainer = colors.paper,
        surfaceContainerHigh = colors.paper,
        surfaceContainerHighest = colors.paper,
        outline = colors.rule,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalInkColors provides colors,
            LocalContentColor provides colors.ink,
            LocalTextStyle provides InkText.body.copy(color = colors.ink),
            content = content,
        )
    }
}
