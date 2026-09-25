package `in`.krishna.pdfviewer

import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

internal class RenderScheduler(
    private val document: PdfDocumentController,
    private val pageRenderer: PdfPageRenderer,
    private val onRendered: (pageIndex: Int, bitmap: Bitmap, generation: Int) -> Unit,
    private val onLayoutReady: (sizes: List<Size>, generation: Int) -> Unit,
    private val onError: (Throwable) -> Unit
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

    private val generation = AtomicInteger(0)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            drainQueue()
        }
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
        signal.trySend(Unit)
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

        signal.trySend(Unit)
    }

    private suspend fun drainQueue() {
        while (scope.isActive) {
            signal.receive()

            while (scope.isActive) {
                val request = synchronized(lock) {
                    val next = queue.poll() ?: return@synchronized null
                    if (queued[next.pageIndex] !== next) {
                        null
                    } else {
                        queued.remove(next.pageIndex)
                        next
                    }
                } ?: break

                if (request.generation != generation.get()) continue

                if (request.pageIndex == -1) {
                    try {
                        val sizes = ArrayList<Size>(document.pageCount)
                        for (index in 0 until document.pageCount) {
                            sizes += document.pageSize(index)
                        }

                        if (request.generation == generation.get()) {
                            withContext(Dispatchers.Main.immediate) {
                                if (request.generation == generation.get()) {
                                    onLayoutReady(sizes, request.generation)
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        withContext(Dispatchers.Main.immediate) {
                            if (request.generation == generation.get()) {
                                onError(error)
                            }
                        }
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

                    withContext(Dispatchers.Main.immediate) {
                        synchronized(lock) {
                            inFlight.remove(request.pageIndex)
                        }

                        if (request.generation == generation.get()) {
                            onRendered(
                                request.pageIndex,
                                bitmap,
                                request.generation
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    synchronized(lock) {
                        inFlight.remove(request.pageIndex)
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    synchronized(lock) {
                        inFlight.remove(request.pageIndex)
                    }

                    withContext(Dispatchers.Main.immediate) {
                        if (request.generation == generation.get()) {
                            onError(error)
                        }
                    }
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
        signal.trySend(Unit)
    }

    fun close() {
        synchronized(lock) {
            generation.incrementAndGet()
            queue.clear()
            queued.clear()
            inFlight.clear()
        }
        signal.close()
        scope.cancel()
    }
}
