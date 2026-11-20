package com.inkpal.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkpal.app.data.Bookmark
import com.inkpal.app.data.Format
import com.inkpal.app.data.epub.EpubBook
import com.inkpal.app.data.epub.SearchHit
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Bookmarks

@Composable
internal fun BookmarkList(
    bookmarks: List<Bookmark>,
    onPick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    val c = Ink.colors
    if (bookmarks.isEmpty()) {
        Text(
            "No bookmarks yet. Open the menu on any page and tap the ribbon to save your place.",
            style = InkText.body,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(bookmarks.sortedBy { it.progress }, key = { _, b -> b.id }) { _, b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPick(b) }
                    .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(InkIcons.BookmarkFilled, null, Modifier.size(18.dp), tint = c.ink)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(b.label, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.snippet.isNotEmpty()) {
                        Text(
                            "“${b.snippet}”",
                            fontFamily = InkFonts.serif,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = c.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${(b.progress * 100).toInt()}% · ${Format.shortDate(b.createdAt)}", style = InkText.small, color = c.faint)
                }
                IconBtn(InkIcons.Close, "Delete bookmark", { onDelete(b) }, size = 18)
            }
        }
    }
}

/** Small ribbon in the top corner of a bookmarked page. */
@Composable
internal fun BookmarkRibbon(topInset: Dp, modifier: Modifier = Modifier) {
    Icon(
        InkIcons.BookmarkFilled,
        contentDescription = "This page is bookmarked",
        tint = Ink.colors.ink,
        modifier = modifier
            .padding(top = topInset + 4.dp, end = 20.dp)
            .size(width = 18.dp, height = 24.dp),
    )
}

// ---------------------------------------------------------------- Search

/** Search box text and results, kept while the panel is closed so reopening shows them. */
internal class SearchState {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var running by mutableStateOf(false)
    var results by mutableStateOf<List<SearchHit>>(emptyList())
}

@Composable
internal fun SearchPanel(
    epub: EpubBook,
    state: SearchState,
    topInset: Dp,
    onPick: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.submitted.isEmpty()) focus.requestFocus() }
    LaunchedEffect(state.submitted) {
        val q = state.submitted
        if (q.length < 2) {
            state.results = emptyList()
            return@LaunchedEffect
        }
        state.running = true
        val ctx = currentCoroutineContext()
        state.results = withContext(Dispatchers.IO) { epub.search(q) { ctx.isActive } }
        state.running = false
    }
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
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(InkIcons.Back, "Close search", onDismiss)
            Box(
                Modifier
                    .weight(1f)
                    .drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(vertical = 10.dp),
            ) {
                if (state.query.isEmpty()) Text("Search in this book", style = InkText.body.copy(fontSize = 16.sp), color = c.faint)
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    singleLine = true,
                    textStyle = InkText.body.copy(fontSize = 16.sp, color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { state.submitted = state.query.trim() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            IconBtn(InkIcons.Search, "Search", { state.submitted = state.query.trim() })
        }
        val status = when {
            state.running -> "Searching…"
            state.submitted.length < 2 -> "Type at least two letters, then search."
            state.results.isEmpty() -> "No matches for “${state.submitted}”."
            state.results.size >= 300 -> "Showing the first 300 matches."
            state.results.size == 1 -> "1 match"
            else -> "${state.results.size} matches"
        }
        Text(status, style = InkText.small, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.results) { i, hit ->
                val newChapter = i == 0 || state.results[i - 1].spineIndex != hit.spineIndex
                if (newChapter) {
                    SectionLabel(
                        epub.chapterTitle(hit.spineIndex),
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                Text(
                    highlight(hit.snippet, state.submitted, c.ink),
                    fontFamily = InkFonts.serif,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = c.muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(hit) }
                        .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** The snippet with each match in bold ink. */
private fun highlight(text: String, query: String, ink: Color) = buildAnnotatedString {
    val lower = text.lowercase()
    val q = query.lowercase()
    var i = 0
    while (q.isNotEmpty()) {
        val at = lower.indexOf(q, i)
        if (at < 0) break
        append(text.substring(i, at))
        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append(text.substring(at, at + q.length)) }
        i = at + q.length
    }
    append(text.substring(i))
}

// ---------------------------------------------------------------- Footnotes and links

/** A footnote shown over the page instead of jumping away. */
@Composable
internal fun NoteSheet(text: String?, onGoToNote: () -> Unit, onDismiss: () -> Unit) {
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
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(c.paper)
                .border(1.dp, c.ink, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .blockTouches()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Note")
            Text(
                text ?: "Loading…",
                fontFamily = InkFonts.serif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = c.ink,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Go to note", onGoToNote, Modifier.weight(1f))
                PrimaryButton("Close", onDismiss, Modifier.weight(1f))
            }
        }
    }
}

/** After following a link: a chip that returns to the page you came from. */
@Composable
internal fun BackChip(onBack: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.ink)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(InkIcons.Back, null, Modifier.size(16.dp), tint = c.onInk)
            Text("Back to where you were", style = InkText.small.copy(fontWeight = FontWeight.SemiBold), color = c.onInk)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Dismiss" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(InkIcons.Close, null, Modifier.size(14.dp), tint = c.onInk)
        }
    }
}
