package com.inkpal.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkpal.app.InkPalApp
import com.inkpal.app.data.Book
import com.inkpal.app.data.BookStatus
import com.inkpal.app.data.Bookmark
import com.inkpal.app.data.LibraryData
import com.inkpal.app.data.ReaderTheme
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.IconBtn
import com.inkpal.app.ui.components.InkSlider
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkPalTheme
import com.inkpal.app.ui.theme.InkText
import com.inkpal.app.ui.theme.NightColors
import com.inkpal.app.ui.theme.PaperColors
import com.inkpal.app.ui.theme.SepiaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * An open PDF. PdfRenderer is not thread-safe, so every render (and the final close) runs on
 * one background thread. Recent pages are kept in a small memory cache.
 */
class PdfDoc private constructor(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    @Volatile private var closed = false
    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Renders page [index] [width] pixels wide on white paper. */
    suspend fun render(index: Int, width: Int): ImageBitmap? = withContext(dispatcher) {
        if (closed || index !in 0 until pageCount) return@withContext null
        val key = "$index@$width"
        cache.get(key)?.let { return@withContext it.asImageBitmap() }
        renderer.openPage(index).use { page ->
            val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 6000)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key, bitmap)
            bitmap.asImageBitmap()
        }
    }

    override fun close() {
        closed = true
        executor.execute {
            try {
                renderer.close()
                pfd.close()
            } catch (_: Exception) {
            }
        }
        executor.shutdown()
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDoc {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("The file can't be opened")
            return try {
                PdfDoc(pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                throw IllegalStateException("This PDF can't be opened (it may be password-protected or damaged)")
            }
        }
    }
}

private enum class PdfPanel { NONE, BOOKMARKS }

@Composable
fun PdfReaderScreen(app: InkPalApp, bookId: String, onClose: () -> Unit) {
    val data by app.store.data.collectAsStateWithLifecycle()
    val book = data.books.firstOrNull { it.id == bookId }
    val theme = data.settings.readerTheme
    val colors = when (theme) {
        ReaderTheme.WHITE -> PaperColors
        ReaderTheme.SEPIA -> SepiaColors
        ReaderTheme.TRUE_BLACK -> NightColors
    }
    val context = LocalContext.current

    var opened by remember(bookId) { mutableStateOf<Result<PdfDoc>?>(null) }
    LaunchedEffect(bookId) {
        val b = book ?: return@LaunchedEffect
        opened = withContext(Dispatchers.IO) { runCatching { PdfDoc.open(context, Uri.parse(b.uri)) } }
    }
    DisposableEffect(bookId) { onDispose { opened?.getOrNull()?.close() } }

    InkPalTheme(colors) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.paper),
        ) {
            val result = opened
            when {
                book == null -> LaunchedEffect(Unit) { onClose() }
                result == null -> ReaderMessage("Opening…", null, onClose)
                result.isFailure -> ReaderMessage(
                    "Can't open this PDF",
                    result.exceptionOrNull()?.message ?: "The file may have moved or be damaged.",
                    onClose,
                )
                else -> PdfContent(app, book, data, result.getOrThrow(), theme, onClose)
            }
        }
    }
}

@Composable
private fun PdfContent(
    app: InkPalApp,
    book: Book,
    data: LibraryData,
    doc: PdfDoc,
    theme: ReaderTheme,
    onClose: () -> Unit,
) {
    val store = app.store
    val settings = data.settings
    val c = Ink.colors
    val density = LocalDensity.current
    val n = doc.pageCount.coerceAtLeast(1)
    val cutoutTop = with(density) { WindowInsets.displayCutout.getTop(this).toDp() }

    var page by remember { mutableIntStateOf(book.position.toIntOrNull()?.coerceIn(0, n - 1) ?: 0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(PdfPanel.NONE) }
    var showEnd by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val touched = { lastTouch = System.currentTimeMillis() }

    fun progressOf(p: Int) = if (n <= 1) 0f else p.toFloat() / (n - 1)
    val tracker = remember(book.id) { SessionTracker(store, book) }
    LaunchedEffect(page) {
        val label = "Page ${page + 1} of $n"
        tracker.progress = progressOf(page)
        tracker.label = "Page ${page + 1}"
        store.savePosition(book.id, progressOf(page), page.toString(), label)
        scale = 1f
        offset = Offset.Zero
    }

    val next = {
        if (page < n - 1) page++ else {
            atEnd = true
            showEnd = true
        }
    }
    val prev = { if (page > 0) page-- }

    val bookmarks = data.bookmarks.filter { it.bookId == book.id }
    val bookmarked = bookmarks.any { it.position == page.toString() }
    val toggleBookmark = {
        val here = bookmarks.filter { it.position == page.toString() }
        if (here.isNotEmpty()) here.forEach { store.removeBookmark(it.id) }
        else store.addBookmark(
            Bookmark(
                id = UUID.randomUUID().toString(),
                bookId = book.id,
                position = page.toString(),
                progress = progressOf(page),
                label = "Page ${page + 1}",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    ReaderEffects(
        tracker = tracker,
        settings = settings,
        theme = theme,
        lastTouch = lastTouch,
        overlayOpen = menuOpen || panel != PdfPanel.NONE || showEnd,
        onTurn = { forward ->
            touched()
            if (forward) next() else prev()
        },
    )

    BackHandler {
        when {
            showEnd -> showEnd = false
            panel != PdfPanel.NONE -> panel = PdfPanel.NONE
            menuOpen -> menuOpen = false
            scale > 1.01f -> {
                scale = 1f
                offset = Offset.Zero
            }
            else -> onClose()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val heightPx = constraints.maxHeight.coerceAtLeast(1)
        // Sharper render once zoomed in; the base render is cached for quick page turns.
        val renderWidth = if (scale > 1.6f) (widthPx * 2).coerceAtMost(2600) else widthPx
        var shown by remember { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
        LaunchedEffect(page, renderWidth) {
            doc.render(page, renderWidth)?.let { shown = page to it }
            if (renderWidth == widthPx) doc.render(page + 1, widthPx)
        }

        fun clamp(o: Offset, s: Float): Offset {
            val maxX = (s - 1f) * widthPx / 2f
            val maxY = (s - 1f) * heightPx / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        val transform = rememberTransformableState { zoom, pan, _ ->
            val s = (scale * zoom).coerceIn(1f, 4f)
            scale = s
            offset = clamp(offset + pan, s)
        }

        shown?.let { (_, bitmap) ->
            Image(
                bitmap,
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = pageColorFilter(theme),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = cutoutTop)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .transformable(transform, canPan = { scale > 1.01f })
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { pos ->
                            touched()
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val s = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                scale = s
                                offset = clamp((center - pos) * (s - 1f), s)
                            }
                        },
                        onTap = { pos ->
                            touched()
                            val x = pos.x / size.width
                            when {
                                scale > 1.01f -> menuOpen = true
                                x < 0.3f -> prev()
                                x > 0.7f -> next()
                                else -> menuOpen = true
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var dx = 0f
                    val threshold = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            touched()
                            if (scale <= 1.01f) {
                                if (dx < -threshold) next() else if (dx > threshold) prev()
                            }
                        },
                    ) { change, amount ->
                        if (scale <= 1.01f) {
                            change.consume()
                            dx += amount
                        }
                    }
                },
        )

        if (scale <= 1.01f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Page ${page + 1} of $n", style = InkText.small, color = c.faint, modifier = Modifier.weight(1f))
                Text("${(progressOf(page) * 100).toInt()}%", style = InkText.small, color = c.faint)
            }
        }
        if (bookmarked && scale <= 1.01f) BookmarkRibbon(cutoutTop, Modifier.align(Alignment.TopEnd))

        if (menuOpen && panel == PdfPanel.NONE && !showEnd) {
            PdfMenu(
                book = book,
                page = page,
                pageCount = n,
                topInset = cutoutTop,
                bookmarked = bookmarked,
                theme = theme,
                onJump = { page = it },
                onDismiss = { menuOpen = false },
                onClose = onClose,
                onToggleBookmark = toggleBookmark,
                onBookmarks = { panel = PdfPanel.BOOKMARKS },
                onTheme = {
                    val all = ReaderTheme.entries
                    store.updateSettings { s -> s.copy(readerTheme = all[(all.indexOf(s.readerTheme) + 1) % all.size]) }
                },
                onFinish = {
                    menuOpen = false
                    atEnd = false
                    showEnd = true
                },
            )
        }
        if (panel == PdfPanel.BOOKMARKS) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(c.paper)
                    .blockTouches()
                    .padding(top = cutoutTop),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bookmarks", style = InkText.bookTitleLarge, modifier = Modifier.weight(1f))
                    IconBtn(InkIcons.Close, "Close", { panel = PdfPanel.NONE })
                }
                BookmarkList(
                    bookmarks = bookmarks,
                    onPick = { b ->
                        panel = PdfPanel.NONE
                        menuOpen = false
                        b.position.toIntOrNull()?.let { page = it.coerceIn(0, n - 1) }
                    },
                    onDelete = { b -> store.removeBookmark(b.id) },
                )
            }
        }
        if (showEnd) {
            EndOfBook(
                book = book,
                atEnd = atEnd,
                sessions = data.sessions.filter { it.bookId == book.id },
                currentSessionMs = tracker.elapsedMs,
                topInset = cutoutTop,
                onMarkFinished = { store.markFinished(book.id) },
                onUndo = { store.markNotFinished(book.id) },
                onKeepReading = { showEnd = false },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PdfMenu(
    book: Book,
    page: Int,
    pageCount: Int,
    topInset: Dp,
    bookmarked: Boolean,
    theme: ReaderTheme,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onTheme: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = Ink.colors
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onDismiss),
    ) {
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
            IconBtn(
                if (bookmarked) InkIcons.BookmarkFilled else InkIcons.Bookmark,
                if (bookmarked) "Remove bookmark" else "Bookmark this page",
                onToggleBookmark,
            )
        }
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
            val last = (pageCount - 1).coerceAtLeast(1)
            val shownPage = dragging?.let { (it * last).roundToInt() } ?: page
            Row {
                Text("Page ${shownPage + 1} of $pageCount", style = InkText.bodyStrong, modifier = Modifier.weight(1f))
                Text("${(shownPage * 100f / last).toInt()}%", style = InkText.small, color = c.muted)
            }
            InkSlider(
                value = dragging ?: (page.toFloat() / last),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onJump((it * last).roundToInt().coerceIn(0, pageCount - 1)) }
                    dragging = null
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(c.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PdfAction("Bookmarks", { onBookmarks() })
                PdfAction("Theme: ${theme.label}", onTheme)
                PdfAction(if (book.status == BookStatus.FINISHED) "Finished" else "Mark finished", onFinish)
            }
            Text(
                "Pinch or double-tap to zoom.",
                style = InkText.small,
                color = c.faint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PdfAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = InkText.small.copy(fontSize = 13.sp),
        color = Ink.colors.ink,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    )
}
