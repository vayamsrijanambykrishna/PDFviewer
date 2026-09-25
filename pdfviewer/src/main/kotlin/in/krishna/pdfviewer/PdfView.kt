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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max

class PdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val document = PdfDocumentController(context)
    private val pageRenderer = PdfPageRenderer()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pageCache = PageCache(
        maxBytes = (
            context.resources.displayMetrics.widthPixels *
                context.resources.displayMetrics.heightPixels * 4L * 3L
            ).coerceIn(8L * 1024L * 1024L, 32L * 1024L * 1024L).toInt()
    )

    private var scheduler: RenderScheduler? = null
    private var pageSizes: List<Size> = emptyList()
    private var pageTops = IntArray(0)
    private var currentPageIndex = -1
    private var scrollOffset = 0f
    private var contentHeight = 0f
    private var documentGeneration = 0
    private var pendingPageIndex = -1

    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val pageSpacingPx =
        (8f * resources.displayMetrics.density).toInt()

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.abortAnimation()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val target = when {
                    scaleFactor < 1.5f -> 2f
                    scaleFactor < 2.5f -> 3f
                    else -> 1f
                }
                setScale(target, event.x, event.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleFactor > 1f) {
                    panX -= distanceX
                    panY -= distanceY
                    clampPan()
                    invalidate()
                } else {
                    scrollByInternal(distanceY)
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (scaleFactor > 1f) return false
                scroller.fling(
                    0,
                    scrollOffset.toInt(),
                    0,
                    (-velocityY).toInt(),
                    0,
                    0,
                    0,
                    maxScrollOffset().toInt()
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setScale(
                    scaleFactor * detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                return true
            }
        }
    )

    val pageCount: Int get() = document.pageCount
    val currentPage: Int get() = currentPageIndex
    val zoom: Float get() = scaleFactor

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
        scrollOffset = 0f
        contentHeight = 0f
        currentPageIndex = -1
        pendingPageIndex = -1
        scaleFactor = 1f
        panX = 0f
        panY = 0f

        document.open(uri)
        documentGeneration++
        scheduler = createScheduler()
        scheduler?.loadLayout()
        invalidate()
    }

    fun showPage(pageIndex: Int) {
        require(pageIndex in 0 until document.pageCount) { "Invalid page index: $pageIndex" }
        if (pageTops.isEmpty()) {
            pendingPageIndex = pageIndex
            return
        }

        if (scaleFactor != 1f) {
            scaleFactor = 1f
            panX = 0f
            panY = 0f
            pageCache.clear()
        }

        pendingPageIndex = -1
        scrollOffset = pageTops[pageIndex].toFloat().coerceIn(0f, maxScrollOffset())
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
        scrollOffset = 0f
        contentHeight = 0f
        currentPageIndex = -1
        pendingPageIndex = -1
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        documentGeneration++
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageSizes.isEmpty()) return

        requestVisiblePages()
        canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        canvas.translate(paddingLeft.toFloat() + panX, paddingTop.toFloat() - scrollOffset + panY)
        canvas.scale(scaleFactor, scaleFactor)

        val first = firstVisiblePage()
        val last = lastVisiblePage()
        for (index in first..last) {
            pageCache.get(index)?.let {
                canvas.drawBitmap(it, 0f, pageTops[index].toFloat(), paint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && scaleFactor > 1f) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        panX += dx
                        panY += dy
                        clampPan()
                        invalidate()
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return scaleHandled || gestureHandled || event.actionMasked != MotionEvent.ACTION_UP
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScrollOffset())
            updateCurrentPage()
            requestVisiblePages()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scheduler?.cancelAll()
        pageCache.clear()
        rebuildLayout()
        clampPan()
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
                    invalidate()
                } else if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            },
            onLayoutReady = { sizes, _ ->
                pageSizes = sizes
                rebuildLayout()
                if (pendingPageIndex in pageSizes.indices) {
                    scrollOffset = pageTops[pendingPageIndex].toFloat().coerceIn(0f, maxScrollOffset())
                    pendingPageIndex = -1
                }
                updateCurrentPage()
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
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        if (availableWidth <= 0 || pageSizes.isEmpty()) {
            pageTops = IntArray(0)
            contentHeight = 0f
            return
        }

        pageTops = IntArray(pageSizes.size)
        var top = 0L
        for (index in pageSizes.indices) {
            val size = pageSizes[index]
            val pageHeight = max(
                1,
                (availableWidth.toDouble() * size.height / size.width).toInt()
            )
            pageTops[index] = top.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            top += pageHeight
            if (index != pageSizes.lastIndex) top += pageSpacingPx
        }

        contentHeight = top.coerceAtMost(Int.MAX_VALUE.toLong()).toFloat()
        scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset())
        updateCurrentPage()
    }

    private fun requestVisiblePages() {
        val localScheduler = scheduler ?: return
        val availableWidth = ((width - paddingLeft - paddingRight) / scaleFactor)
            .toInt().coerceAtLeast(1)
        if (pageSizes.isEmpty()) return

        val first = (firstVisiblePage() - 2).coerceAtLeast(0)
        val last = (lastVisiblePage() + 2).coerceAtMost(pageSizes.lastIndex)
        for (index in first..last) {
            if (pageCache.get(index) == null) localScheduler.request(index, availableWidth)
        }
    }

    private fun firstVisiblePage(): Int {
        if (pageTops.isEmpty()) return 0
        val documentY = (scrollOffset - panY) / scaleFactor
        var low = 0
        var high = pageTops.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= documentY) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        return result.coerceIn(0, pageTops.lastIndex)
    }

    private fun lastVisiblePage(): Int {
        if (pageTops.isEmpty()) return 0
        val viewportBottom = (scrollOffset + height - paddingTop - paddingBottom - panY) / scaleFactor
        var low = firstVisiblePage()
        var high = pageTops.lastIndex
        var result = low
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] < viewportBottom) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        return result.coerceIn(0, pageTops.lastIndex)
    }

    private fun scrollByInternal(delta: Float) {
        val newOffset = (scrollOffset + delta).coerceIn(0f, maxScrollOffset())
        if (newOffset == scrollOffset) return
        scrollOffset = newOffset
        updateCurrentPage()
        requestVisiblePages()
        invalidate()
    }

    private fun maxScrollOffset(): Float {
        val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        return max(0f, contentHeight * scaleFactor - viewportHeight)
    }

    private fun updateCurrentPage() {
        if (pageTops.isEmpty()) {
            currentPageIndex = -1
            return
        }

        val viewportCenter =
            (scrollOffset + (height - paddingTop - paddingBottom) / 2f - panY) / scaleFactor

        var low = 0
        var high = pageTops.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= viewportCenter) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        currentPageIndex = result
    }

    private fun setScale(target: Float, focusX: Float, focusY: Float) {
        val newScale = target.coerceIn(1f, 3f)
        if (newScale == scaleFactor) return

        if (newScale == 1f) {
            scaleFactor = 1f
            panX = 0f
            panY = 0f
            scrollOffset = scrollOffset.coerceIn(0f, maxScrollOffset())
            pageCache.clear()
            requestVisiblePages()
            invalidate()
            return
        }

        val oldScale = scaleFactor
        val contentX = (focusX - paddingLeft - panX) / oldScale
        val contentY = (focusY - paddingTop + scrollOffset - panY) / oldScale

        scaleFactor = newScale
        panX = focusX - paddingLeft - contentX * newScale
        panY = focusY - paddingTop + contentY * newScale - scrollOffset

        clampPan()
        pageCache.clear()
        requestVisiblePages()
        invalidate()
    }

    private fun clampPan() {
        if (scaleFactor <= 1f) {
            panX = 0f
            panY = 0f
            return
        }

        val viewportWidth = (width - paddingLeft - paddingRight).toFloat()
        val viewportHeight = (height - paddingTop - paddingBottom).toFloat()
        val contentWidth = viewportWidth * scaleFactor
        val scaledHeight = contentHeight * scaleFactor

        val maxPanX = max(0f, (contentWidth - viewportWidth) / 2f)
        val maxPanY = max(0f, (scaledHeight - viewportHeight) / 2f)

        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }
}
