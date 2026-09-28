package `in`.krishna.pdfviewer.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class PdfEngine(private val context: Context) {

    // 2 ALAG-ALAG THREADS (VIP Priority + Background)
    private val priorityDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val backgroundDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var masterPfd: ParcelFileDescriptor? = null
    private var priorityRenderer: PdfRenderer? = null
    private var bgRenderer: PdfRenderer? = null

    var pageCount: Int = 0
        private set

    // Dimensions Cache taaki bar-bar native page open na karna pade
    private val dimensionsCache = ConcurrentHashMap<Int, Pair<Int, Int>>()

    // LRU Bitmap Cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    suspend fun open(uri: Uri): Result<Int> = withContext(priorityDispatcher) {
        runCatching {
            closeInternal()
            masterPfd = when (uri.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(uri, "r")
                "file" -> ParcelFileDescriptor.open(
                    File(uri.path ?: throw FileNotFoundException("Path not found")),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                else -> context.contentResolver.openFileDescriptor(uri, "r")
            } ?: throw IllegalStateException("PFD open nahi ho saka")

            // pfd.dup() se 2 independent thread-safe renderers
            val bgPfd = masterPfd!!.dup()

            priorityRenderer = PdfRenderer(masterPfd!!)
            bgRenderer = PdfRenderer(bgPfd)

            pageCount = priorityRenderer!!.pageCount
            pageCount
        }
    }

    suspend fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? = withContext(priorityDispatcher) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        dimensionsCache[pageIndex]?.let { return@withContext it }

        if (priorityRenderer == null) return@withContext null
        try {
            val page = priorityRenderer!!.openPage(pageIndex)
            val dims = Pair(page.width, page.height)
            page.close()
            dimensionsCache[pageIndex] = dims
            dims
        } catch (e: Exception) {
            null
        }
    }

    // VIP FAST LANE: Active Screen & Zoom (<50ms execution)
    suspend fun renderPageImmediate(pageIndex: Int, baseWidth: Int, scaleFactor: Float): Bitmap? =
        withContext(priorityDispatcher) {
            renderInternal(priorityRenderer, pageIndex, baseWidth, scaleFactor)
        }

    // SLOW BACKGROUND LANE: Neighbors & Carousel Thumbnails
    suspend fun renderPageBackground(pageIndex: Int, baseWidth: Int, scaleFactor: Float): Bitmap? =
        withContext(backgroundDispatcher) {
            renderInternal(bgRenderer, pageIndex, baseWidth, scaleFactor)
        }

    private fun renderInternal(
        renderer: PdfRenderer?,
        pageIndex: Int,
        baseWidth: Int,
        scaleFactor: Float
    ): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pageCount || renderer == null) return null
        if (scaleFactor <= 0.05f) return null

        val roundedScale = (Math.round(scaleFactor * 10f) / 10f).coerceIn(0.3f, 4.0f)
        val cacheKey = "${pageIndex}_$roundedScale"

        bitmapCache.get(cacheKey)?.let { return it }

        return try {
            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetWidth = (baseWidth * roundedScale).toInt()
            val targetHeight = (targetWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmapCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun closeInternal() {
        bitmapCache.evictAll()
        dimensionsCache.clear()
        try { priorityRenderer?.close() } catch (_: Exception) {}
        try { bgRenderer?.close() } catch (_: Exception) {}
        try { masterPfd?.close() } catch (_: Exception) {}
        priorityRenderer = null
        bgRenderer = null
        masterPfd = null
        pageCount = 0
    }

    fun close() {
        closeInternal()
        priorityDispatcher.close()
        backgroundDispatcher.close()
    }
}
