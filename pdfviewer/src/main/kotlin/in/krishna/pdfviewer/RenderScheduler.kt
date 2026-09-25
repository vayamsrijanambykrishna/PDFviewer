package `in`.krishna.pdfviewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import java.util.PriorityQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val queue = PriorityQueue<RenderRequest>(
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
                // The document may have been closed while the request was running.
            }
        }
    }

    fun request(
        pageIndex: Int,
        targetWidth: Int,
        priority: Int = 0
    ) {
        if (targetWidth <= 0) return

        synchronized(lock) {
            if (inFlight.contains(pageIndex)) return

            val current = queued[pageIndex]
            if (current != null &&
                current.targetWidth >= targetWidth &&
                current.priority >= priority
            ) {
                return
            }

            current?.let {
                queue.remove(it)
            }

            val request = RenderRequest(
                pageIndex = pageIndex,
                targetWidth = targetWidth,
                priority = priority,
                generation = generation.get(),
                sequence = sequence++
            )

            queued[pageIndex] = request
            queue.offer(request)
            lock.notifyAll()
        }
    }

    private fun drainQueue() {
        while (!executor.isShutdown) {
            val request = synchronized(lock) {
                while (queue.isEmpty() && !executor.isShutdown) {
                    try {
                        lock.wait()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }

                if (executor.isShutdown) return

                val next = queue.poll()
                if (next != null) {
                    queued.remove(next.pageIndex)
                }
                next
            } ?: continue

            if (request.generation != generation.get()) continue

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

                    signalQueue()
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    inFlight.remove(request.pageIndex)
                }
                signalQueue()
            }
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            generation.incrementAndGet()
            queue.clear()
            queued.clear()
            lock.notifyAll()
        }
    }

    fun close() {
        cancelAll()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun signalQueue() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }
}
