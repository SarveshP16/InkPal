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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.inkpal.app.R
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

/** True black for the reader: lit pixels only where there is text (saves power on OLED). */
val NightColors = InkColors(
    paper = Color(0xFF000000),
    ink = Color(0xFFE6E6E6),
    muted = Color(0xFFA6A6A6),
    faint = Color(0xFF8C8C8C),
    rule = Color(0xFF3A3A3A),
    hairline = Color(0xFF262626),
    onInk = Color(0xFF000000),
)

/** Warm paper for the reader: softer than white, still high contrast (9:1). */
val SepiaColors = InkColors(
    paper = Color(0xFFF4ECD8),
    ink = Color(0xFF2E2416),
    muted = Color(0xFF5A4C3A),
    faint = Color(0xFF6E604C),
    rule = Color(0xFFD6C9AE),
    hairline = Color(0xFFE6DCC4),
    onInk = Color(0xFFF4ECD8),
)

val LocalInkColors = staticCompositionLocalOf { PaperColors }

/** Paper and ink of a reader theme, for the theme pickers. */
fun readerSwatch(theme: com.inkpal.app.data.ReaderTheme): Pair<Color, Color> = when (theme) {
    com.inkpal.app.data.ReaderTheme.WHITE -> Color.White to Color(0xFF111111)
    com.inkpal.app.data.ReaderTheme.SEPIA -> SepiaColors.paper to SepiaColors.ink
    com.inkpal.app.data.ReaderTheme.TRUE_BLACK -> Color.Black to Color.White
}

object Ink {
    val colors: InkColors
        @Composable get() = LocalInkColors.current
}

/** Literata (bundled, OFL) for book-ish type, system sans for the interface. */
@OptIn(ExperimentalTextApi::class)
object InkFonts {
    private fun literata(weight: Int) = Font(
        R.font.literata,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val serif: FontFamily = FontFamily(
        literata(400),
        literata(500),
        literata(600),
        literata(700),
        Font(
            R.font.literata_italic,
            weight = FontWeight.Normal,
            style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
    )
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
