package com.inkpal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkpal.app.data.Book
import com.inkpal.app.data.Covers
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkFonts
import com.inkpal.app.ui.theme.InkText

private val ButtonShape = RoundedCornerShape(6.dp)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = InkText.label, color = Ink.colors.muted, modifier = modifier)
}

/** A 44dp icon-only button with a spoken label. */
@Composable
fun IconBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 22,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size.dp), tint = Ink.colors.ink)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Ink.colors
    Row(
        modifier
            .heightIn(min = 50.dp)
            .clip(ButtonShape)
            .background(if (enabled) c.ink else c.rule)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = c.onInk)
        Text(text, style = InkText.button, color = c.onInk)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Ink.colors
    val color = if (enabled) c.ink else c.faint
    Row(
        modifier
            .heightIn(min = 50.dp)
            .clip(ButtonShape)
            .border(1.dp, color, ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(text, style = InkText.button, color = color)
    }
}

/** Quiet text-and-icon action (e.g. "Reset progress"). */
@Composable
fun TextAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Ink.colors.muted)
        Text(text, style = InkText.body, color = Ink.colors.muted)
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, thickness: Int = 3) {
    val c = Ink.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(c.hairline),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(c.ink),
        )
    }
}

/** Text tabs with an underline on the selected one, over a hairline. */
@Composable
fun <T> UnderlineTabs(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(c.rule, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .drawBehind {
                        if (active) {
                            val y = size.height - 1.dp.toPx()
                            drawLine(c.ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = InkText.body.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (active) c.ink else c.muted,
                )
            }
        }
    }
}

/** Black-and-white switch from the mockups: filled when on, outlined when off. */
@Composable
fun InkSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) c.ink else c.paper)
            .border(if (checked) 0.dp else 1.5.dp, c.ink, CircleShape)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.paper else c.ink),
        )
    }
}

/** A full-width settings row with a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .drawBehind {
                drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = InkText.body)
            if (subtitle != null) Text(subtitle, style = InkText.small, color = c.muted)
        }
        InkSwitch(checked, onChange)
    }
}

/** A tappable settings row showing its current value on the right. */
@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = InkText.body, color = if (enabled) c.ink else c.faint)
            if (subtitle != null) Text(subtitle, style = InkText.small, color = c.muted)
        }
        Text(value, style = InkText.body, color = if (enabled) c.muted else c.faint)
        Icon(InkIcons.ChevronRight, null, Modifier.size(16.dp), tint = if (enabled) c.muted else c.faint)
    }
}

/** A labelled single-line text field with an underline, in the app's black-and-white style. */
@Composable
fun InkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = Ink.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = InkText.label, color = c.muted)
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(c.ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(vertical = 10.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = InkText.body.copy(fontSize = 16.sp), color = c.faint)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = InkText.body.copy(fontSize = 16.sp, color = c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

/** A thin black-and-white slider: 3dp track, round thumb. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val c = Ink.colors
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.ink),
            )
        },
        track = { state ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.rule),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(c.ink),
                )
            }
        },
    )
}

/** Small label chip; filled for status, outlined for format. */
@Composable
fun Chip(text: String, filled: Boolean = false) {
    val c = Ink.colors
    val shape = RoundedCornerShape(3.dp)
    Text(
        text.uppercase(),
        style = InkText.small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.66.sp),
        color = if (filled) c.onInk else c.ink,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(c.ink) else Modifier.border(1.dp, c.ink, shape))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * The book's cover image when the file has one; otherwise a plain typographic cover
 * (black or outlined white, chosen from the book id so it stays the same).
 */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, book.id, book.hasCover) {
        value = if (book.hasCover) Covers.load(context, book.id) else null
    }
    val shape = RoundedCornerShape(2.dp)
    val c = Ink.colors
    val img = image
    if (img != null) {
        Image(
            img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(shape)
                .border(BorderStroke(1.dp, c.rule), shape),
        )
    } else {
        val dark = book.id.firstOrNull()?.let { it.code % 2 == 0 } ?: true
        val bg = if (dark) c.ink else c.paper
        val fg = if (dark) c.onInk else c.ink
        BoxWithConstraints(
            modifier
                .clip(shape)
                .background(bg)
                .then(if (dark) Modifier else Modifier.border(1.dp, c.ink, shape)),
        ) {
            val w = maxWidth.value
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = (w * 0.085f).dp, vertical = (w * 0.095f).dp),
            ) {
                Text(
                    book.shortTitle,
                    color = fg,
                    fontFamily = InkFonts.serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = (w * 0.12f).coerceIn(9f, 30f).sp,
                    lineHeight = (w * 0.145f).coerceIn(11f, 34f).sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width((w * 0.2f).dp)
                        .height(1.dp)
                        .background(fg),
                )
                Spacer(Modifier.height((w * 0.055f).dp))
                Text(
                    book.author.ifEmpty { book.format.label }.uppercase(),
                    color = fg,
                    fontSize = (w * 0.068f).coerceIn(6f, 12f).sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A cover at the standard 2:3 book shape. */
@Composable
fun BookCover23(book: Book, modifier: Modifier = Modifier) {
    BookCover(book, modifier.aspectRatio(2f / 3f))
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title, style = InkText.bookTitle) },
        text = { Text(text, style = InkText.body, color = c.muted) },
        confirmButton = {
            Text(
                confirm,
                style = InkText.button,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { onConfirm() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
        dismissButton = {
            Text(
                "Cancel",
                style = InkText.button.copy(fontWeight = FontWeight.Medium),
                color = c.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
    )
}
