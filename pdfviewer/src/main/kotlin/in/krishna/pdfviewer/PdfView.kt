package `in`.krishna.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OverScroller
import android.view.View
import kotlin.math.max

class PdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val document = PdfDocumentController(context)
    private val pageRenderer = PdfPageRenderer()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scroller = OverScroller(context)

    private val pageCache = PageCache(
        maxBytes = (
            context.resources.displayMetrics.widthPixels *
                context.resources.displayMetrics.heightPixels * 4L * 3L
            ).coerceIn(
                8L * 1024L * 1024L,
                32L * 1024L * 1024L
            ).toInt()
    )

    private var scheduler: RenderScheduler? = null
    private var pageSizes: List<Size> = emptyList()
    private var pageTops = IntArray(0)
    private var currentPageIndex = -1
    private var scrollOffset = 0
    private var contentHeight = 0
    private var documentGeneration = 0
    private var pendingPageIndex = -1

    private val pageSpacingPx =
        (8f * resources.displayMetrics.density).toInt()

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.abortAnimation()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                scrollByInternal(distanceY.toInt())
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                scroller.fling(
                    0,
                    scrollOffset,
                    0,
                    (-velocityY).toInt(),
                    0,
                    0,
                    0,
                    maxScrollOffset()
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    val pageCount: Int
        get() = document.pageCount

    /** Zero-based index of the page nearest the viewport center. */
    val currentPage: Int
        get() = currentPageIndex

    init {
        setBackgroundColor(android.graphics.Color.WHITE)
        isFocusable = true
    }

    fun setDocument(uri: Uri) {
        closeScheduler()
        pageCache.clear()
        document.close()

        pageSizes = emptyList()
        pageTops = IntArray(0)
        scrollOffset = 0
        contentHeight = 0
        currentPageIndex = -1
        pendingPageIndex = -1

        document.open(uri)
        documentGeneration++

        scheduler = createScheduler()
        scheduler?.loadLayout()
        invalidate()
    }

    /**
     * Scrolls to a zero-based page index and starts rendering it plus nearby pages.
     */
    fun showPage(pageIndex: Int) {
        require(pageIndex in 0 until document.pageCount) {
            "Invalid page index: $pageIndex"
        }

        if (pageTops.isEmpty()) {
            pendingPageIndex = pageIndex
            return
        }

        pendingPageIndex = -1
        scrollOffset = pageTops[pageIndex].coerceIn(0, maxScrollOffset())
        updateCurrentPage()
        requestVisiblePages()
        invalidate()
    }

    fun closeDocument() {
        closeScheduler()
        document.close()
        pageCache.clear()

        pageSizes = emptyList()
        pageTops = IntArray(0)
        scrollOffset = 0
        contentHeight = 0
        currentPageIndex = -1
        pendingPageIndex = -1
        documentGeneration++

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pageSizes.isEmpty()) return

        requestVisiblePages()

        canvas.save()
        canvas.clipRect(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom
        )
        canvas.translate(
            paddingLeft.toFloat(),
            paddingTop.toFloat() - scrollOffset.toFloat()
        )

        val first = firstVisiblePage()
        val last = lastVisiblePage()

        for (index in first..last) {
            val bitmap = pageCache.get(index) ?: continue
            canvas.drawBitmap(
                bitmap,
                0f,
                pageTops[index].toFloat(),
                paint
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) ||
            super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.coerceIn(0, maxScrollOffset())
            updateCurrentPage()
            requestVisiblePages()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        scheduler?.cancelAll()
        pageCache.clear()
        rebuildLayout()
        requestVisiblePages()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            pageCache.trimForMemoryPressure()
            requestVisiblePages()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        closeDocument()
        super.onDetachedFromWindow()
    }

    private fun createScheduler(): RenderScheduler {
        return RenderScheduler(
            document = document,
            pageRenderer = pageRenderer,
            onRendered = { pageIndex, bitmap, _ ->
                if (pageIndex < pageCount) {
                    pageCache.put(pageIndex, bitmap)
                    updateCurrentPage()
                    invalidate()
                } else if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            },
            onLayoutReady = { sizes, _ ->
                pageSizes = sizes
                rebuildLayout()

                if (pendingPageIndex in pageSizes.indices) {
                    scrollOffset = pageTops[pendingPageIndex]
                        .coerceIn(0, maxScrollOffset())
                    pendingPageIndex = -1
                    updateCurrentPage()
                }

                requestVisiblePages()
                invalidate()
            }
        )
    }

    private fun closeScheduler() {
        scheduler?.close()
        scheduler = null
    }

    private fun rebuildLayout() {
        val availableWidth =
            (width - paddingLeft - paddingRight).coerceAtLeast(0)

        if (availableWidth <= 0 || pageSizes.isEmpty()) {
            pageTops = IntArray(0)
            contentHeight = 0
            return
        }

        pageTops = IntArray(pageSizes.size)

        var top = 0L

        for (index in pageSizes.indices) {
            val size = pageSizes[index]
            val pageHeight = max(
                1,
                (
                    availableWidth.toDouble() *
                        size.height.toDouble() /
                        size.width.toDouble()
                    ).toInt()
                )
            )

            pageTops[index] =
                top.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            top += pageHeight.toLong()

            if (index != pageSizes.lastIndex) {
                top += pageSpacingPx.toLong()
            }
        }

        contentHeight =
            top.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        scrollOffset =
            scrollOffset.coerceIn(0, maxScrollOffset())

        updateCurrentPage()
    }

    private fun requestVisiblePages() {
        val localScheduler = scheduler ?: return

        val availableWidth =
            (width - paddingLeft - paddingRight).coerceAtLeast(0)

        if (availableWidth <= 0 || pageSizes.isEmpty()) return

        val first = (firstVisiblePage() - 2).coerceAtLeast(0)
        val last = (lastVisiblePage() + 2)
            .coerceAtMost(pageSizes.lastIndex)

        for (index in first..last) {
            if (pageCache.get(index) == null) {
                localScheduler.request(index, availableWidth)
            }
        }
    }

    private fun firstVisiblePage(): Int {
        if (pageTops.isEmpty()) return 0

        var low = 0
        var high = pageTops.lastIndex
        var result = 0

        while (low <= high) {
            val mid = (low + high) ushr 1

            if (pageTops[mid] <= scrollOffset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result.coerceIn(0, pageTops.lastIndex)
    }

    private fun lastVisiblePage(): Int {
        if (pageTops.isEmpty()) return 0

        val viewportBottom =
            scrollOffset +
                (height - paddingTop - paddingBottom)
                    .coerceAtLeast(0)

        var low = firstVisiblePage()
        var high = pageTops.lastIndex
        var result = low

        while (low <= high) {
            val mid = (low + high) ushr 1

            if (pageTops[mid] < viewportBottom) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result.coerceIn(0, pageTops.lastIndex)
    }

    private fun scrollByInternal(delta: Int) {
        val newOffset =
            (scrollOffset + delta).coerceIn(0, maxScrollOffset())

        if (newOffset == scrollOffset) return

        scrollOffset = newOffset
        updateCurrentPage()
        requestVisiblePages()
        invalidate()
    }

    private fun maxScrollOffset(): Int {
        val viewportHeight =
            (height - paddingTop - paddingBottom).coerceAtLeast(0)

        return max(0, contentHeight - viewportHeight)
    }

    private fun updateCurrentPage() {
        if (pageTops.isEmpty()) {
            currentPageIndex = -1
            return
        }

        val viewportCenter =
            scrollOffset +
                (height - paddingTop - paddingBottom)
                    .coerceAtLeast(0) / 2

        var low = 0
        var high = pageTops.lastIndex
        var result = 0

        while (low <= high) {
            val mid = (low + high) ushr 1

            if (pageTops[mid] <= viewportCenter) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        currentPageIndex = result
    }
}
