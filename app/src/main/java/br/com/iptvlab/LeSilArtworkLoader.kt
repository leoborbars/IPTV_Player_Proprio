package br.com.iptvlab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Loads only provider artwork and keeps a small in-memory cache for TV catalog rows. */
object LeSilArtworkLoader {
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private val queue = LinkedBlockingDeque<Runnable>(256)
    private val executor = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, queue).apply {
        prestartAllCoreThreads()
    }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun into(url: String?, target: ImageView, maxDimension: Int = 900, priority: Boolean = false) {
        val key = url?.let { "$it#$maxDimension" }
        if (target.tag == key && target.drawable != null) return
        target.tag = key
        target.setImageDrawable(null)
        if (url.isNullOrBlank()) return

        cache.get(key)?.let {
            target.setImageBitmap(it)
            return
        }
        val targetReference = WeakReference(target)
        val task = Runnable {
            val currentTarget = targetReference.get() ?: return@Runnable
            if (currentTarget.tag != key) return@Runnable
            val bitmap = cache.get(key) ?: decode(url, maxDimension)?.also { cache.put(key, it) }
                ?: return@Runnable
            currentTarget.post {
                if (currentTarget.tag == key) currentTarget.setImageBitmap(bitmap)
            }
        }
        // Keep normal cards in row order; the first poster of each row goes to
        // the front so it cannot starve behind later columns while scrolling.
        val offered = if (priority) queue.offerFirst(task) else queue.offerLast(task)
        if (!offered) {
            queue.pollLast()
            if (priority) queue.offerFirst(task) else queue.offerLast(task)
        }
    }

    private fun decode(url: String, maxDimension: Int): Bitmap? {
        val connection = runCatching { URL(url).openConnection() as? HttpURLConnection }.getOrNull() ?: return null
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "image/*")
        return try {
            if (connection.responseCode !in 200..299) return null
            val advertisedLength = connection.contentLengthLong
            if (advertisedLength > MAX_DOWNLOAD_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_DOWNLOAD_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
