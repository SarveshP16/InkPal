package com.inkpal.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext

data class ScanState(
    val running: Boolean = false,
    /** New books added by the scan in progress. */
    val added: Int = 0,
)

/**
 * Keeps the library in step with the folders the user picked (Storage Access Framework
 * trees, so no broad storage permission is needed). Walks each folder, adds new .epub and
 * .m4b files with their metadata and cover, and drops books whose files are gone.
 */
class LibraryScanner(
    private val context: Context,
    private val store: InkStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var rescanRequested = false

    fun addFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not keep access to $uri", e)
        }
        val folder = LibraryFolder(uri.toString(), Format.folderName(uri), System.currentTimeMillis())
        store.update { d ->
            if (d.folders.any { it.uri == folder.uri }) d else d.copy(folders = d.folders + folder)
        }
        scan()
    }

    /** Stops using a folder. Its books leave the library; reading history is kept. */
    fun removeFolder(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        store.data.value.books.filter { it.folderUri == uri }.forEach { Covers.delete(context, it.id) }
        store.update { d ->
            d.copy(folders = d.folders.filterNot { it.uri == uri }, books = d.books.filterNot { it.folderUri == uri })
        }
    }

    /** Called when the app comes to the foreground; cheap enough to run often. */
    fun scanIfStale() {
        val d = store.data.value
        if (d.folders.isNotEmpty() && System.currentTimeMillis() - d.lastScanAt > 60_000) scan()
    }

    fun scan() {
        if (job?.isActive == true) {
            rescanRequested = true
            return
        }
        job = scope.launch(Dispatchers.IO) {
            do {
                rescanRequested = false
                runScan()
            } while (rescanRequested)
        }
    }

    private class Found(
        val uri: String,
        val folderUri: String,
        val name: String,
        val format: BookFormat,
        val size: Long,
        val modified: Long,
    )

    private suspend fun runScan() {
        _state.value = ScanState(running = true)
        try {
            val found = LinkedHashMap<String, Found>()
            val scannedFolders = mutableSetOf<String>()
            for (folder in store.data.value.folders) {
                currentCoroutineContext().ensureActive()
                try {
                    val tree = Uri.parse(folder.uri)
                    walk(tree, DocumentsContract.getTreeDocumentId(tree), folder.uri, found)
                    scannedFolders += folder.uri
                } catch (e: Exception) {
                    // Folder unreadable (SD card out, permission revoked): leave its books alone.
                    Log.w(TAG, "Scan failed for ${folder.uri}", e)
                }
            }

            // Drop books whose files disappeared from a folder we could read; refresh file details.
            val gone = store.data.value.books.filter { it.folderUri in scannedFolders && it.uri !in found }
            gone.forEach { Covers.delete(context, it.id) }
            val goneIds = gone.mapTo(HashSet()) { it.id }
            store.update { d ->
                d.copy(books = d.books.filter { it.id !in goneIds }.map { b ->
                    val f = found[b.uri]
                    if (f != null && (f.size != b.sizeBytes || f.modified != b.modifiedAt)) {
                        b.copy(sizeBytes = f.size, modifiedAt = f.modified, fileName = f.name)
                    } else b
                })
            }

            // Add new files one at a time so they appear while the scan continues.
            val known = store.data.value.books.mapTo(HashSet()) { it.uri }
            val hidden = store.data.value.hidden.toSet()
            for (item in found.values) {
                if (item.uri in known || item.uri in hidden) continue
                currentCoroutineContext().ensureActive()
                val book = readBook(item)
                store.update { d -> if (d.books.any { it.uri == book.uri }) d else d.copy(books = d.books + book) }
                _state.update { it.copy(added = it.added + 1) }
            }
            store.update { it.copy(lastScanAt = System.currentTimeMillis()) }
        } finally {
            _state.update { it.copy(running = false) }
        }
    }

    private fun walk(tree: Uri, rootId: String, folderUri: String, out: MutableMap<String, Found>) {
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.addLast(rootId to 0)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty()) {
            val (docId, depth) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".") && depth < MAX_DEPTH) pending.addLast(id to depth + 1)
                        continue
                    }
                    val format = BookFormat.fromFileName(name) ?: continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    out.putIfAbsent(
                        uri,
                        Found(
                            uri = uri,
                            folderUri = folderUri,
                            name = name,
                            format = format,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            modified = if (c.isNull(4)) 0 else c.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    private fun readBook(item: Found): Book {
        val id = Format.idFor(item.uri)
        val uri = Uri.parse(item.uri)
        val info = try {
            when (item.format) {
                BookFormat.EPUB -> EpubMeta.read(context, uri)
                BookFormat.M4B -> AudioMeta.read(context, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Metadata failed for ${item.name}", e)
            null
        }
        val hasCover = info?.cover?.let { Covers.save(context, id, it) } ?: false
        return Book(
            id = id,
            uri = item.uri,
            folderUri = item.folderUri,
            fileName = item.name,
            format = item.format,
            title = info?.title?.takeIf { it.isNotBlank() } ?: Format.titleFromFileName(item.name),
            author = info?.author.orEmpty(),
            sizeBytes = item.size,
            modifiedAt = item.modified,
            addedAt = System.currentTimeMillis(),
            hasCover = hasCover,
            durationMs = info?.durationMs ?: 0,
        )
    }

    companion object {
        private const val TAG = "LibraryScanner"
        private const val MAX_DEPTH = 12
    }
}
