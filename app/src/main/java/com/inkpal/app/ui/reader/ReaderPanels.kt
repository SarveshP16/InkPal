package com.inkpal.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inkpal.app.ui.components.InkSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkpal.app.data.AppSettings
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Format
import com.inkpal.app.data.ReaderFont
import com.inkpal.app.data.ReaderTheme
import com.inkpal.app.data.ReadingSession
import com.inkpal.app.data.epub.EpubBook
import com.inkpal.app.data.epub.TocEntry
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.BookCover23
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText

/** Swallows taps so they don't reach the page underneath. */
private fun Modifier.blockTouches(): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {},
)

// ---------------------------------------------------------------- Menu (tap the middle)

@Composable
internal fun ReaderMenu(
    book: Book,
    epub: EpubBook,
    engine: ReaderEngine,
    topInset: Dp,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onContents: () -> Unit,
    onText: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = Ink.colors
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onDismiss),
    ) {
        // Top bar
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(c.paper)
                .blockTouches()
                .drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(top = topInset + 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(InkIcons.Back, "Close book", onClose)
            Text(
                book.shortTitle,
                style = InkText.bodyStrong.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onText)
                    .semantics { contentDescription = "Text settings" },
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", fontFamily = InkFonts.serif, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = c.ink)
            }
            IconBtn(InkIcons.Contents, "Contents", onContents)
        }

        // Bottom panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(c.paper)
                .blockTouches()
                .drawBehind { drawLine(c.ink, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            val shown = dragging ?: engine.progress
            val shownIndex = dragging?.let { epub.locate(it).first } ?: engine.index
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    epub.chapterTitle(shownIndex),
                    style = InkText.bodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (dragging != null) "${(shown * 100).toInt()}%"
                    else "Page ${engine.page + 1} of ${engine.pageCount} · ${(shown * 100).toInt()}%",
                    style = InkText.small,
                    color = c.muted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(InkIcons.ChapterPrev, "Previous chapter", { engine.prevChapter() }, size = 20)
                InkSlider(
                    value = shown,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { engine.jumpToProgress(it) }
                        dragging = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                IconBtn(InkIcons.ChapterNext, "Next chapter", { engine.nextChapter() }, size = 20)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(c.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(top = 6.dp),
            ) {
                MenuAction("Contents", InkIcons.Contents, onContents, Modifier.weight(1f))
                MenuAction("Text", null, onText, Modifier.weight(1f))
                MenuAction(if (book.status == BookStatus.FINISHED) "Finished" else "Mark finished", InkIcons.Check, onFinish, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MenuAction(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) {
    val c = Ink.colors
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.ink)
        } else {
            Text("Aa", fontFamily = InkFonts.serif, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.height(20.dp))
        }
        Text(label, style = InkText.small.copy(fontSize = 11.sp), color = c.ink)
    }
}

// ---------------------------------------------------------------- Contents

@Composable
internal fun ContentsPanel(
    epub: EpubBook,
    currentIndex: Int,
    topInset: Dp,
    onPick: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    // Books without a contents list get one line per section.
    val entries = remember(epub) {
        epub.toc.ifEmpty { epub.spine.indices.map { TocEntry("Section ${it + 1}", it, null, 0) } }
    }
    val current = entries.indexOfLast { it.spineIndex <= currentIndex }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .blockTouches()
            .padding(top = topInset),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Contents", style = InkText.bookTitleLarge, modifier = Modifier.weight(1f))
            IconBtn(InkIcons.Close, "Close contents", onDismiss)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(entries) { i, entry ->
                val isCurrent = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onPick(entry) }
                        .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                        .padding(start = (20 + entry.depth * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.title,
                        style = InkText.body.copy(
                            fontSize = if (entry.depth == 0) 15.sp else 14.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (entry.depth == 0 || isCurrent) c.ink else c.muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrent) {
                        Box(
                            Modifier
                                .padding(start = 12.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(c.ink),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Text settings (Aa)

@Composable
internal fun TextPanel(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(c.paper)
                .border(1.dp, c.ink, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .blockTouches()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.rule),
            )

            // Brightness
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Brightness", Modifier.weight(1f))
                    val auto = settings.brightness < 0f
                    Text(
                        if (auto) "Following system" else "Use system",
                        style = InkText.small.copy(fontWeight = FontWeight.SemiBold),
                        color = if (auto) c.muted else c.ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !auto, role = Role.Button) { onChange { it.copy(brightness = -1f) } }
                            .padding(horizontal = 6.dp, vertical = 12.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(InkIcons.SunSmall, null, Modifier.size(16.dp), tint = c.ink)
                    InkSlider(
                        value = if (settings.brightness < 0f) 0.5f else settings.brightness,
                        onValueChange = { v -> onChange { it.copy(brightness = v.coerceIn(0.01f, 1f)) } },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Icon(InkIcons.Sun, null, Modifier.size(22.dp), tint = c.ink)
                }
            }

            // Size
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Size")
                val shape = RoundedCornerShape(6.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, c.ink, shape)
                        .clip(shape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val steps = ReaderStyle.sizes.size
                    SizeButton("A", 15, "Smaller text", settings.textSize > 0) { onChange { it.copy(textSize = (it.textSize - 1).coerceAtLeast(0)) } }
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(steps) { i ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (i <= settings.textSize) Modifier.background(c.ink)
                                        else Modifier.border(1.dp, c.faint, CircleShape),
                                    ),
                            )
                        }
                    }
                    SizeButton("A", 24, "Larger text", settings.textSize < steps - 1) { onChange { it.copy(textSize = (it.textSize + 1).coerceAtMost(steps - 1)) } }
                }
            }

            // Font
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Font")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFont.entries.forEach { font ->
                        Choice(
                            selected = settings.readerFont == font,
                            onClick = { onChange { it.copy(readerFont = font) } },
                            modifier = Modifier.weight(1f),
                        ) { fg ->
                            Text(
                                font.label,
                                color = fg,
                                fontSize = 15.sp,
                                fontFamily = when (font) {
                                    ReaderFont.SERIF -> InkFonts.serif
                                    ReaderFont.SANS -> InkFonts.sans
                                    ReaderFont.PUBLISHER -> InkFonts.sans
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Line spacing + margins
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Line spacing")
                    Segments(
                        icons = listOf(InkIcons.SpacingTight, InkIcons.SpacingNormal, InkIcons.SpacingLoose),
                        labels = listOf("Tight line spacing", "Normal line spacing", "Loose line spacing"),
                        selected = settings.lineSpacing,
                    ) { v -> onChange { it.copy(lineSpacing = v) } }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Margins")
                    Segments(
                        icons = listOf(InkIcons.MarginNarrow, InkIcons.MarginNormal, InkIcons.MarginWide),
                        labels = listOf("Narrow margins", "Normal margins", "Wide margins"),
                        selected = settings.margins,
                    ) { v -> onChange { it.copy(margins = v) } }
                }
            }

            // Theme
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("White", "Default", Color.White, Color(0xFF111111), settings.readerTheme == ReaderTheme.WHITE, Modifier.weight(1f)) {
                        onChange { it.copy(readerTheme = ReaderTheme.WHITE) }
                    }
                    ThemeChoice("True black", "Saves battery", Color.Black, Color.White, settings.readerTheme == ReaderTheme.TRUE_BLACK, Modifier.weight(1f)) {
                        onChange { it.copy(readerTheme = ReaderTheme.TRUE_BLACK) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeButton(letter: String, size: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        Modifier
            .width(64.dp)
            .height(48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, fontFamily = InkFonts.serif, fontSize = size.sp, color = if (enabled) c.ink else c.faint)
    }
}

@Composable
private fun Choice(selected: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable (Color) -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(c.ink) else Modifier.border(1.dp, c.rule, shape))
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) c.onInk else c.ink)
    }
}

@Composable
private fun Segments(icons: List<ImageVector>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, c.ink, shape)
            .clip(shape),
    ) {
        icons.forEachIndexed { i, icon ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (on) c.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = labels[i] },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (on) c.onInk else c.ink)
            }
        }
    }
}

@Composable
private fun ThemeChoice(title: String, subtitle: String, bg: Color, fg: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    Column(
        modifier
            .heightIn(min = 56.dp)
            .then(if (selected) Modifier.border(2.dp, c.ink, RoundedCornerShape(6.dp)).padding(3.dp) else Modifier.padding(3.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(if (!selected) Modifier.border(1.dp, c.rule, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = InkText.bodyStrong, color = fg)
        Text(if (selected) "Selected" else subtitle, style = InkText.small, color = fg)
    }
}

// ---------------------------------------------------------------- End of book

@Composable
internal fun EndOfBook(
    book: Book,
    /** True when the reader paged past the last page; false when opened from the menu. */
    atEnd: Boolean,
    sessions: List<ReadingSession>,
    currentSessionMs: Long,
    topInset: Dp,
    onMarkFinished: () -> Unit,
    onUndo: () -> Unit,
    onKeepReading: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ink.colors
    val finished = book.status == BookStatus.FINISHED
    val readingMs = sessions.sumOf { it.durationMs } + currentSessionMs
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .blockTouches()
            .padding(top = topInset + 8.dp, start = 28.dp, end = 28.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconBtn(InkIcons.Close, "Back to the book", onKeepReading)
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel(if (atEnd) "The end" else "${book.percent}% read")
        Spacer(Modifier.height(14.dp))
        BookCover23(book, Modifier.width(120.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            if (atEnd) "You reached the end of ${book.shortTitle}" else "Finish ${book.shortTitle}?",
            style = InkText.bookTitleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(c.rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(c.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .padding(vertical = 14.dp),
        ) {
            Stat("Started", if (book.startedAt > 0) Format.shortDate(book.startedAt) else "—", Modifier.weight(1f))
            Stat("Reading time", Format.duration(readingMs), Modifier.weight(1f))
            Stat("Sessions", (sessions.size + if (currentSessionMs > 0) 1 else 0).toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        if (finished) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .border(2.dp, c.ink, RoundedCornerShape(6.dp))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(InkIcons.Check, null, Modifier.size(18.dp), tint = c.ink)
                Text("Moved to Finished", style = InkText.button, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text(
                    "Undo",
                    style = InkText.button,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Back to library", onClose, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Mark as finished", onMarkFinished, Modifier.fillMaxWidth(), icon = InkIcons.Check)
            Spacer(Modifier.height(10.dp))
            OutlineButton("Not yet, keep reading", onKeepReading, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = InkText.small, color = Ink.colors.muted)
        Text(value, style = InkText.bodyStrong.copy(fontSize = 15.sp))
    }
}
