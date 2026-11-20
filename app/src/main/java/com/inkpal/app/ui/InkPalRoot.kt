package com.inkpal.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkpal.app.InkPalApp
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookStatus
import androidx.compose.runtime.LaunchedEffect
import com.inkpal.app.ui.player.AudioPlayerScreen
import com.inkpal.app.ui.player.MiniPlayer
import com.inkpal.app.ui.reader.PdfReaderScreen
import com.inkpal.app.ui.reader.ReaderScreen
import com.inkpal.app.data.BookFormat
import com.inkpal.app.ui.details.BookDetailsScreen
import com.inkpal.app.ui.history.HistoryScreen
import com.inkpal.app.ui.library.LibraryScreen
import com.inkpal.app.ui.settings.SettingsScreen
import com.inkpal.app.ui.wishlist.WishlistScreen
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkText

enum class Tab { LIBRARY, HISTORY, WISHLIST, SETTINGS }

/** App shell: four tabs, the book details page, the readers and the audiobook player. */
@Composable
fun InkPalRoot(app: InkPalApp) {
    val data by app.store.data.collectAsStateWithLifecycle()
    val scan by app.scanner.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var readerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val openPlayerRequest by app.audio.openPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest) {
            readerBookId = null
            playerOpen = true
            app.audio.reconnect()
            app.audio.openPlayerRequest.value = false
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.scanner.addFolder(uri)
    }
    val addFolder = { pickFolder.launch(null) }

    /** Opens a book to read; a finished book starts over ("Read again"). */
    val read: (Book) -> Unit = { b ->
        if (b.format.isAudio) {
            app.store.markOpened(b.id, restart = b.status == BookStatus.FINISHED)
            app.store.book(b.id)?.let { app.audio.play(it) }
            playerOpen = true
        } else {
            app.store.markOpened(b.id, restart = b.status == BookStatus.FINISHED)
            readerBookId = b.id
        }
    }

    readerBookId?.let { id ->
        val format = data.books.firstOrNull { it.id == id }?.format
        if (format == BookFormat.PDF) PdfReaderScreen(app, id, onClose = { readerBookId = null })
        else ReaderScreen(app, id, onClose = { readerBookId = null })
        return
    }

    if (playerOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.colors.paper)
                .systemBarsPadding(),
        ) {
            AudioPlayerScreen(
                app = app,
                onClose = { playerOpen = false },
                onDetails = {
                    playerOpen = false
                    openBookId = it
                },
            )
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.colors.paper)
            .systemBarsPadding(),
    ) {
        val book = openBookId?.let { id -> data.books.firstOrNull { it.id == id } }
        if (book != null) {
            BackHandler { openBookId = null }
            BookDetailsScreen(
                book = book,
                data = data,
                onBack = { openBookId = null },
                onOpen = { read(book) },
                onToggleFinished = {
                    if (book.status == BookStatus.FINISHED) app.store.markNotFinished(book.id)
                    else app.store.markFinished(book.id)
                },
                onReset = { app.store.resetProgress(book.id) },
                onRemove = {
                    openBookId = null
                    app.store.removeBook(book.id)
                },
            )
        } else {
            if (tab != Tab.LIBRARY) BackHandler { tab = Tab.LIBRARY }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.LIBRARY -> LibraryScreen(
                            data = data,
                            scan = scan,
                            onOpen = { openBookId = it.id },
                            onRead = read,
                            onAddFolder = addFolder,
                            onRescan = app.scanner::scan,
                        )
                        Tab.HISTORY -> HistoryScreen(data = data, onOpen = { openBookId = it })
                        Tab.WISHLIST -> WishlistScreen(data = data, store = app.store, onOpenBook = { openBookId = it })
                        Tab.SETTINGS -> SettingsScreen(
                            data = data,
                            scan = scan,
                            store = app.store,
                            onAddFolder = addFolder,
                            onRemoveFolder = app.scanner::removeFolder,
                            onRescan = app.scanner::scan,
                        )
                    }
                }
                MiniPlayer(app, onOpen = { playerOpen = true })
                BottomBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(c.paper)
            .drawBehind { drawLine(c.rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
    ) {
        BarItem("Library", InkIcons.Library, InkIcons.LibraryBold, tab == Tab.LIBRARY) { onSelect(Tab.LIBRARY) }
        BarItem("History", InkIcons.History, InkIcons.HistoryBold, tab == Tab.HISTORY) { onSelect(Tab.HISTORY) }
        BarItem("Wishlist", InkIcons.Heart, InkIcons.HeartBold, tab == Tab.WISHLIST) { onSelect(Tab.WISHLIST) }
        BarItem("Settings", InkIcons.Settings, InkIcons.SettingsBold, tab == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(
    label: String,
    icon: ImageVector,
    activeIcon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = Ink.colors
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { selected = active }
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (active) {
                    val inset = size.width * 0.3f
                    drawLine(c.ink, Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), 2.dp.toPx())
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(if (active) activeIcon else icon, null, Modifier.size(22.dp), tint = if (active) c.ink else c.muted)
        Text(
            label,
            style = InkText.small.copy(fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
            color = if (active) c.ink else c.muted,
        )
    }
}
