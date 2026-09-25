package `in`.krishna.pdfviewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

internal class RenderScheduler(
    private val document: PdfDocumentController,
    private val pageRenderer: PdfPageRenderer,
    private val onRendered: (pageIndex: Int, bitmap: Bitmap, generation: Int) -> Unit,
    private val onLayoutReady: (sizes: List<Size>, generation: Int) -> Unit
) {

    private data class RenderRequest(
        val pageIndex: Int,
        val targetWidth: Int,
        val priority: Int,
        val generation: Int,
        val sequence: Long
    )

    private val lock = Any()
    private val queue = PriorityBlockingQueue(
        11,
        compareByDescending<RenderRequest> { it.priority }
            .thenBy { it.sequence }
    )
    private val queued = HashMap<Int, RenderRequest>()
    private val inFlight = HashSet<Int>()
    private var sequence = 0L

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PdfView-Renderer").apply {
                isDaemon = true
            }
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)

    init {
        executor.execute { drainQueue() }
    }

    fun loadLayout() {
        synchronized(lock) {
            val request = RenderRequest(
                pageIndex = -1,
                targetWidth = 0,
                priority = Int.MAX_VALUE,
                generation = generation.get(),
                sequence = sequence++
            )
            queued[-1] = request
            queue.offer(request)
        }
    }

    fun request(
        pageIndex: Int,
        targetWidth: Int,
        priority: Int = 0
    ) {
        if (pageIndex < 0 || targetWidth <= 0) return

        synchronized(lock) {
            if (inFlight.contains(pageIndex)) return

            val current = queued[pageIndex]
            if (current != null &&
                current.targetWidth >= targetWidth &&
                current.priority >= priority
            ) {
                return
            }

            current?.let(queue::remove)

            val request = RenderRequest(
                pageIndex = pageIndex,
                targetWidth = targetWidth,
                priority = priority,
                generation = generation.get(),
                sequence = sequence++
            )

            queued[pageIndex] = request
            queue.offer(request)
        }
    }

    private fun drainQueue() {
        while (!executor.isShutdown) {
            val request = try {
                queue.take()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }

            synchronized(lock) {
                if (queued[request.pageIndex] !== request) continue
                queued.remove(request.pageIndex)
                if (request.generation != generation.get()) continue
            }

            if (request.pageIndex == -1) {
                try {
                    val sizes = ArrayList<Size>(document.pageCount)
                    for (index in 0 until document.pageCount) {
                        sizes += document.pageSize(index)
                    }

                    mainHandler.post {
                        if (request.generation == generation.get()) {
                            onLayoutReady(sizes, request.generation)
                        }
                    }
                } catch (_: Throwable) {
                    // The document may have been closed while the request was running.
                }
                continue
            }

            synchronized(lock) {
                if (!inFlight.add(request.pageIndex)) continue
            }

            try {
                val bitmap = document.renderPage(
                    pageIndex = request.pageIndex,
                    targetWidth = request.targetWidth,
                    pageRenderer = pageRenderer
                )

                mainHandler.post {
                    synchronized(lock) {
                        inFlight.remove(request.pageIndex)
                    }

                    if (request.generation == generation.get()) {
                        onRendered(
                            request.pageIndex,
                            bitmap,
                            request.generation
                        )
                    } else if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    inFlight.remove(request.pageIndex)
                }
            }
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            generation.incrementAndGet()
            queue.clear()
            queued.clear()
        }
    }

    fun close() {
        cancelAll()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
