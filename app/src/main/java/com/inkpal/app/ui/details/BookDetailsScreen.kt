package com.inkpal.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Format
import com.inkpal.app.data.LibraryData
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.BookCover23
import com.inkpal.app.ui.components.Chip
import com.inkpal.app.ui.components.ConfirmDialog
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.ProgressLine
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.components.TextAction
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkText

@Composable
fun BookDetailsScreen(
    book: Book,
    data: LibraryData,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onToggleFinished: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = Ink.colors
    val audio = book.format.isAudio
    val folder = data.folders.firstOrNull { it.uri == book.folderUri }?.name.orEmpty()
    val sessions = remember(data.sessions, book.id) {
        data.sessions.filter { it.bookId == book.id }.sortedByDescending { it.startedAt }
    }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp)) {
            IconBtn(InkIcons.Back, "Back", onBack)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Cover and facts
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BookCover23(book, Modifier.width(116.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.shortTitle, style = InkText.bookTitleLarge)
                    if (book.subtitle.isNotEmpty()) {
                        Text(book.subtitle, style = InkText.body.copy(fontFamily = com.inkpal.app.ui.theme.InkFonts.serif), color = c.muted)
                    }
                    if (book.author.isNotEmpty()) Text(book.author, style = InkText.body.copy(fontSize = InkText.button.fontSize), color = c.muted)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(book.format.label)
                        when (book.status) {
                            BookStatus.READING -> Chip(if (audio) "Listening" else "Reading", filled = true)
                            BookStatus.FINISHED -> Chip("Finished", filled = true)
                            BookStatus.NEW -> Unit
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val facts = buildList {
                        add(book.fileName)
                        add(listOfNotNull(
                            Format.fileSize(book.sizeBytes).takeIf { book.sizeBytes > 0 },
                            Format.duration(book.durationMs).takeIf { book.durationMs > 0 },
                        ).joinToString(" · "))
                        if (folder.isNotEmpty()) add(folder)
                    }.filter { it.isNotEmpty() }
                    Text(facts.joinToString("\n"), style = InkText.small, color = c.faint)
                }
            }

            // Progress
            if (book.status != BookStatus.NEW || book.progress > 0f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (book.status == BookStatus.FINISHED) "Finished" else "${book.percent}% ${if (audio) "listened" else "read"}",
                            style = InkText.bodyStrong,
                            modifier = Modifier.weight(1f),
                        )
                        if (book.positionLabel.isNotEmpty()) Text(book.positionLabel, style = InkText.small, color = c.muted)
                    }
                    ProgressLine(if (book.status == BookStatus.FINISHED) 1f else book.progress, thickness = 4)
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (book.startedAt > 0) "Started ${Format.shortDate(book.startedAt)}" else "",
                            style = InkText.small,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                book.finishedAt > 0 -> "Finished ${Format.shortDate(book.finishedAt)}"
                                book.lastOpenedAt > 0 -> "Last ${if (audio) "listened" else "read"} ${Format.relativeDay(book.lastOpenedAt)}"
                                else -> ""
                            },
                            style = InkText.small,
                            color = c.muted,
                        )
                    }
                }
            }

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val verb = if (audio) "listening" else "reading"
                val primary = when {
                    book.status == BookStatus.FINISHED -> if (audio) "Listen again" else "Read again"
                    book.progress > 0f || book.startedAt > 0 -> "Continue $verb"
                    else -> "Start $verb"
                }
                PrimaryButton(primary, onOpen, Modifier.fillMaxWidth())
                OutlineButton(
                    if (book.status == BookStatus.FINISHED) "Mark as not finished" else "Mark as finished",
                    onToggleFinished,
                    Modifier.fillMaxWidth(),
                    icon = if (book.status == BookStatus.FINISHED) null else InkIcons.Check,
                )
            }

            // Sessions
            Column {
                SectionLabel(if (audio) "Listening sessions" else "Reading sessions", Modifier.padding(bottom = 6.dp))
                if (sessions.isEmpty()) {
                    Text(
                        "No sessions yet. Each time you ${if (audio) "listen" else "read"}, it's logged here.",
                        style = InkText.body,
                        color = c.muted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    sessions.take(5).forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(Format.dayAndTime(s.startedAt), style = InkText.body, modifier = Modifier.weight(1f))
                            val range = listOf(s.fromLabel, s.toLabel).filter { it.isNotEmpty() }.joinToString("–")
                            Text(
                                listOf(Format.duration(s.durationMs), range).filter { it.isNotEmpty() }.joinToString(" · "),
                                style = InkText.body,
                                color = c.muted,
                            )
                        }
                    }
                    if (sessions.size > 5) {
                        Text(
                            "${sessions.size} sessions in all",
                            style = InkText.small.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (book.status != BookStatus.NEW || book.progress > 0f) {
                    TextAction("Reset progress", InkIcons.Reset, { confirmReset = true })
                }
                TextAction("Remove from library", InkIcons.Trash, { confirmRemove = true })
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset progress?",
            text = "“${book.shortTitle}” goes back to the start and is marked as new. Your reading history stays.",
            confirm = "Reset",
            onConfirm = {
                confirmReset = false
                onReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove from library?",
            text = "The file stays on your phone, but InkPal won't show it again. You can bring removed books back in Settings.",
            confirm = "Remove",
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
