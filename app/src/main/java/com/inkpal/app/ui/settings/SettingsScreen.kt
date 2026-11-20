package com.inkpal.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inkpal.app.BuildConfig
import com.inkpal.app.data.BookFormat
import com.inkpal.app.data.Format
import com.inkpal.app.data.InkStore
import com.inkpal.app.data.LibraryData
import com.inkpal.app.data.LibraryFolder
import com.inkpal.app.data.ReaderTheme
import com.inkpal.app.data.ScanState
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.ConfirmDialog
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.components.SectionLabel
import com.inkpal.app.ui.components.SwitchRow
import com.inkpal.app.ui.components.ValueRow
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkText

private val IdleOptions = listOf(2, 5, 10, 15, 30, 0)
private val SpeedOptions = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

private fun idleLabel(min: Int) = if (min == 0) "Never" else "$min min"
private fun speedLabel(speed: Float): String {
    val number = if (speed % 1f == 0f) "%.1f".format(speed) else speed.toString()
    return "$number×"
}

@Composable
fun SettingsScreen(
    data: LibraryData,
    scan: ScanState,
    store: InkStore,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
) {
    val c = Ink.colors
    val s = data.settings
    var folderToRemove by remember { mutableStateOf<LibraryFolder?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Text("Settings", style = InkText.screenTitle, modifier = Modifier.padding(top = 6.dp))

        // Library folders
        Column {
            SectionLabel("Library folders", Modifier.padding(bottom = 4.dp))
            if (data.folders.isEmpty()) {
                Text("No folders yet. Add the folders that hold your books.", style = InkText.body, color = c.muted, modifier = Modifier.padding(vertical = 10.dp))
            }
            data.folders.forEach { folder ->
                val inFolder = data.books.filter { it.folderUri == folder.uri }
                val epubs = inFolder.count { it.format == BookFormat.EPUB }
                val m4bs = inFolder.count { it.format == BookFormat.M4B }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(InkIcons.Folder, null, Modifier.size(20.dp), tint = c.ink)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(folder.name, style = InkText.bodyStrong)
                        Text(
                            listOfNotNull(
                                "$epubs EPUB".takeIf { epubs > 0 },
                                "$m4bs M4B".takeIf { m4bs > 0 },
                            ).joinToString(" · ").ifEmpty { "No books" },
                            style = InkText.small,
                            color = c.muted,
                        )
                    }
                    IconBtn(InkIcons.Close, "Stop using ${folder.name}", { folderToRemove = folder }, size = 18)
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Add folder", onAddFolder, Modifier.weight(1f), icon = InkIcons.Plus)
                OutlineButton(
                    if (scan.running) "Scanning…" else "Rescan",
                    onRescan,
                    Modifier.weight(1f),
                    icon = InkIcons.Refresh,
                    enabled = !scan.running && data.folders.isNotEmpty(),
                )
            }
            Text(
                if (data.lastScanAt > 0) {
                    "Last scan ${Format.relativeDay(data.lastScanAt)}, ${Format.clock(data.lastScanAt)} · ${data.books.size} ${if (data.books.size == 1) "book" else "books"}"
                } else "Not scanned yet",
                style = InkText.small,
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (data.hidden.isNotEmpty()) {
                ValueRow(
                    title = "Removed books",
                    value = "Restore ${data.hidden.size}",
                    subtitle = "Books you removed stay hidden on rescan",
                    onClick = {
                        store.restoreHidden()
                        onRescan()
                    },
                )
            }
        }

        // Reading
        Column {
            SectionLabel("Reading")
            SwitchRow("Turn pages with volume keys", s.volumeKeysTurnPages, { v -> store.updateSettings { it.copy(volumeKeysTurnPages = v) } })
            SwitchRow("Keep screen on while reading", s.keepScreenOn, { v -> store.updateSettings { it.copy(keepScreenOn = v) } })
            Box {
                var open by remember { mutableStateOf(false) }
                ValueRow(
                    title = "Turn screen off when idle",
                    value = idleLabel(s.idleTimeoutMin),
                    subtitle = "After this long without a page turn",
                    onClick = { open = true },
                    enabled = s.keepScreenOn,
                )
                OptionsMenu(open, { open = false }, IdleOptions, s.idleTimeoutMin, ::idleLabel) { v ->
                    store.updateSettings { it.copy(idleTimeoutMin = v) }
                }
            }
            Box {
                var open by remember { mutableStateOf(false) }
                ValueRow("Default audiobook speed", speedLabel(s.audioSpeed), { open = true })
                OptionsMenu(open, { open = false }, SpeedOptions, s.audioSpeed, ::speedLabel) { v ->
                    store.updateSettings { it.copy(audioSpeed = v) }
                }
            }
        }

        // Appearance
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Reader theme")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    ThemeOption(theme.label, theme.note, s.readerTheme == theme, theme, Modifier.weight(1f)) {
                        store.updateSettings { it.copy(readerTheme = theme) }
                    }
                }
            }
            Text(
                "Sepia is easier on the eyes at night. True black uses less power on OLED screens.",
                style = InkText.small,
                color = c.muted,
            )
        }

        Text("InkPal ${BuildConfig.VERSION_NAME}", style = InkText.small, color = c.faint)
    }

    folderToRemove?.let { folder ->
        ConfirmDialog(
            title = "Stop using ${folder.name}?",
            text = "Its books leave your library. The files stay on your phone and your reading history is kept.",
            confirm = "Remove folder",
            onConfirm = {
                folderToRemove = null
                onRemoveFolder(folder.uri)
            },
            onDismiss = { folderToRemove = null },
        )
    }
}

@Composable
private fun <T> OptionsMenu(
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
private fun ThemeOption(title: String, subtitle: String, selected: Boolean, theme: ReaderTheme, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(6.dp)
    val (bg, fg) = com.inkpal.app.ui.theme.readerSwatch(theme)
    val dark = theme != ReaderTheme.WHITE
    Column(
        modifier
            .heightIn(min = 60.dp)
            .then(if (selected) Modifier.border(2.dp, c.ink, shape).padding(3.dp) else Modifier.padding(3.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(if (!dark && !selected) Modifier.border(1.dp, c.rule, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = InkText.bodyStrong, color = fg)
        Text(if (selected) "Selected" else subtitle, style = InkText.small, color = fg)
    }
}
