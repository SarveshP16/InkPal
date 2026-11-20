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

/**
 * Runs inside each chapter: counts pages, scrolls to a page, finds anchors, search hits,
 * links under a tap and the first words of the current page.
 */
private const val READER_JS = """
(function(){
var W=function(){return window.innerWidth;};
var se=function(){return document.scrollingElement||document.documentElement;};
var pageOf=function(el){return Math.max(0,Math.floor((el.getBoundingClientRect().left+se().scrollLeft)/W()));};
var texts=function(){return document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);};
var skip=function(node){var p=node.parentNode?node.parentNode.nodeName.toLowerCase():'';return p==='script'||p==='style';};
window.InkPal={
 count:function(){return Math.max(1,Math.round(se().scrollWidth/W()));},
 go:function(p){se().scrollLeft=p*W();},
 setStyle:function(css){var s=document.getElementById('inkpal-style');if(s){s.textContent=css;}
   requestAnimationFrame(function(){requestAnimationFrame(InkPal.report);});},
 pageOfAnchor:function(id){var el=document.getElementById(id);
   if(!el){var n=document.getElementsByName(id);el=n.length?n[0]:null;}
   if(!el){return 0;}
   return pageOf(el);},
 clearHit:function(){var ms=document.querySelectorAll('mark.inkpal-hit');
   for(var i=0;i<ms.length;i++){var m=ms[i];var p=m.parentNode;p.replaceChild(document.createTextNode(m.textContent),m);p.normalize();}},
 findHit:function(q,n){InkPal.clearHit();q=q.toLowerCase();var w=texts();var c=0;var node;
   while((node=w.nextNode())){if(skip(node)){continue;}var s=node.nodeValue.toLowerCase();var from=0;
     while(true){var at=s.indexOf(q,from);if(at<0){break;}
       if(c===n){var r=document.createRange();r.setStart(node,at);r.setEnd(node,at+q.length);
         var m=document.createElementNS(document.documentElement.namespaceURI||'http://www.w3.org/1999/xhtml','mark');
         m.setAttribute('class','inkpal-hit');r.surroundContents(m);return pageOf(m);}
       c++;from=at+q.length;}}
   return -1;},
 snippet:function(){var w=texts();var node;var r=document.createRange();
   while((node=w.nextNode())){if(skip(node)){continue;}var v=node.nodeValue;if(!v||!v.trim()){continue;}
     r.selectNodeContents(node);var b=r.getBoundingClientRect();if(b.right<=0){continue;}if(b.left>=W()){break;}
     var lo=0,hi=v.length-1;
     while(lo<hi){var mid=(lo+hi)>>1;r.setStart(node,mid);r.setEnd(node,mid+1);if(r.getBoundingClientRect().left>=-1){hi=mid;}else{lo=mid+1;}}
     return v.substring(lo).replace(/\s+/g,' ').trim().substring(0,160);}
   return '';},
 linkAt:function(x,y){var el=document.elementFromPoint(x,y);if(!el||!el.closest){return null;}
   var a=el.closest('a[href]');if(!a){return null;}
   var href=a.getAttribute('href')||'';
   var kind=(a.getAttributeNS('http://www.idpf.org/2007/ops','type')||a.getAttribute('epub:type')||'')+' '+(a.getAttribute('role')||'');
   var label=(a.textContent||'').trim();
   var note=/noteref/.test(kind)||/^[\[(]?[0-9*†‡]{1,3}[\])]?$/.test(label)||(!!a.closest('sup')&&label.length<5);
   var text='';
   if(href.charAt(0)==='#'){var tg=document.getElementById(href.substring(1));
     if(tg){text=(tg.textContent||'').trim();if(text.length<3&&tg.parentElement){text=(tg.parentElement.textContent||'').trim();}}}
   return {href:href,note:note,text:text.replace(/\s+/g,' ').substring(0,1500)};},
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
        data class Hit(val query: String, val occurrence: Int) : Target
    }

    private var pending: Target = Target.Fraction(0f)
    @Volatile private var css: String = ""
    private var webView: WebView? = null

    val fraction: Float
        get() = if (pageCount <= 1) 0f else page.toFloat() / (pageCount - 1)

    val progress: Float get() = epub.progressOf(index, fraction)

    /** Saved-position form of where the reader is ("chapterIndex:fraction"). */
    val position: String get() = "$index:$fraction"

    /** Where to return after following a link, while the "Back" chip is showing. */
    var backPosition by mutableStateOf<String?>(null)
        private set

    /** True when [position] (a saved position) falls on the page being shown. */
    fun isOnPage(position: String): Boolean {
        val (i, f) = parse(position)
        return ready && i == index && (f * (pageCount - 1)).roundToInt() == page
    }

    fun jumpToPosition(position: String) {
        val (i, f) = parse(position)
        if (i == index && ready) moveTo((f * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1))
        else load(i, Target.Fraction(f))
    }

    /** Opens a search result and highlights it. */
    fun jumpToHit(spineIndex: Int, query: String, occurrence: Int) = load(spineIndex, Target.Hit(query, occurrence))

    /** The first words on the current page, for a bookmark. */
    fun snippet(onResult: (String) -> Unit) {
        val view = webView ?: return onResult("")
        view.evaluateJavascript("InkPal.snippet()") { onResult(jsString(it)) }
    }

    class Link(val href: String, val isNote: Boolean, val text: String)

    /** The link under a tap at ([x], [y]) in CSS px, or null. */
    fun linkAt(x: Float, y: Float, onResult: (Link?) -> Unit) {
        val view = webView
        if (view == null || !ready) return onResult(null)
        view.evaluateJavascript("InkPal.linkAt($x,$y)") { r ->
            val link = try {
                if (r == null || r == "null") null
                else JSONObject(r).let { Link(it.optString("href"), it.optBoolean("note"), it.optString("text")) }
            } catch (e: Exception) {
                null
            }
            onResult(link?.takeIf { it.href.isNotEmpty() })
        }
    }

    /** Book path and #id that [href] points to, relative to the current chapter. */
    fun resolveLink(href: String): Pair<String, String?> {
        val current = epub.spine[index].path
        if (href.startsWith("#")) return current to href.drop(1)
        return EpubBook.resolve(current.substringBeforeLast('/', ""), href)
    }

    /** Follows a link inside the book; returns false when it points outside the book. */
    fun followLink(href: String): Boolean {
        val (path, anchor) = resolveLink(href)
        val i = epub.spineIndexOf(path) ?: return false
        backPosition = position
        load(i, anchor?.let { Target.Anchor(it) } ?: Target.Fraction(0f))
        return true
    }

    fun goBack() {
        val back = backPosition ?: return
        backPosition = null
        jumpToPosition(back)
    }

    fun dismissBack() {
        backPosition = null
    }

    private fun parse(position: String): Pair<Int, Float> {
        val i = position.substringBefore(':').toIntOrNull()?.coerceIn(0, epub.spine.lastIndex) ?: 0
        val f = position.substringAfter(':', "0").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        return i to f
    }

    private fun jsString(raw: String?): String = try {
        org.json.JSONTokener(raw ?: "").nextValue() as? String ?: ""
    } catch (e: Exception) {
        ""
    }

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
            is Target.Hit -> webView?.evaluateJavascript(
                "InkPal.findHit(${JSONObject.quote(t.query)},${t.occurrence})",
            ) { result ->
                place(result?.trim('"')?.toIntOrNull()?.takeIf { it >= 0 } ?: 0)
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
