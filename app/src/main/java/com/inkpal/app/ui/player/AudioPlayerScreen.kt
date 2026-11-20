package com.inkpal.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkpal.app.InkPalApp
import com.inkpal.app.audio.Chapter
import com.inkpal.app.audio.SleepMode
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Format
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.BookCover
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.InkSlider
import com.inkpal.app.ui.reader.EndOfBook
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText
import kotlinx.coroutines.delay

private val Speeds = listOf(0.8f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

private fun speedLabel(speed: Float): String {
    val number = if (speed % 1f == 0f) "%.1f".format(speed) else speed.toString().trimEnd('0')
    return "$number×"
}

/** Full-screen audiobook player. Playback keeps going when this screen is closed. */
@Composable
fun AudioPlayerScreen(app: InkPalApp, onClose: () -> Unit, onDetails: (String) -> Unit) {
    val hub = app.audio
    val audio by hub.state.collectAsStateWithLifecycle()
    val chapters by hub.chapters.collectAsStateWithLifecycle()
    val sleep by hub.sleep.collectAsStateWithLifecycle()
    val data by app.store.data.collectAsStateWithLifecycle()
    val book = audio.bookId?.let { id -> data.books.firstOrNull { it.id == id } }
    val c = Ink.colors

    var showChapters by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(audio.ended) {
        if (audio.ended) {
            atEnd = true
            showEnd = true
        }
    }

    BackHandler {
        when {
            showEnd -> showEnd = false
            showChapters -> showChapters = false
            else -> onClose()
        }
    }

    if (book == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("Nothing playing", style = InkText.bookTitle)
            Text("Open an audiobook from your library to listen.", style = InkText.body, color = c.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Back to library",
                style = InkText.button,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, c.ink, RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        return
    }

    val chapter = chapters.lastOrNull { it.startMs <= audio.positionMs }
    val chapterIndex = chapter?.let { chapters.indexOf(it) } ?: -1
    val start = chapter?.startMs ?: 0L
    val end = chapter?.let { hub.chapterEnd(it) } ?: audio.durationMs
    val length = (end - start).coerceAtLeast(1)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shownPos = dragging?.let { start + (it * length).toLong() } ?: audio.positionMs
    val leftInBook = ((audio.durationMs - audio.positionMs) / audio.speed.coerceAtLeast(0.1f)).toLong()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(InkIcons.ChevronDown, "Close player", onClose)
                Text(
                    "LISTENING · ${book.format.label}",
                    style = InkText.label,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconBtn(InkIcons.More, "Book details", { onDetails(book.id) })
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCover(
                        book,
                        Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.shortTitle, fontFamily = InkFonts.serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = buildList {
                        if (book.author.isNotEmpty()) add(book.author)
                        if (chapters.size > 1 && chapterIndex >= 0) add("Chapter ${chapterIndex + 1} of ${chapters.size}")
                    }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = InkText.body, color = c.muted)
                    if (chapter != null && chapters.size > 1) {
                        Text(chapter.title, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InkSlider(
                        value = dragging ?: ((audio.positionMs - start).toFloat() / length).coerceIn(0f, 1f),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { hub.seekTo(start + (it * length).toLong()) }
                            dragging = null
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(Format.clockDuration(shownPos - start), style = InkText.small, color = c.muted)
                        Text(
                            if (audio.durationMs > 0) "${Format.duration(leftInBook)} left in book" else "",
                            style = InkText.small,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Text("−" + Format.clockDuration(end - shownPos), style = InkText.small, color = c.muted)
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBtn(InkIcons.ChapterPrev, "Previous chapter", { hub.prevChapter() })
                    SkipButton(InkIcons.Back30, "Back 30 seconds") { hub.seekBy(-30_000) }
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(c.ink)
                            .clickable(role = Role.Button) { hub.togglePlay() }
                            .semantics { contentDescription = if (audio.playing) "Pause" else "Play" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (audio.playing) InkIcons.Pause else InkIcons.Play, null, Modifier.size(30.dp), tint = c.onInk)
                    }
                    SkipButton(InkIcons.Forward30, "Forward 30 seconds") { hub.seekBy(30_000) }
                    IconBtn(InkIcons.ChapterNext, "Next chapter", { hub.nextChapter() })
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .drawBehind { drawLine(c.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(top = 6.dp, bottom = 16.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    var open by remember { mutableStateOf(false) }
                    BarAction(speedLabel(audio.speed), "Speed", null) { open = true }
                    OptionMenu(open, { open = false }, Speeds, audio.speed, ::speedLabel) { hub.setSpeed(it) }
                }
                Box(Modifier.weight(1f)) {
                    var open by remember { mutableStateOf(false) }
                    SleepAction(sleep?.endsAt, sleep?.mode) { open = true }
                    OptionMenu(open, { open = false }, SleepMode.entries, sleep?.mode ?: SleepMode.OFF, { it.label }) { hub.setSleep(it) }
                }
                Box(Modifier.weight(1f)) {
                    BarAction("Chapters", null, InkIcons.Contents) { showChapters = true }
                }
                Box(Modifier.weight(1f)) {
                    BarAction(if (book.status == BookStatus.FINISHED) "Finished" else "Mark finished", null, InkIcons.Check) {
                        atEnd = false
                        showEnd = true
                    }
                }
            }
        }

        if (showChapters) {
            ChapterList(
                chapters = chapters,
                current = chapterIndex,
                durationMs = audio.durationMs,
                onPick = {
                    hub.seekTo(it.startMs)
                    showChapters = false
                },
                onDismiss = { showChapters = false },
            )
        }
        if (showEnd) {
            EndOfBook(
                book = book,
                atEnd = atEnd,
                sessions = data.sessions.filter { it.bookId == book.id },
                currentSessionMs = 0,
                topInset = 0.dp,
                onMarkFinished = { app.store.markFinished(book.id) },
                onUndo = { app.store.markNotFinished(book.id) },
                onKeepReading = { showEnd = false },
                onClose = {
                    showEnd = false
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun SkipButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(36.dp), tint = Ink.colors.ink)
        Text("30", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink.colors.ink, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BarAction(value: String, label: String?, icon: ImageVector?, onClick: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = c.ink)
        else Text(value, style = InkText.bodyStrong.copy(fontSize = 16.sp), modifier = Modifier.height(22.dp))
        Text(if (icon != null) value else label.orEmpty(), style = InkText.small.copy(fontSize = 11.sp), color = c.muted, maxLines = 1)
    }
}

@Composable
private fun SleepAction(endsAt: Long?, mode: SleepMode?, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val text = when {
        mode == null || mode == SleepMode.OFF -> "Sleep: off"
        endsAt != null -> "Sleep " + Format.clockDuration((endsAt - now).coerceAtLeast(0))
        else -> "Sleep: chapter"
    }
    BarAction(text, null, InkIcons.Moon, onClick)
}

@Composable
private fun <T> OptionMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    current: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    val c = Ink.colors
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, c.ink),
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(label(option), style = InkText.body.copy(fontWeight = if (option == current) FontWeight.SemiBold else FontWeight.Normal)) },
                trailingIcon = if (option == current) {
                    { Icon(InkIcons.Check, null, Modifier.size(18.dp), tint = c.ink) }
                } else null,
                onClick = {
                    onPick(option)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<Chapter>,
    current: Int,
    durationMs: Long,
    onPick: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .clickable(enabled = false, onClick = {}),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Chapters", style = InkText.bookTitleLarge, modifier = Modifier.weight(1f))
            IconBtn(InkIcons.Close, "Close chapters", onDismiss)
        }
        if (chapters.size <= 1) {
            Text(
                "This audiobook has no chapter markers.",
                style = InkText.body,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            return
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(chapters) { i, ch ->
                val isCurrent = i == current
                val next = chapters.getOrNull(i + 1)?.startMs ?: durationMs
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onPick(ch) }
                        .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        ch.title,
                        style = InkText.body.copy(fontSize = 15.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal),
                        modifier = Modifier.weight(1f),
                    )
                    Text(Format.clockDuration((next - ch.startMs).coerceAtLeast(0)), style = InkText.small, color = c.muted)
                    if (isCurrent) Box(Modifier.size(8.dp).clip(CircleShape).background(c.ink))
                }
            }
        }
    }
}

/** A slim bar above the tabs while an audiobook is loaded: tap to open the player. */
@Composable
fun MiniPlayer(app: InkPalApp, onOpen: () -> Unit) {
    val audio by app.audio.state.collectAsStateWithLifecycle()
    val data by app.store.data.collectAsStateWithLifecycle()
    val chapters by app.audio.chapters.collectAsStateWithLifecycle()
    val book: Book = audio.bookId?.let { id -> data.books.firstOrNull { it.id == id } } ?: return
    val c = Ink.colors
    val chapter = chapters.lastOrNull { it.startMs <= audio.positionMs }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.paper)
            .drawBehind { drawLine(c.rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClickLabel = "Open player", onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookCover(book, Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(book.shortTitle, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                chapter?.title ?: Format.clockDuration(audio.positionMs),
                style = InkText.small,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.ink)
                .clickable(role = Role.Button) { app.audio.togglePlay() }
                .semantics { contentDescription = if (audio.playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (audio.playing) InkIcons.Pause else InkIcons.Play, null, Modifier.size(18.dp), tint = c.onInk)
        }
        if (!audio.playing) IconBtn(InkIcons.Close, "Stop and close", { app.audio.stop() }, size = 18)
    }
}
