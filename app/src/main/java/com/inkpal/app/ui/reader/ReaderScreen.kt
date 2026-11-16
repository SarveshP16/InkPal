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
import com.inkpal.app.data.Book
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

internal enum class Panel { NONE, CONTENTS, TEXT }

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
    val dark = data.settings.readerTheme == ReaderTheme.TRUE_BLACK
    val colors = if (dark) NightColors else PaperColors
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
                result == null -> Message("Opening…", null, onClose)
                result.isFailure -> Message(
                    "Can't open this book",
                    result.exceptionOrNull()?.message ?: "The file may have moved or be damaged.",
                    onClose,
                )
                else -> ReaderContent(app, book, data, result.getOrThrow(), dark, onClose)
            }
        }
    }
}

@Composable
private fun Message(title: String, detail: String?, onClose: () -> Unit) {
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
    dark: Boolean,
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
    val css = ReaderStyle.css(settings, dark, topPad, bottomPad)

    var menuOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var showEnd by remember { mutableStateOf(false) }
    var atEnd by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }

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

    // Reading session: from start/return to leaving the screen or the app.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, tracker) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.begin()
                Lifecycle.Event.ON_STOP -> tracker.end()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.end()
        }
    }

    // Full screen while reading.
    DisposableEffect(activity, dark) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    // Reader-only brightness.
    DisposableEffect(activity, settings.brightness) {
        val window = activity?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else settings.brightness.coerceIn(0.01f, 1f)
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Keep the screen on while reading, until nothing has happened for the idle timeout.
    LaunchedEffect(lastTouch, settings.keepScreenOn, settings.idleTimeoutMin) {
        idle = false
        if (settings.keepScreenOn && settings.idleTimeoutMin > 0) {
            delay(settings.idleTimeoutMin * 60_000L)
            idle = true
        }
    }
    DisposableEffect(settings.keepScreenOn, idle) {
        view.keepScreenOn = settings.keepScreenOn && !idle
        onDispose { view.keepScreenOn = false }
    }

    // Volume keys turn pages when nothing is open on top of the page.
    val latestSettings by rememberUpdatedState(settings)
    val overlayOpen by rememberUpdatedState(menuOpen || panel != Panel.NONE || showEnd)
    DisposableEffect(engine) {
        ReaderKeys.active = { latestSettings.volumeKeysTurnPages && !overlayOpen }
        ReaderKeys.turn = { forward ->
            touched()
            if (forward) engine.next() else engine.prev()
        }
        onDispose {
            ReaderKeys.active = null
            ReaderKeys.turn = null
        }
    }

    BackHandler {
        when {
            showEnd -> showEnd = false
            panel != Panel.NONE -> panel = Panel.NONE
            menuOpen -> menuOpen = false
            else -> onClose()
        }
    }

    val background = ReaderEngine.backgroundFor(dark)
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
                        when {
                            x < 0.3f -> engine.prev()
                            x > 0.7f -> engine.next()
                            else -> menuOpen = true
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
            )
        }
        when (panel) {
            Panel.CONTENTS -> ContentsPanel(
                epub = epub,
                currentIndex = engine.index,
                topInset = cutoutTop,
                onPick = { entry ->
                    panel = Panel.NONE
                    menuOpen = false
                    engine.jumpTo(entry)
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
