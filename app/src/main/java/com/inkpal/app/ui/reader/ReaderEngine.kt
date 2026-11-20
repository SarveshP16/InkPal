package com.inkpal.app.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inkpal.app.R
import com.inkpal.app.data.AppSettings
import com.inkpal.app.data.ReaderFont
import com.inkpal.app.data.ReaderTheme
import com.inkpal.app.data.epub.EpubBook
import com.inkpal.app.data.epub.TocEntry
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

/** The reader's text settings as CSS. Sizes are CSS px, which equal dp in the WebView. */
object ReaderStyle {
    val sizes = listOf(15, 16, 17, 19, 21, 23, 26)
    private val spacings = listOf(1.4f, 1.6f, 1.9f)
    private val margins = listOf(16, 28, 40)

    /** Background and text colours of the page for each reader theme. */
    fun pageColors(theme: ReaderTheme): Pair<String, String> = when (theme) {
        ReaderTheme.WHITE -> "#FFFFFF" to "#111111"
        ReaderTheme.SEPIA -> "#F4ECD8" to "#2E2416"
        ReaderTheme.TRUE_BLACK -> "#000000" to "#C8C8C8"
    }

    fun css(s: AppSettings, theme: ReaderTheme, topPx: Int, bottomPx: Int): String {
        val m = margins[s.margins.coerceIn(0, margins.lastIndex)]
        val size = sizes[s.textSize.coerceIn(0, sizes.lastIndex)]
        val lh = spacings[s.lineSpacing.coerceIn(0, spacings.lastIndex)]
        val (bg, fg) = pageColors(theme)
        val family = when (s.readerFont) {
            ReaderFont.SERIF -> "'InkLiterata', serif"
            ReaderFont.SANS -> "sans-serif"
            ReaderFont.PUBLISHER -> null
        }
        val inheritFont = if (family != null) "font-family: inherit !important;" else ""
        // The page is the html element laid out in columns one screen wide; the gap equals
        // both margins, so page N starts exactly at N * screen width.
        return """
            @font-face { font-family: 'InkLiterata'; font-style: normal; font-weight: 200 900; src: url('$BASE$FONT_DIR/literata.ttf'); }
            @font-face { font-family: 'InkLiterata'; font-style: italic; font-weight: 200 900; src: url('$BASE$FONT_DIR/literata_italic.ttf'); }
            mark.inkpal-hit { background: $fg !important; color: $bg !important; border-radius: 2px; }
            :root { margin: 0 !important; padding: ${topPx}px ${m}px ${bottomPx}px ${m}px !important;
              width: 100vw !important; height: 100vh !important; box-sizing: border-box !important;
              column-count: 1 !important; column-gap: ${2 * m}px !important; column-fill: auto !important;
              overflow: hidden !important; background: $bg !important; -webkit-text-size-adjust: 100% !important; }
            body { margin: 0 !important; padding: 0 !important; background: transparent !important; color: $fg !important;
              font-size: ${size}px !important; line-height: $lh !important; ${if (family != null) "font-family: $family !important;" else ""}
              overflow-wrap: break-word; widows: 2; orphans: 2; }
            body * { color: inherit !important; background-color: transparent !important; border-color: currentColor !important; $inheritFont }
            p, li, div, span, blockquote { line-height: inherit !important; }
            img, svg, video { max-width: 100% !important; max-height: calc(100vh - ${topPx + bottomPx}px) !important;
              height: auto; object-fit: contain; break-inside: avoid; }
            a { text-decoration: underline; }
        """.trimIndent().replace("\n", " ")
    }
}

/** Runs inside each chapter: counts pages, scrolls to a page, finds anchors. */
private const val READER_JS = """
(function(){
var W=function(){return window.innerWidth;};
var se=function(){return document.scrollingElement||document.documentElement;};
window.InkPal={
 count:function(){return Math.max(1,Math.round(se().scrollWidth/W()));},
 go:function(p){se().scrollLeft=p*W();},
 setStyle:function(css){var s=document.getElementById('inkpal-style');if(s){s.textContent=css;}
   requestAnimationFrame(function(){requestAnimationFrame(InkPal.report);});},
 pageOfAnchor:function(id){var el=document.getElementById(id);
   if(!el){var n=document.getElementsByName(id);el=n.length?n[0]:null;}
   if(!el){return 0;}
   return Math.max(0,Math.floor((el.getBoundingClientRect().left+se().scrollLeft)/W()));},
 report:function(){InkPalBridge.onLayout(InkPal.count());}
};
window.addEventListener('load',function(){setTimeout(InkPal.report,30);});
window.addEventListener('resize',function(){setTimeout(InkPal.report,30);});
})();
"""

private const val HOST = "book.inkpal"
private const val BASE = "https://$HOST/"
/** App files (the bundled reading font) are served under this path, outside any book's paths. */
private const val FONT_DIR = "__inkpal__"

/**
 * Shows an EPUB one page at a time in a WebView. Each chapter is laid out in screen-wide
 * columns; a page turn just moves the scroll position, so nothing animates or reflows.
 *
 * [onPosition] fires after every move with the chapter index and the position inside it
 * (0..1); [onEnd] fires when the reader tries to go past the last page.
 */
class ReaderEngine(
    private val epub: EpubBook,
    private val onPosition: (index: Int, fraction: Float) -> Unit,
    private val onEnd: () -> Unit,
) {
    var index by mutableIntStateOf(0)
        private set
    var page by mutableIntStateOf(0)
        private set
    var pageCount by mutableIntStateOf(1)
        private set
    /** False while a chapter loads or re-lays out; taps are ignored and the view is hidden. */
    var ready by mutableStateOf(false)
        private set

    private sealed interface Target {
        data class Fraction(val value: Float) : Target
        data object Last : Target
        data class Anchor(val id: String) : Target
    }

    private var pending: Target = Target.Fraction(0f)
    @Volatile private var css: String = ""
    private var webView: WebView? = null

    val fraction: Float
        get() = if (pageCount <= 1) 0f else page.toFloat() / (pageCount - 1)

    val progress: Float get() = epub.progressOf(index, fraction)

    @SuppressLint("SetJavaScriptEnabled")
    fun createView(context: Context, background: Int): WebView = WebView(context).apply {
        // Must not be wrap_content: the WebView then sizes `vh` from its content (0 here).
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(background)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFocusable = false
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // With the injected viewport meta tag this makes 1 CSS px = 1 dp at scale 1.
            useWideViewPort = true
            loadWithOverviewMode = false
            textZoom = 100
            defaultTextEncodingName = "utf-8"
            mediaPlaybackRequiresUserGesture = true
        }
        addJavascriptInterface(Bridge(), "InkPalBridge")
        webViewClient = Client()
        webView = this
    }

    fun release() {
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("InkPalBridge")
            destroy()
        }
        webView = null
    }

    /** Opens the book at a saved position ("chapterIndex:fraction"). */
    fun start(position: String, initialCss: String) {
        css = initialCss
        val i = position.substringBefore(':').toIntOrNull()?.coerceIn(0, epub.spine.lastIndex) ?: 0
        val f = position.substringAfter(':', "0").toFloatOrNull() ?: 0f
        load(i, Target.Fraction(f))
    }

    fun next() {
        if (!ready) return
        when {
            page < pageCount - 1 -> moveTo(page + 1)
            index < epub.spine.lastIndex -> load(index + 1, Target.Fraction(0f))
            else -> onEnd()
        }
    }

    fun prev() {
        if (!ready) return
        when {
            page > 0 -> moveTo(page - 1)
            index > 0 -> load(index - 1, Target.Last)
        }
    }

    fun nextChapter() {
        if (index < epub.spine.lastIndex) load(index + 1, Target.Fraction(0f))
    }

    fun prevChapter() {
        if (page > 0) load(index, Target.Fraction(0f)) else if (index > 0) load(index - 1, Target.Fraction(0f))
    }

    fun jumpToProgress(progress: Float) {
        val (i, f) = epub.locate(progress)
        if (i == index && ready) {
            moveTo((f * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1))
        } else {
            load(i, Target.Fraction(f))
        }
    }

    fun jumpTo(entry: TocEntry) {
        load(entry.spineIndex, entry.anchor?.let { Target.Anchor(it) } ?: Target.Fraction(0f))
    }

    /** New text settings: restyle in place and keep the reader at the same spot. */
    fun setCss(newCss: String) {
        if (newCss == css) return
        css = newCss
        val view = webView ?: return
        if (ready) {
            pending = Target.Fraction(fraction)
            ready = false
            view.evaluateJavascript("InkPal.setStyle(${JSONObject.quote(newCss)})", null)
        }
    }

    private fun load(i: Int, target: Target) {
        index = i
        pending = target
        ready = false
        webView?.loadUrl(BASE + Uri.encode(epub.spine[i].path, "/"))
    }

    private fun moveTo(p: Int) {
        page = p
        pending = Target.Fraction(fraction)
        webView?.evaluateJavascript("InkPal.go($p)", null)
        onPosition(index, fraction)
    }

    /** JS reported the chapter's page count (after load, restyle or resize). */
    private fun onLayout(count: Int) {
        pageCount = count.coerceAtLeast(1)
        when (val t = pending) {
            is Target.Fraction -> place((t.value * (pageCount - 1)).roundToInt())
            Target.Last -> place(pageCount - 1)
            is Target.Anchor -> webView?.evaluateJavascript("InkPal.pageOfAnchor(${JSONObject.quote(t.id)})") { result ->
                place(result?.trim('"')?.toIntOrNull() ?: 0)
            }
        }
    }

    private fun place(p: Int) {
        page = p.coerceIn(0, pageCount - 1)
        pending = Target.Fraction(fraction)
        webView?.evaluateJavascript("InkPal.go($page)") { ready = true }
        onPosition(index, fraction)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onLayout(count: Int) {
            webView?.post { this@ReaderEngine.onLayout(count) }
        }
    }

    /** Serves the book's own files; everything else (the network) is refused. */
    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val url = request.url
            if (url.host != HOST) return notFound()
            val path = url.path.orEmpty().removePrefix("/")
            if (path.startsWith("$FONT_DIR/")) {
                val res = when (path.substringAfter('/')) {
                    "literata.ttf" -> R.font.literata
                    "literata_italic.ttf" -> R.font.literata_italic
                    else -> return notFound()
                }
                return WebResourceResponse("font/ttf", null, view.context.resources.openRawResource(res))
            }
            return try {
                val stream = epub.open(path) ?: return notFound()
                val type = epub.mediaType(path)
                if (epub.spineIndexOf(path) != null) {
                    val html = stream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val bytes = inject(html, css).toByteArray(Charsets.UTF_8)
                    WebResourceResponse(type, "utf-8", ByteArrayInputStream(bytes))
                } else {
                    WebResourceResponse(type, null, stream)
                }
            } catch (e: Exception) {
                notFound()
            }
        }

        private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    companion object {
        fun backgroundFor(theme: ReaderTheme): Int = Color.parseColor(ReaderStyle.pageColors(theme).first)

        /** Removes the book's own scripts and adds InkPal's style and page script to the head. */
        fun inject(html: String, css: String): String {
            val clean = html.replace(Regex("(?is)<script\\b.*?</script>"), "")
            val head = "<meta name=\"viewport\" content=\"width=device-width, height=device-height, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no\"/>" +
                "<style id=\"inkpal-style\">$css</style>" +
                "<script type=\"text/javascript\">//<![CDATA[\n$READER_JS\n//]]></script>"
            Regex("(?i)</head>").find(clean)?.let { return clean.replaceRange(it.range.first, it.range.first, head) }
            Regex("(?i)<body[^>]*>").find(clean)?.let { return clean.replaceRange(it.range.first, it.range.first, "<head>$head</head>") }
            return head + clean
        }
    }
}
