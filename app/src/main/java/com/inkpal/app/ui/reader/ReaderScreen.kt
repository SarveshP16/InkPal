package com.inkpal.app.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkpal.app.InkPalApp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.inkpal.app.data.Book
import com.inkpal.app.data.Bookmark
import kotlinx.coroutines.launch
import com.inkpal.app.data.InkStore
import com.inkpal.app.data.LibraryData
import com.inkpal.app.data.ReaderTheme
import com.inkpal.app.data.ReadingSession
import com.inkpal.app.data.epub.EpubBook
import com.inkpal.app.data.epub.EpubCache
import com.inkpal.app.ui.InkIcons
import com.inkpal.app.ui.components.OutlineButton
import com.inkpal.app.ui.components.PrimaryButton
import com.inkpal.app.ui.theme.Ink
import com.inkpal.app.ui.theme.InkPalTheme
import com.inkpal.app.ui.theme.InkText
import com.inkpal.app.ui.theme.NightColors
import com.inkpal.app.ui.theme.SepiaColors
import com.inkpal.app.ui.theme.PaperColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Volume-key page turns, forwarded from MainActivity while the reader is open. */
object ReaderKeys {
    var active: (() -> Boolean)? = null
    var turn: ((forward: Boolean) -> Unit)? = null
}

internal enum class Panel { NONE, CONTENTS, BOOKMARKS, SEARCH, TEXT }

/** A footnote pop-up: its text (null while loading) and the link it came from. */
internal class NoteState(val text: String?, val href: String)

/** Logs one stretch of reading between opening (or returning to) the book and leaving it. */
internal class SessionTracker(private val store: InkStore, private val book: Book) {
    var progress: Float = book.progress
    var label: String = book.positionLabel
    private var startedAt = 0L
    private var fromProgress = 0f
    private var fromLabel = ""

    val elapsedMs: Long get() = if (startedAt == 0L) 0 else System.currentTimeMillis() - startedAt

    fun begin() {
        startedAt = System.currentTimeMillis()
        fromProgress = progress
        fromLabel = label
    }

    fun end() {
        if (startedAt == 0L) return
        val now = System.currentTimeMillis()
        if (now - startedAt >= 15_000 || abs(progress - fromProgress) > 0.0005f) {
            store.addSession(
                ReadingSession(
                    id = UUID.randomUUID().toString(),
                    bookId = book.id,
                    bookTitle = book.shortTitle,
                    format = book.format,
                    startedAt = startedAt,
                    endedAt = now,
                    fromProgress = fromProgress,
                    toProgress = progress,
                    fromLabel = fromLabel,
                    toLabel = label,
                ),
            )
        }
        startedAt = 0L
    }
}

@Composable
fun ReaderScreen(app: InkPalApp, bookId: String, onClose: () -> Unit) {
    val data by app.store.data.collectAsStateWithLifecycle()
    val book = data.books.firstOrNull { it.id == bookId }
    val theme = data.settings.readerTheme
    val dark = theme == ReaderTheme.TRUE_BLACK
    val colors = when (theme) {
        ReaderTheme.WHITE -> PaperColors
        ReaderTheme.SEPIA -> SepiaColors
        ReaderTheme.TRUE_BLACK -> NightColors
    }
    val context = LocalContext.current

    var opened by remember(bookId) { mutableStateOf<Result<EpubBook>?>(null) }
    LaunchedEffect(bookId) {
        val b = book ?: return@LaunchedEffect
        opened = withContext(Dispatchers.IO) { runCatching { EpubBook.open(EpubCache.fileFor(context, b)) } }
    }
    DisposableEffect(bookId) {
        onDispose { opened?.getOrNull()?.close() }
    }

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
                    "Can't open this book",
                    result.exceptionOrNull()?.message ?: "The file may have moved or be damaged.",
                    onClose,
                )
                else -> ReaderContent(app, book, data, result.getOrThrow(), theme, onClose)
            }
        }
    }
}

@Composable
internal fun ReaderMessage(title: String, detail: String?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = InkText.bookTitle, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, style = InkText.body, color = Ink.colors.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlineButton("Back to library", onClose, Modifier.width(220.dp))
        }
    }
}

@Composable
private fun ReaderContent(
    app: InkPalApp,
    book: Book,
    data: LibraryData,
    epub: EpubBook,
    theme: ReaderTheme,
    onClose: () -> Unit,
) {
    val store = app.store
    val settings = data.settings
    val c = Ink.colors
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    val cutoutTop = with(density) { WindowInsets.displayCutout.getTop(this).toDp() }
    val topPad = (cutoutTop.value + 36f).roundToInt()
    val bottomPad = 40
    val css = ReaderStyle.css(settings, theme, topPad, bottomPad)
    val dark = theme == ReaderTheme.TRUE_BLACK

    var menuOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var showEnd by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf<NoteState?>(null) }
    val search = remember(epub) { SearchState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bookmarks = data.bookmarks.filter { it.bookId == book.id }

    val tracker = remember(book.id) { SessionTracker(store, book) }
    val engine = remember(epub) {
        ReaderEngine(
            epub,
            onPosition = { i, f ->
                val p = epub.progressOf(i, f)
                val label = epub.chapterTitle(i)
                tracker.progress = p
                tracker.label = label
                store.savePosition(book.id, p, "$i:$f", label)
            },
            onEnd = {
                atEnd = true
                showEnd = true
            },
        )
    }
    DisposableEffect(engine) { onDispose { engine.release() } }
    LaunchedEffect(css) { engine.setCss(css) }

    val touched = { lastTouch = System.currentTimeMillis() }
    val bookmarked = engine.ready && bookmarks.any { engine.isOnPage(it.position) }

    val toggleBookmark: () -> Unit = {
        val here = bookmarks.filter { engine.isOnPage(it.position) }
        if (here.isNotEmpty()) {
            here.forEach { store.removeBookmark(it.id) }
        } else {
            val position = engine.position
            val progress = engine.progress
            val label = epub.chapterTitle(engine.index)
            engine.snippet { text ->
                store.addBookmark(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        position = position,
                        progress = progress,
                        label = label,
                        snippet = text,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** A tapped link: footnotes pop up, book links jump (with a way back), web links open outside. */
    val openLink: (ReaderEngine.Link) -> Unit = { link ->
        val href = link.href
        when {
            href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                }
            }
            link.isNote -> {
                if (link.text.isNotEmpty()) {
                    note = NoteState(link.text, href)
                } else {
                    note = NoteState(null, href)
                    val (path, id) = engine.resolveLink(href)
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { id?.let { epub.noteText(path, it) } }
                        if (note?.href == href) {
                            if (text != null) note = NoteState(text, href)
                            else {
                                note = null
                                engine.followLink(href)
                            }
                        }
                    }
                }
            }
            else -> engine.followLink(href)
        }
    }

    ReaderEffects(
        tracker = tracker,
        settings = settings,
        theme = theme,
        lastTouch = lastTouch,
        overlayOpen = menuOpen || panel != Panel.NONE || showEnd || note != null,
        onTurn = { forward ->
            touched()
            if (forward) engine.next() else engine.prev()
        },
    )

    BackHandler {
        when {
            note != null -> note = null
            showEnd -> showEnd = false
            panel != Panel.NONE -> panel = Panel.NONE
            menuOpen -> menuOpen = false
            else -> onClose()
        }
    }

    val background = ReaderEngine.backgroundFor(theme)
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> engine.createView(ctx, background).also { engine.start(book.position, css) } },
            update = {
                it.setBackgroundColor(background)
                it.alpha = if (engine.ready) 1f else 0f
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Page turns: tap the edges or swipe; tap the middle for the menu.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    detectTapGestures { pos ->
                        touched()
                        val x = pos.x / size.width
                        // A link under the finger wins over the page-turn zones.
                        engine.linkAt(pos.x / density.density, pos.y / density.density) { link ->
                            when {
                                link != null -> openLink(link)
                                x < 0.3f -> engine.prev()
                                x > 0.7f -> engine.next()
                                else -> menuOpen = true
                            }
                        }
                    }
                }
                .pointerInput(engine) {
                    var dx = 0f
                    val threshold = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            touched()
                            if (dx < -threshold) engine.next() else if (dx > threshold) engine.prev()
                        },
                    ) { change, amount ->
                        change.consume()
                        dx += amount
                    }
                },
        )

        // Running header and footer.
        Text(
            book.shortTitle,
            style = InkText.small,
            color = c.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = cutoutTop + 12.dp, start = 48.dp, end = 48.dp),
        )
        if (engine.ready) {
            val left = engine.pageCount - 1 - engine.page
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    epub.chapterTitle(engine.index),
                    style = InkText.small,
                    color = c.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (left) {
                        0 -> "Last page in chapter"
                        1 -> "1 page left in chapter"
                        else -> "$left pages left in chapter"
                    },
                    style = InkText.small,
                    color = c.faint,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text("${(engine.progress * 100).toInt()}%", style = InkText.small, color = c.faint)
            }
        }

        if (bookmarked) BookmarkRibbon(cutoutTop, Modifier.align(Alignment.TopEnd))
        val back = engine.backPosition
        if (back != null && !menuOpen && panel == Panel.NONE) {
            BackChip(
                onBack = { engine.goBack() },
                onDismiss = { engine.dismissBack() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            )
        }

        if (menuOpen && panel == Panel.NONE && !showEnd) {
            ReaderMenu(
                book = book,
                epub = epub,
                engine = engine,
                topInset = cutoutTop,
                onDismiss = { menuOpen = false },
                onClose = onClose,
                onContents = { panel = Panel.CONTENTS },
                onText = { panel = Panel.TEXT },
                onFinish = {
                    menuOpen = false
                    atEnd = false
                    showEnd = true
                },
                bookmarked = bookmarked,
                onToggleBookmark = toggleBookmark,
                onBookmarks = { panel = Panel.BOOKMARKS },
                onSearch = { panel = Panel.SEARCH },
            )
        }
        when (panel) {
            Panel.CONTENTS, Panel.BOOKMARKS -> ContentsPanel(
                epub = epub,
                currentIndex = engine.index,
                topInset = cutoutTop,
                onPick = { entry ->
                    panel = Panel.NONE
                    menuOpen = false
                    engine.jumpTo(entry)
                },
                onDismiss = { panel = Panel.NONE },
                bookmarks = bookmarks,
                startOnBookmarks = panel == Panel.BOOKMARKS,
                onPickBookmark = { b ->
                    panel = Panel.NONE
                    menuOpen = false
                    engine.jumpToPosition(b.position)
                },
                onDeleteBookmark = { b -> store.removeBookmark(b.id) },
            )
            Panel.SEARCH -> SearchPanel(
                epub = epub,
                state = search,
                topInset = cutoutTop,
                onPick = { hit ->
                    panel = Panel.NONE
                    menuOpen = false
                    engine.jumpToHit(hit.spineIndex, search.submitted, hit.occurrence)
                },
                onDismiss = { panel = Panel.NONE },
            )
            Panel.TEXT -> TextPanel(
                settings = settings,
                onChange = { change -> store.updateSettings(change) },
                onDismiss = { panel = Panel.NONE },
            )
            Panel.NONE -> Unit
        }
        note?.let { n ->
            NoteSheet(
                text = n.text,
                onGoToNote = {
                    note = null
                    engine.followLink(n.href)
                },
                onDismiss = { note = null },
            )
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

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
