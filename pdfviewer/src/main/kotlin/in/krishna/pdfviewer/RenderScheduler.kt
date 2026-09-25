package `in`.krishna.pdfviewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class RenderScheduler(
    private val document: PdfDocumentController,
    private val pageRenderer: PdfPageRenderer,
    private val onRendered: (pageIndex: Int, bitmap: Bitmap, generation: Int) -> Unit,
    private val onLayoutReady: (sizes: List<Size>, generation: Int) -> Unit
) {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PdfView-Renderer").apply {
                isDaemon = true
            }
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()

    fun loadLayout() {
        val requestGeneration = generation.get()

        executor.execute {
            try {
                val sizes = ArrayList<Size>(document.pageCount)

                for (index in 0 until document.pageCount) {
                    sizes += document.pageSize(index)
                }

                mainHandler.post {
                    if (requestGeneration == generation.get()) {
                        onLayoutReady(sizes, requestGeneration)
                    }
                }
            } catch (_: Throwable) {
                // The document may have been closed while this request was running.
            }
        }
    }

    fun request(pageIndex: Int, targetWidth: Int) {
        if (targetWidth <= 0 || !inFlight.add(pageIndex)) return

        val requestGeneration = generation.get()

        executor.execute {
            try {
                val bitmap = document.renderPage(
                    pageIndex = pageIndex,
                    targetWidth = targetWidth,
                    pageRenderer = pageRenderer
                )

                mainHandler.post {
                    inFlight.remove(pageIndex)

                    if (requestGeneration == generation.get()) {
                        onRendered(
                            pageIndex,
                            bitmap,
                            requestGeneration
                        )
                    } else if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } catch (_: Throwable) {
                mainHandler.post {
                    inFlight.remove(pageIndex)
                }
            }
        }
    }

    fun cancelAll() {
        generation.incrementAndGet()
        inFlight.clear()
    }

    fun close() {
        cancelAll()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
