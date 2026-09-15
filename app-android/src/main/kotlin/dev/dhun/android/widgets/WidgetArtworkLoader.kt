package dev.dhun.android.widgets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Fetches, downscales and rounds track artwork for the home-screen widgets.
 *
 * The playback engine stores only an artwork **URI** in the Media3 metadata
 * ([dev.dhun.android.playback.AndroidDhunPlayer] sets `artworkUri` from the
 * track thumbnail) — nobody decodes it into `artworkData`. This loader does
 * that last hop on a daemon worker thread: HTTP GET → sampled decode →
 * center-crop square → rounded corners → [LruCache], with the result
 * delivered on the main thread for `RemoteViews.setImageViewBitmap`.
 *
 * Binder safety: bitmaps are capped at [MAX_BITMAP_PX] (256² ARGB ≈ 256 KB,
 * far under the ~1 MB RemoteViews transaction limit).
 *
 * Every entry point is failure-silent by design — artwork must never break
 * a widget push. Failures deliver `null` and the updater keeps the note
 * placeholder.
 */
object WidgetArtworkLoader {

    private const val TAG = "DHUN_WIDGET"

    /** Max bitmap edge in px (square). */
    const val MAX_BITMAP_PX = 256

    /** Corner radius as a fraction of the bitmap edge. */
    const val CORNER_RADIUS_FRACTION = 0.22f

    /** How long we wait for artwork bytes before giving up. */
    const val FETCH_TIMEOUT_MS = 8_000

    /** Refuse to buffer more than this many image bytes off the network. */
    const val MAX_FETCH_BYTES = 2 * 1024 * 1024

    /** 4 MB of decoded bitmaps ≈ 16 entries at 256². */
    const val CACHE_BYTES = 4 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "widget-artwork").apply { isDaemon = true }
    }

    /** Synchronous cache probe — lets the updater push instantly when warm. */
    fun cached(key: String?): Bitmap? {
        if (key.isNullOrBlank()) return null
        return runCatching { cache.get(key) }.getOrNull()
    }

    /**
     * Load decoded `artworkData` bytes (used when the session already embeds
     * artwork). No network involved.
     */
    fun loadBytes(bytes: ByteArray?, key: String, deliver: (Bitmap?) -> Unit) {
        cached(key)?.let { deliverOnMain(it, deliver); return }
        if (bytes == null || bytes.isEmpty()) {
            deliverOnMain(null, deliver)
            return
        }
        io.execute {
            val bitmap = runCatching {
                decodeSampled(bytes, MAX_BITMAP_PX)?.let { transform(it) }
            }.getOrNull()
            if (bitmap != null) runCatching { cache.put(key, bitmap) }
            deliverOnMain(bitmap, deliver)
        }
    }

    /**
     * Load artwork from a remote URI (the normal path — thumbnails).
     * [key] should be the URI string itself ([DhunWidgetState.artworkKey]).
     */
    fun loadUri(uri: String?, key: String, deliver: (Bitmap?) -> Unit) {
        cached(key)?.let { deliverOnMain(it, deliver); return }
        if (uri.isNullOrBlank()) {
            deliverOnMain(null, deliver)
            return
        }
        io.execute {
            val bytes = runCatching { fetchBytes(uri) }.getOrNull()
            val bitmap = bytes?.let {
                runCatching { decodeSampled(it, MAX_BITMAP_PX)?.let(::transform) }.getOrNull()
            }
            if (bitmap != null) runCatching { cache.put(key, bitmap) }
            deliverOnMain(bitmap, deliver)
        }
    }

    /** Test hook — clears the cache between cases. */
    internal fun evictAll() {
        runCatching { cache.evictAll() }
    }

    // ------------------------------------------------------------------ decode

    /**
     * Decode with an `inSampleSize` so the longest edge lands at/below
     * [maxEdgePx]. Returns null for undecodable input (never throws).
     */
    internal fun decodeSampled(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            var sample = 1
            while (longest / (sample * 2) >= maxEdgePx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }

    /**
     * Center-crop to a square, scale to [MAX_BITMAP_PX], round the corners.
     * RemoteViews ImageViews cannot clip, so transparency does the rounding.
     */
    internal fun transform(src: Bitmap): Bitmap {
        val edge = minOf(src.width, src.height).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(src, (src.width - edge) / 2, (src.height - edge) / 2, edge, edge)
        val scaled = if (edge != MAX_BITMAP_PX) {
            Bitmap.createScaledBitmap(cropped, MAX_BITMAP_PX, MAX_BITMAP_PX, true)
        } else {
            cropped
        }
        val out = Bitmap.createBitmap(MAX_BITMAP_PX, MAX_BITMAP_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, MAX_BITMAP_PX, MAX_BITMAP_PX)
        val rectF = RectF(rect)
        val radius = MAX_BITMAP_PX * CORNER_RADIUS_FRACTION
        canvas.drawRoundRect(rectF, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, rect, rect, paint)
        return out
    }

    // ------------------------------------------------------------------ fetch

    private fun fetchBytes(uri: String): ByteArray? {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = FETCH_TIMEOUT_MS
                readTimeout = FETCH_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) DHUN/1.0",
                )
                setRequestProperty("Accept", "image/*")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "artwork fetch: HTTP ${connection.responseCode} for $uri")
                return null
            }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FETCH_BYTES) {
                        Log.w(TAG, "artwork fetch: over size cap for $uri")
                        return null
                    }
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "artwork fetch failed for $uri", e)
            return null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun deliverOnMain(bitmap: Bitmap?, deliver: (Bitmap?) -> Unit) {
        val main = runCatching { Looper.getMainLooper() }.getOrNull()
        if (main == null || Looper.myLooper() == main) {
            // No main looper (plain-JVM tests) or already there — deliver inline.
            runCatching { deliver(bitmap) }
        } else {
            runCatching { Handler(main).post { runCatching { deliver(bitmap) } } }
        }
    }
}
