package com.inkpal.app.ui.wishlist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkpal.app.data.Book
import com.inkpal.app.data.Format
import com.inkpal.app.data.InkStore
import com.inkpal.app.data.LibraryData
import com.inkpal.app.data.WishItem
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.InkField
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.UnderlineTabs
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText

private enum class WishTab(val label: String) { WANTED("Want to read"), GOT("Got it") }

/** Books the user wants to read: just a title, an author and a note, no file needed. */
@Composable
fun WishlistScreen(data: LibraryData, store: InkStore, onOpenBook: (String) -> Unit) {
    val c = Ink.colors
    var tab by rememberSaveable { mutableStateOf(WishTab.WANTED) }
    var editing by remember { mutableStateOf<WishItem?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val wanted = remember(data.wishlist) { data.wishlist.filter { it.gotAt == 0L }.sortedByDescending { it.addedAt } }
    val got = remember(data.wishlist) { data.wishlist.filter { it.gotAt > 0L }.sortedByDescending { it.gotAt } }
    val shown = if (tab == WishTab.WANTED) wanted else got

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wishlist", style = InkText.screenTitle, modifier = Modifier.weight(1f))
            IconBtn(InkIcons.Plus, "Add a book to your wishlist", { adding = true })
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item {
                UnderlineTabs(
                    items = listOf(
                        WishTab.WANTED to "${WishTab.WANTED.label} (${wanted.size})",
                        WishTab.GOT to "${WishTab.GOT.label} (${got.size})",
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (shown.isEmpty()) {
                item { EmptyWishlist(tab, onAdd = { adding = true }) }
            }
            items(shown, key = { it.id }) { item ->
                val match = remember(item.title, item.author, data.books) { matchInLibrary(item, data.books) }
                WishRow(
                    item = item,
                    inLibrary = match,
                    onClick = { editing = item },
                    onToggleGot = { store.toggleWishGot(item.id) },
                    onOpenBook = { match?.let { onOpenBook(it.id) } },
                )
            }
        }
    }

    if (adding) {
        WishDialog(
            initial = null,
            onSave = { title, author, note ->
                store.addWish(title, author, note)
                adding = false
                tab = WishTab.WANTED
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { item ->
        WishDialog(
            initial = item,
            onSave = { title, author, note ->
                store.editWish(item.id, title, author, note)
                editing = null
            },
            onDelete = {
                store.removeWish(item.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** A library book with the same title (and author, when both are known). */
private fun matchInLibrary(item: WishItem, books: List<Book>): Book? {
    fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    val title = norm(item.title)
    if (title.isEmpty()) return null
    val author = norm(item.author)
    return books.firstOrNull { b ->
        (norm(b.shortTitle) == title || norm(b.title) == title) &&
            (author.isEmpty() || b.author.isEmpty() || norm(b.author).contains(author) || author.contains(norm(b.author)))
    }
}

@Composable
private fun WishRow(
    item: WishItem,
    inLibrary: Book?,
    onClick: () -> Unit,
    onToggleGot: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val c = Ink.colors
    val got = item.gotAt > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick)
            .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.title,
                fontFamily = InkFonts.serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                color = if (got) c.muted else c.ink,
                textDecoration = if (got) TextDecoration.LineThrough else null,
            )
            if (item.author.isNotEmpty()) Text(item.author, style = InkText.body, color = c.muted)
            if (item.note.isNotEmpty()) Text(item.note, style = InkText.small, color = c.faint, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (got) "Got it ${Format.shortDate(item.gotAt)}" else "Added ${Format.shortDate(item.addedAt)}",
                    style = InkText.small,
                    color = c.faint,
                )
                if (inLibrary != null) {
                    Text(
                        "IN YOUR LIBRARY",
                        style = InkText.small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.66.sp),
                        color = c.onInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.ink)
                            .clickable(role = Role.Button, onClickLabel = "Open book", onClick = onOpenBook)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
        // Round check: tap to move between "Want to read" and "Got it".
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Checkbox, onClick = onToggleGot)
                .semantics { contentDescription = if (got) "Move back to Want to read" else "Mark as got it" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(if (got) Modifier.background(c.ink) else Modifier.border(1.5.dp, c.ink, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (got) Icon(InkIcons.Check, null, Modifier.size(16.dp), tint = c.onInk)
            }
        }
    }
}

@Composable
private fun EmptyWishlist(tab: WishTab, onAdd: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(InkIcons.Heart, null, Modifier.size(40.dp), tint = c.ink)
        Spacer(Modifier.height(16.dp))
        Text(
            if (tab == WishTab.WANTED) "Books you want to read" else "Nothing ticked off yet",
            style = InkText.bookTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (tab == WishTab.WANTED) "Heard about a good book? Add its title and author so you don't forget it."
            else "When you get a book from your wishlist, tick it and it moves here.",
            style = InkText.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        if (tab == WishTab.WANTED) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Add a book", onAdd, Modifier.width(220.dp), icon = InkIcons.Plus)
        }
    }
}

@Composable
private fun WishDialog(
    initial: WishItem?,
    onSave: (title: String, author: String, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var author by rememberSaveable { mutableStateOf(initial?.author.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) focus.requestFocus() }
    val canSave = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        shape = RoundedCornerShape(8.dp),
        title = { Text(if (initial == null) "Add to wishlist" else "Edit book", style = InkText.bookTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InkField("Title", title, { title = it }, placeholder = "Book title", focusRequester = focus)
                InkField("Author", author, { author = it }, placeholder = "Optional")
                InkField("Note", note, { note = it }, placeholder = "Who recommended it, where to find it…", singleLine = false)
            }
        },
        confirmButton = {
            Text(
                "Save",
                style = InkText.button,
                color = if (canSave) c.ink else c.faint,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = canSave, role = Role.Button) { onSave(title, author, note) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    Text(
                        "Delete",
                        style = InkText.button.copy(fontWeight = FontWeight.Medium),
                        color = c.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button, onClick = onDelete)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
                Text(
                    "Cancel",
                    style = InkText.button.copy(fontWeight = FontWeight.Medium),
                    color = c.muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        },
    )
}
