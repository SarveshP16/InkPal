package com.inkpal.app.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Format
import com.inkpal.app.data.LibraryData
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.BookCover23
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.components.UnderlineTabs
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private enum class HistoryTab(val label: String) { ACTIVITY("Activity"), FINISHED("Finished") }

private class Entry(
    val time: Long,
    val bookId: String,
    val title: String,
    val detail: String,
    val amount: String,
    val icon: ImageVector,
)

@Composable
fun HistoryScreen(data: LibraryData, onOpen: (String) -> Unit) {
    val c = Ink.colors
    var tab by rememberSaveable { mutableStateOf(HistoryTab.ACTIVITY) }

    val stats = remember(data.sessions, data.books) { Stats.from(data) }
    val days = remember(data.sessions, data.books) { activityByDay(data) }
    val finished = remember(data.books) {
        data.books.filter { it.status == BookStatus.FINISHED }.sortedByDescending { it.finishedAt }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        item {
            Text("History", style = InkText.screenTitle, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        }
        item { StatsRow(stats) }
        item {
            UnderlineTabs(
                items = HistoryTab.entries.map { it to it.label },
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
        }
        when (tab) {
            HistoryTab.ACTIVITY -> {
                if (days.isEmpty()) {
                    item { EmptyText("Nothing logged yet. Your reading and listening will appear here, day by day.") }
                }
                days.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
                            SectionLabel(Format.dayLabel(day), Modifier.weight(1f))
                            val total = data.sessions.filter { Format.dayKey(it.startedAt) == day }.sumOf { it.durationMs }
                            if (total > 0) Text(Format.duration(total), style = InkText.small, color = c.muted)
                        }
                    }
                    items(entries, key = { "${it.bookId}-${it.time}" }) { e -> EntryRow(e) { onOpen(e.bookId) } }
                }
            }
            HistoryTab.FINISHED -> {
                if (finished.isEmpty()) item { EmptyText("Books you finish will be listed here.") }
                items(finished, key = { it.id }) { book ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpen(book.id) }
                            .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover23(book, Modifier.width(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(book.shortTitle, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(book.author, book.format.label).filter { it.isNotEmpty() }.joinToString(" · "), style = InkText.small, color = c.muted, maxLines = 1)
                        }
                        if (book.finishedAt > 0) Text(Format.shortDate(book.finishedAt), style = InkText.small, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = InkText.body, color = Ink.colors.muted, modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
private fun EntryRow(e: Entry, onClick: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).border(1.dp, c.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(e.icon, null, Modifier.size(16.dp), tint = c.ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.title, style = InkText.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.detail, style = InkText.small, color = c.muted, maxLines = 1)
        }
        if (e.amount.isNotEmpty()) Text(e.amount, style = InkText.small.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun StatsRow(stats: Stats) {
    val c = Ink.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .border(1.dp, c.ink, shape),
    ) {
        Stat(Format.duration(stats.weekMs).takeIf { stats.weekMs > 0 } ?: "0 min", "This week", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 0.dp).drawBehind { drawRect(c.rule) })
        Stat(stats.finishedThisYear.toString(), "Finished in ${LocalDate.now().year}", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().drawBehind { drawRect(c.rule) })
        Stat(if (stats.streakDays == 1) "1 day" else "${stats.streakDays} days", "Streak", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontFamily = InkFonts.serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = InkText.small, color = Ink.colors.muted, maxLines = 1)
    }
}

private class Stats(val weekMs: Long, val finishedThisYear: Int, val streakDays: Int) {
    companion object {
        fun from(data: LibraryData): Stats {
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekMs = data.sessions.filter { !Format.dayKey(it.startedAt).isBefore(weekStart) }.sumOf { it.durationMs }
            val finished = data.books.count { it.status == BookStatus.FINISHED && it.finishedAt > 0 && Format.dayKey(it.finishedAt).year == today.year }
            val activeDays = data.sessions.mapTo(HashSet()) { Format.dayKey(it.startedAt) }
            // A streak still counts if today hasn't been read yet but yesterday was.
            var day = if (today in activeDays) today else today.minusDays(1)
            var streak = 0
            while (day in activeDays) {
                streak++
                day = day.minusDays(1)
            }
            return Stats(weekMs, finished, streak)
        }
    }
}

/** Sessions plus "marked as finished" events, grouped by day, newest first. */
private fun activityByDay(data: LibraryData): List<Pair<LocalDate, List<Entry>>> {
    val entries = mutableListOf<Entry>()
    data.sessions.forEach { s ->
        val verb = if (s.format.isAudio) "Listened" else "Read"
        val range = listOf(s.fromLabel, s.toLabel).filter { it.isNotEmpty() }.distinct().joinToString("–")
        entries += Entry(
            time = s.startedAt,
            bookId = s.bookId,
            title = data.books.firstOrNull { it.id == s.bookId }?.shortTitle ?: s.bookTitle,
            detail = if (range.isNotEmpty()) "$verb · $range" else "$verb · ${(s.fromProgress * 100).toInt()}% → ${(s.toProgress * 100).toInt()}%",
            amount = Format.duration(s.durationMs),
            icon = if (s.format.isAudio) InkIcons.Headphones else InkIcons.BookOpen,
        )
    }
    data.books.filter { it.status == BookStatus.FINISHED && it.finishedAt > 0 }.forEach { b ->
        entries += Entry(b.finishedAt, b.id, b.shortTitle, "Marked as finished", "", InkIcons.Check)
    }
    return entries
        .sortedByDescending { it.time }
        .groupBy { Format.dayKey(it.time) }
        .toList()
}
