package com.inkpal.app.data.epub

import android.content.Context
import android.net.Uri
import com.inkpal.app.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

/** One reading-order document (usually a chapter). `weight` is its size, used to estimate overall progress. */
class SpineItem(val path: String, val mediaType: String, val weight: Long)

/** One line of the table of contents. */
class TocEntry(val title: String, val spineIndex: Int, val anchor: String?, val depth: Int)

/**
 * An opened EPUB: reading order, table of contents and access to the files inside.
 * Backed by a local copy of the book (see [EpubCache]) so files can be read in any order.
 */
class EpubBook private constructor(
    private val zip: ZipFile,
    private val entryNames: Map<String, String>,
    val spine: List<SpineItem>,
    val toc: List<TocEntry>,
    private val mediaTypes: Map<String, String>,
) : Closeable {

    private val cumulative = LongArray(spine.size)
    private val total: Long
    private val spineIndexByPath: Map<String, Int> = spine.withIndex().associate { (i, s) -> s.path.lowercase() to i }
    private val titles: List<String>

    init {
        var acc = 0L
        spine.forEachIndexed { i, s ->
            cumulative[i] = acc
            acc += s.weight.coerceAtLeast(1)
        }
        total = acc.coerceAtLeast(1)
        // Each spine item takes the title of the last (most specific) contents entry at or before it,
        // so chapter 5 inside "Part 1" reads "5: …", not the part name.
        titles = spine.indices.map { i ->
            toc.lastOrNull { it.spineIndex <= i }?.title ?: "Section ${i + 1}"
        }
    }

    fun chapterTitle(index: Int): String = titles.getOrElse(index) { "" }

    /** Overall progress 0..1 for a position inside spine item [index]. */
    fun progressOf(index: Int, fraction: Float): Float {
        if (spine.isEmpty()) return 0f
        val i = index.coerceIn(0, spine.lastIndex)
        val w = spine[i].weight.coerceAtLeast(1)
        return ((cumulative[i] + fraction.coerceIn(0f, 1f) * w) / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** The spine item and position inside it for overall progress [progress]. */
    fun locate(progress: Float): Pair<Int, Float> {
        if (spine.isEmpty()) return 0 to 0f
        val target = progress.coerceIn(0f, 1f) * total
        var i = spine.lastIndex
        while (i > 0 && cumulative[i] > target) i--
        val w = spine[i].weight.coerceAtLeast(1)
        return i to ((target - cumulative[i]) / w).toFloat().coerceIn(0f, 1f)
    }

    fun spineIndexOf(path: String): Int? = spineIndexByPath[path.lowercase()]

    fun open(path: String): InputStream? {
        val name = entryNames[path.lowercase()] ?: return null
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry)
    }

    fun mediaType(path: String): String = mediaTypes[path.lowercase()] ?: guessType(path)

    override fun close() = zip.close()

    companion object {
        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            try {
                return parse(zip)
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }

        private fun parse(zip: ZipFile): EpubBook {
            val names = HashMap<String, String>()
            zip.entries().asSequence().forEach { names[it.name.lowercase()] = it.name }
            fun read(path: String): String? = names[path.lowercase()]?.let { n ->
                zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }
            }

            val container = read("META-INF/container.xml")
            val opfPath = container
                ?.let { Jsoup.parse(it, "", Parser.xmlParser()).allElements.firstOrNull { e -> e.local() == "rootfile" }?.attr("full-path") }
                ?.takeIf { names.containsKey(it.lowercase()) }
                ?: names.values.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                ?: throw IllegalStateException("No package file in this EPUB")
            val opf = Jsoup.parse(read(opfPath).orEmpty(), "", Parser.xmlParser())
            val opfDir = opfPath.substringBeforeLast('/', "")

            class Item(val id: String, val path: String, val type: String, val properties: String)
            val manifest = opf.allElements.filter { it.local() == "item" }.map {
                Item(
                    id = it.attr("id"),
                    path = resolve(opfDir, it.attr("href")).first,
                    type = it.attr("media-type"),
                    properties = it.attr("properties"),
                )
            }
            val byId = manifest.associateBy { it.id }
            val mediaTypes = manifest.associate { it.path.lowercase() to it.type }

            val spineEl = opf.allElements.firstOrNull { it.local() == "spine" }
            val spine = spineEl?.allElements.orEmpty()
                .filter { it.local() == "itemref" }
                .mapNotNull { byId[it.attr("idref")] }
                .filter { "html" in it.type || it.path.endsWith("htm", true) || it.path.endsWith("html", true) }
                .map { item ->
                    val size = names[item.path.lowercase()]?.let { zip.getEntry(it)?.size } ?: 1L
                    SpineItem(item.path, item.type, size.coerceAtLeast(1))
                }
            if (spine.isEmpty()) throw IllegalStateException("This EPUB has no readable chapters")
            val spineIndex = spine.withIndex().associate { (i, s) -> s.path.lowercase() to i }

            // Contents: the EPUB 3 nav document, else the EPUB 2 NCX.
            val toc = mutableListOf<TocEntry>()
            fun addEntry(title: String, href: String, baseDir: String, depth: Int) {
                val (path, anchor) = resolve(baseDir, href)
                val idx = spineIndex[path.lowercase()] ?: return
                val clean = title.replace(Regex("\\s+"), " ").trim()
                if (clean.isNotEmpty()) toc += TocEntry(clean, idx, anchor, depth)
            }

            val nav = manifest.firstOrNull { "nav" in it.properties.split(' ') }
            val navHtml = nav?.let { read(it.path) }
            if (nav != null && navHtml != null) {
                val doc = Jsoup.parse(navHtml)
                val navEl = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" || it.attr("role") == "doc-toc" }
                    ?: doc.selectFirst("nav")
                val baseDir = nav.path.substringBeforeLast('/', "")
                fun walk(list: Element, depth: Int) {
                    list.children().filter { it.normalName() == "li" }.forEach { li ->
                        val a = li.children().firstOrNull { it.normalName() == "a" } ?: li.selectFirst("a")
                        if (a != null) addEntry(a.text(), a.attr("href"), baseDir, depth)
                        li.children().firstOrNull { it.normalName() == "ol" || it.normalName() == "ul" }?.let { walk(it, depth + 1) }
                    }
                }
                navEl?.children()?.firstOrNull { it.normalName() == "ol" || it.normalName() == "ul" }?.let { walk(it, 0) }
            }
            if (toc.isEmpty()) {
                val ncx = spineEl?.attr("toc")?.let { byId[it] }
                    ?: manifest.firstOrNull { it.type == "application/x-dtbncx+xml" }
                val ncxText = ncx?.let { read(it.path) }
                if (ncx != null && ncxText != null) {
                    val doc = Jsoup.parse(ncxText, "", Parser.xmlParser())
                    val baseDir = ncx.path.substringBeforeLast('/', "")
                    fun walk(parent: Element, depth: Int) {
                        parent.children().filter { it.local() == "navPoint".lowercase() }.forEach { p ->
                            val label = p.children().firstOrNull { it.local() == "navlabel" }?.text().orEmpty()
                            val src = p.children().firstOrNull { it.local() == "content" }?.attr("src").orEmpty()
                            if (src.isNotEmpty()) addEntry(label, src, baseDir, depth)
                            walk(p, depth + 1)
                        }
                    }
                    doc.allElements.firstOrNull { it.local() == "navmap" }?.let { walk(it, 0) }
                }
            }

            return EpubBook(zip, names, spine, toc, mediaTypes)
        }

        private fun Element.local(): String = tagName().substringAfter(':').lowercase()

        /** Resolves [href] against [baseDir] inside the zip; returns the path and the #anchor, if any. */
        fun resolve(baseDir: String, href: String): Pair<String, String?> {
            val anchor = href.substringAfter('#', "").ifEmpty { null }
            val raw = href.substringBefore('#')
            val decoded = try {
                URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                raw
            }
            val parts = ArrayDeque<String>()
            (if (baseDir.isEmpty() || decoded.startsWith("/")) decoded else "$baseDir/$decoded").split('/').forEach { seg ->
                when (seg) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(seg)
                }
            }
            return parts.joinToString("/") to anchor
        }

        private fun guessType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "js" -> "text/javascript"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Local copies of opened EPUBs in the cache folder, so the reader can jump around inside
 * the zip. A copy is reused while the file size matches; only the newest few are kept.
 */
object EpubCache {
    private const val KEEP = 6

    suspend fun fileFor(context: Context, book: Book): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val file = File(dir, "${book.id}.epub")
        val fresh = file.exists() && book.sizeBytes > 0 && file.length() == book.sizeBytes
        if (!fresh) {
            val tmp = File(dir, "${book.id}.tmp")
            val input = context.contentResolver.openInputStream(Uri.parse(book.uri))
                ?: throw IllegalStateException("The file can't be opened")
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            file.delete()
            if (!tmp.renameTo(file)) throw IllegalStateException("Couldn't prepare the book")
        }
        file.setLastModified(System.currentTimeMillis())
        dir.listFiles { f -> f.name.endsWith(".epub") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        file
    }
}
