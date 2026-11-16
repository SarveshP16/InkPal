package com.inkpal.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookFormat
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Format
import com.inkpal.app.data.LibraryData
import com.inkpal.app.data.ScanState
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.BookCover
import com.inkpal.app.ui.components.BookCover23
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.ProgressLine
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.components.UnderlineTabs
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkText

enum class Shelf(val label: String) { ALL("All"), READING("Reading"), AUDIO("Audiobooks"), FINISHED("Finished") }
enum class SortMode(val label: String) { RECENT("Recently read"), TITLE("Title"), AUTHOR("Author") }

@Composable
fun LibraryScreen(
    data: LibraryData,
    scan: ScanState,
    onOpen: (Book) -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    var shelf by rememberSaveable { mutableStateOf(Shelf.ALL) }
    var sort by rememberSaveable { mutableStateOf(SortMode.RECENT) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val books = remember(data.books, shelf, sort, query) {
        val q = query.trim()
        data.books
            .filter {
                when (shelf) {
                    Shelf.ALL -> true
                    Shelf.READING -> it.status == BookStatus.READING
                    Shelf.AUDIO -> it.format.isAudio
                    Shelf.FINISHED -> it.status == BookStatus.FINISHED
                }
            }
            .filter { q.isEmpty() || it.title.contains(q, true) || it.author.contains(q, true) }
            .let { list ->
                when (sort) {
                    SortMode.RECENT -> list.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
                    SortMode.TITLE -> list.sortedBy { it.shortTitle.lowercase().removePrefix("the ") }
                    SortMode.AUTHOR -> list.sortedWith(compareBy({ it.author.ifEmpty { "￿" }.lowercase() }, { it.shortTitle.lowercase() }))
                }
            }
    }
    val reading = data.books.filter { it.status == BookStatus.READING }
    val continueText = reading.filter { it.format == BookFormat.EPUB }.maxByOrNull { it.lastOpenedAt }
    val continueAudio = reading.filter { it.format.isAudio }.maxByOrNull { it.lastOpenedAt }

    Column(Modifier.fillMaxSize()) {
        Header(
            searching = searching,
            query = query,
            onQuery = { query = it },
            onSearch = { searching = true },
            onCloseSearch = {
                searching = false
                query = ""
            },
            sort = sort,
            onSort = { sort = it },
            onAddFolder = onAddFolder,
        )
        if (scan.running) ScanBar(scan)

        when {
            data.folders.isEmpty() -> EmptyLibrary(onAddFolder)
            data.books.isEmpty() -> NoBooks(scan.running, onRescan, onAddFolder)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (!searching && (continueText != null || continueAudio != null)) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                        ContinueSection(continueText, continueAudio, onOpen)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
                    UnderlineTabs(
                        items = Shelf.entries.map { it to it.label },
                        selected = shelf,
                        onSelect = { shelf = it },
                    )
                }
                if (books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                        Text(
                            if (query.isNotBlank()) "No books match “${query.trim()}”." else emptyShelfText(shelf),
                            style = InkText.body,
                            color = Ink.colors.muted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(books, key = { it.id }) { book -> BookTile(book) { onOpen(book) } }
            }
        }
    }
}

private fun emptyShelfText(shelf: Shelf) = when (shelf) {
    Shelf.ALL -> "No books yet."
    Shelf.READING -> "Books you start will show up here."
    Shelf.AUDIO -> "No .m4b audiobooks in your folders."
    Shelf.FINISHED -> "Books you finish will show up here."
}

@Composable
private fun Header(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    onAddFolder: () -> Unit,
) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Box(
                Modifier
                    .weight(1f)
                    .drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(vertical = 10.dp),
            ) {
                if (query.isEmpty()) Text("Search title or author", style = InkText.body.copy(fontSize = InkText.button.fontSize), color = c.faint)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = InkText.body.copy(fontSize = InkText.button.fontSize, color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            IconBtn(InkIcons.Close, "Close search", onCloseSearch)
        } else {
            Text("Library", style = InkText.screenTitle, modifier = Modifier.weight(1f))
            IconBtn(InkIcons.Search, "Search library", onSearch)
            Box {
                var open by remember { mutableStateOf(false) }
                IconBtn(InkIcons.Sort, "Sort: ${sort.label}", { open = true })
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    containerColor = c.paper,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, c.ink),
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.label,
                                    style = InkText.body.copy(fontWeight = if (mode == sort) FontWeight.SemiBold else FontWeight.Normal),
                                )
                            },
                            trailingIcon = if (mode == sort) {
                                { Icon(InkIcons.Check, null, Modifier.size(18.dp), tint = c.ink) }
                            } else null,
                            onClick = {
                                onSort(mode)
                                open = false
                            },
                        )
                    }
                }
            }
            IconBtn(InkIcons.Plus, "Add a folder of books", onAddFolder)
        }
    }
}

@Composable
private fun ScanBar(scan: ScanState) {
    val c = Ink.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (scan.added > 0) "Scanning folders… ${scan.added} new" else "Scanning folders…",
            style = InkText.small,
            color = c.muted,
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = c.ink,
            trackColor = c.hairline,
        )
    }
}

@Composable
private fun ContinueSection(text: Book?, audio: Book?, onOpen: (Book) -> Unit) {
    val c = Ink.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Continue")
        if (text != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, c.ink, RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onOpen(text) }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BookCover(text, Modifier.size(width = 64.dp, height = 96.dp))
                Column(Modifier.weight(1f).height(96.dp)) {
                    Text(text.shortTitle, style = InkText.bookTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (text.author.isNotEmpty()) Text(text.author, style = InkText.small.copy(fontSize = InkText.body.fontSize), color = c.muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth()) {
                        Text(text.positionLabel.ifEmpty { "Last read ${Format.relativeDay(text.lastOpenedAt)}" }, style = InkText.small, color = c.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${text.percent}%", style = InkText.small.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(Modifier.height(7.dp))
                    ProgressLine(text.progress)
                }
            }
        }
        if (audio != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, c.rule, RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onOpen(audio) }
                    .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (audio.hasCover) {
                    BookCover(audio, Modifier.size(44.dp))
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(1.dp, c.ink, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(InkIcons.Headphones, null, Modifier.size(20.dp), tint = c.ink) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(audio.shortTitle, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val left = if (audio.durationMs > 0) Format.duration((audio.durationMs * (1 - audio.progress)).toLong()) + " left" else "${audio.percent}%"
                    Text(listOf(audio.positionLabel, left).filter { it.isNotEmpty() }.joinToString(" · "), style = InkText.small, color = c.muted, maxLines = 1)
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.ink),
                    contentAlignment = Alignment.Center,
                ) { Icon(InkIcons.Play, null, Modifier.size(16.dp), tint = c.onInk) }
            }
        }
    }
}

@Composable
private fun BookTile(book: Book, onClick: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCover23(book, Modifier.fillMaxWidth())
        if (book.status == BookStatus.READING) ProgressLine(book.progress, thickness = 2)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.shortTitle, style = InkText.small.copy(fontSize = InkText.body.fontSize.times(13f / 14f), fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (book.status == BookStatus.FINISHED) Icon(InkIcons.Check, null, Modifier.size(13.dp), tint = c.muted)
                else if (book.format.isAudio) Icon(InkIcons.Headphones, null, Modifier.size(13.dp), tint = c.muted)
                Text(tileMeta(book), style = InkText.small, color = c.muted, maxLines = 1)
            }
        }
    }
}

private fun tileMeta(book: Book) = when (book.status) {
    BookStatus.FINISHED -> "Finished"
    BookStatus.READING -> "${book.format.label} · ${book.percent}%"
    BookStatus.NEW -> "${book.format.label} · New"
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(InkIcons.BookOpen, null, Modifier.size(44.dp), tint = c.ink)
        Spacer(Modifier.height(20.dp))
        Text("Your library is empty", style = InkText.bookTitleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Choose the folders on your phone that hold your .epub books and .m4b audiobooks. InkPal only reads the folders you pick.",
            style = InkText.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Choose a folder", onAddFolder, Modifier.width(240.dp), icon = InkIcons.Folder)
    }
}

@Composable
private fun NoBooks(scanning: Boolean, onRescan: () -> Unit, onAddFolder: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (scanning) "Looking for books…" else "No books found", style = InkText.bookTitleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (scanning) "New books appear here as they're found." else "None of your folders contain .epub or .m4b files yet.",
            style = InkText.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        if (!scanning) {
            Spacer(Modifier.height(28.dp))
            Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Add another folder", onAddFolder, Modifier.fillMaxWidth(), icon = InkIcons.Folder)
                OutlineButton("Scan again", onRescan, Modifier.fillMaxWidth(), icon = InkIcons.Refresh)
            }
        }
    }
}
