package com.inkpal.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Cover images, downscaled once at scan time and kept in `files/covers/<bookId>.jpg`. */
object Covers {
    private const val MAX_HEIGHT = 480

    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
    }

    fun file(context: Context, id: String): File =
        File(File(context.filesDir, "covers").apply { mkdirs() }, "$id.jpg")

    /** Returns true when a cover was written. */
    fun save(context: Context, id: String, bytes: ByteArray): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
            if (bitmap == null) {
                false
            } else {
                file(context, id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                cache.remove(id)
                true
            }
        }
    } catch (e: Exception) {
        false
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete()
        cache.remove(id)
    }

    suspend fun load(context: Context, id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, id)
            if (!f.exists()) return@withContext null
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(f.path, options)?.asImageBitmap()?.also { cache.put(id, it) }
        }
    }
}
