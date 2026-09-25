package `in`.krishna.pdfviewer

import android.animation.ValueAnimator
import android.content.Context
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
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

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
    private var lastScrollDirection = 0f
    private var scaleAnimator: ValueAnimator? = null
    private var zoomFocusX = 0f
    private var zoomFocusY = 0f
    private var rotationDegrees = 0
    private var minZoom = 1f
    private var maxZoom = 3f
    private var pageChangeListener: ((Int) -> Unit)? = null

    private var pageSpacingPx =
        (8f * resources.displayMetrics.density).toInt()

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                cancelScaleAnimation()
                if (!scroller.isFinished) scroller.abortAnimation()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val target = when {
                    scaleFactor < 1.5f -> 2f.coerceAtMost(maxZoom)
                    scaleFactor < 2.5f -> maxZoom
                    else -> minZoom
                }
                animateScale(target, event.x, event.y)
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
                if (scaleFactor > 1f) {
                    val consumed = flingZoomed(
                        velocityX,
                        velocityY
                    )
                    return consumed
                }

                startVerticalFling(velocityY)
                return true
            }
        }
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelScaleAnimation()
                if (!scroller.isFinished) scroller.abortAnimation()
                zoomFocusX = detector.focusX
                zoomFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomFocusX = detector.focusX
                zoomFocusY = detector.focusY
                setScale(
                    scaleFactor * detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                clampPan()
                requestVisiblePages()
            }
        }
    )

    val pageCount: Int
        get() = document.pageCount

    val currentPage: Int
        get() = currentPageIndex

    val zoom: Float
        get() = scaleFactor

    val rotation: Int
        get() = rotationDegrees

    fun applyConfig(config: PdfViewerConfig) {
        pageSpacingPx = (config.pageSpacingDp * resources.displayMetrics.density)
            .coerceAtLeast(0f)
            .toInt()
        minZoom = config.minZoom.coerceIn(1f, 3f)
        maxZoom = config.maxZoom.coerceIn(minZoom, 3f)
        setBackgroundColor(config.backgroundColor)
        scaleFactor = scaleFactor.coerceIn(minZoom, maxZoom)
        pageCache.clear()
        rebuildLayout()
        requestVisiblePages()
        invalidate()
    }

    fun setOnPageChangedListener(listener: ((page: Int) -> Unit)?) {
        pageChangeListener = listener
    }

    fun nextPage() {
        if (currentPageIndex < pageCount - 1) showPage(currentPageIndex + 1)
    }

    fun previousPage() {
        if (currentPageIndex > 0) showPage(currentPageIndex - 1)
    }

    fun goToPage(pageIndex: Int) = showPage(pageIndex)

    fun rotate(clockwise: Boolean = true) {
        rotationDegrees = (rotationDegrees + if (clockwise) 90 else -90 + 360) % 360
        cancelScaleAnimation()
        scaleFactor = minZoom
        panX = 0f
        panY = 0f
        pageCache.clear()
        rebuildLayout()
        requestVisiblePages()
        invalidate()
    }

    fun resetRotation() {
        if (rotationDegrees == 0) return
        rotationDegrees = 0
        cancelScaleAnimation()
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        pageCache.clear()
        rebuildLayout()
        requestVisiblePages()
        invalidate()
    }

    init {
        setBackgroundColor(android.graphics.Color.WHITE)
        isFocusable = true
    }

    fun setDocument(uri: Uri) {
        cancelScaleAnimation()
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
        lastScrollDirection = 0f
        rotationDegrees = 0

        document.open(uri)
        documentGeneration++

        scheduler = createScheduler()
        scheduler?.loadLayout()
        invalidate()
    }

    fun showPage(pageIndex: Int) {
        require(pageIndex in 0 until document.pageCount) {
            "Invalid page index: $pageIndex"
        }

        if (pageTops.isEmpty()) {
            pendingPageIndex = pageIndex
            return
        }

        cancelScaleAnimation()
        if (scaleFactor != 1f) {
            scaleFactor = 1f
            panX = 0f
            panY = 0f
            pageCache.clear()
        }

        pendingPageIndex = -1
        scrollOffset = pageTops[pageIndex]
            .toFloat()
            .coerceIn(0f, maxScrollOffset())

        updateCurrentPage()
        requestVisiblePages()
        invalidate()
    }

    fun closeDocument() {
        cancelScaleAnimation()
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
        lastScrollDirection = 0f
        rotationDegrees = 0
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
            paddingLeft.toFloat() + panX,
            paddingTop.toFloat() - scrollOffset + panY
        )
        canvas.scale(scaleFactor, scaleFactor)

        val first = firstVisiblePage()
        val last = lastVisiblePage()

        if (first <= last) {
            for (index in first..last) {
                pageCache.get(index)?.let { bitmap ->
                    val viewportWidth =
                        (width - paddingLeft - paddingRight).toFloat()

                    if (rotationDegrees == 0) {
                        canvas.drawBitmap(
                            bitmap,
                            0f,
                            pageTops[index].toFloat(),
                            paint
                        )
                    } else {
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()
                        val centerX = viewportWidth / 2f
                        val centerY =
                            pageTops[index] +
                                rotatedPageSlotHeight(index, viewportWidth) / 2f

                        canvas.save()
                        canvas.rotate(rotationDegrees.toFloat(), centerX, centerY)
                        canvas.drawBitmap(
                            bitmap,
                            centerX - bitmapWidth / 2f,
                            centerY - bitmapHeight / 2f,
                            paint
                        )
                        canvas.restore()
                    }
                }
            }
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelScaleAnimation()
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

                        val beforeX = panX
                        val beforeY = panY
                        clampPan()

                        val consumedX = panX - (beforeX - dx)
                        val consumedY = panY - (beforeY - dy)

                        // When the zoomed content reaches a vertical edge,
                        // hand the remaining drag back to document scrolling.
                        if (abs(dy) > abs(dx) &&
                            abs(consumedY - dy) > 0.5f
                        ) {
                            scrollByInternal(-(dy - consumedY))
                        }

                        invalidate()
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                requestVisiblePages()
            }
        }

        return scaleHandled ||
            gestureHandled ||
            event.actionMasked != MotionEvent.ACTION_UP
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            if (scaleFactor <= minZoom) {
                scrollOffset = scroller.currY
                    .toFloat()
                    .coerceIn(0f, maxScrollOffset())
                updateCurrentPage()
            } else {
                panX = scroller.currX.toFloat()
                panY = scroller.currY.toFloat()
                clampPan()
            }

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
        clampPan()
        requestVisiblePages()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when {
            level >= TRIM_MEMORY_COMPLETE -> {
                pageCache.clear()
                scheduler?.cancelAll()
            }
            level >= TRIM_MEMORY_RUNNING_CRITICAL -> {
                pageCache.trimToBytes(4L * 1024L * 1024L)
                scheduler?.cancelAll()
            }
            level >= TRIM_MEMORY_RUNNING_LOW -> {
                pageCache.trimForMemoryPressure()
            }
        }

        requestVisiblePages()
        invalidate()
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
                    scrollOffset = pageTops[pendingPageIndex]
                        .toFloat()
                        .coerceIn(0f, maxScrollOffset())
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
        val availableWidth =
            (width - paddingLeft - paddingRight).coerceAtLeast(0)

        if (availableWidth <= 0 || pageSizes.isEmpty()) {
            pageTops = IntArray(0)
            contentHeight = 0f
            return
        }

        pageTops = IntArray(pageSizes.size)

        var top = 0L

        for (index in pageSizes.indices) {
            val pageHeight = rotatedPageSlotHeight(
                index,
                availableWidth.toFloat()
            ).toInt().coerceAtLeast(1)

            pageTops[index] =
                top.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            top += pageHeight.toLong()

            if (index != pageSizes.lastIndex) {
                top += pageSpacingPx.toLong()
            }
        }

        contentHeight =
            top.coerceAtMost(Int.MAX_VALUE.toLong()).toFloat()

        scrollOffset =
            scrollOffset.coerceIn(0f, maxScrollOffset())

        updateCurrentPage()
    }

    private fun rotatedPageSlotHeight(
        pageIndex: Int,
        availableWidth: Float
    ): Float {
        val size = pageSizes[pageIndex]
        val rotated = rotationDegrees == 90 || rotationDegrees == 270

        return if (!rotated) {
            availableWidth * size.height.toFloat() / size.width.toFloat()
        } else {
            availableWidth * size.width.toFloat() / size.height.toFloat()
        }
    }

    private fun requestVisiblePages() {
        val localScheduler = scheduler ?: return
        val baseWidth =
            (width - paddingLeft - paddingRight)
                .coerceAtLeast(1)

        // Render above 1x while zoomed, capped to keep bitmap memory bounded.
        val qualityScale = scaleFactor.coerceAtMost(2f)

        if (pageSizes.isEmpty()) return

        val firstVisible = firstVisiblePage()
        val lastVisible = lastVisiblePage()

        val direction = sign(lastScrollDirection).toInt()
        val before = if (direction < 0) 3 else 2
        val after = if (direction > 0) 3 else 2

        val first = (firstVisible - before).coerceAtLeast(0)
        val last = (lastVisible + after)
            .coerceAtMost(pageSizes.lastIndex)

        for (index in first..last) {
            if (pageCache.get(index) == null) {
                val size = pageSizes[index]
                val rotated = rotationDegrees == 90 || rotationDegrees == 270
                val fitWidth = if (rotated) {
                    baseWidth.toDouble() * size.width.toDouble() / size.height.toDouble()
                } else {
                    baseWidth.toDouble()
                }
                val targetWidth = (fitWidth * qualityScale)
                    .toLong()
                    .coerceAtMost(4096L)
                    .toInt()
                    .coerceAtLeast(1)

                val priority = when {
                    index in firstVisible..lastVisible -> 100
                    direction > 0 && index > lastVisible ->
                        80 - (index - lastVisible)
                    direction < 0 && index < firstVisible ->
                        80 - (firstVisible - index)
                    else -> 50
                }

                localScheduler.request(
                    pageIndex = index,
                    targetWidth = targetWidth,
                    priority = priority.coerceAtLeast(1)
                )
            }
        }
    }

    private fun firstVisiblePage(): Int {
        if (pageTops.isEmpty()) return 0

        val documentY =
            (scrollOffset - panY) / scaleFactor

        var low = 0
        var high = pageTops.lastIndex
        var result = 0

        while (low <= high) {
            val mid = (low + high) ushr 1

            if (pageTops[mid] <= documentY) {
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
            (
                scrollOffset +
                    height -
                    paddingTop -
                    paddingBottom -
                    panY
                ) / scaleFactor

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

    private fun scrollByInternal(delta: Float) {
        if (delta == 0f) return

        lastScrollDirection = delta

        val oldOffset = scrollOffset
        val newOffset =
            (scrollOffset + delta)
                .coerceIn(0f, maxScrollOffset())

        if (newOffset == oldOffset) return

        scrollOffset = newOffset
        updateCurrentPage()
        requestVisiblePages()
        invalidate()
    }

    private fun startVerticalFling(velocityY: Float) {
        lastScrollDirection = velocityY.sign
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
    }

    private fun flingZoomed(
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val minX = -maxPanX()
        val maxX = maxPanX()
        val minY = -maxPanY()
        val maxY = maxPanY()

        if (minX == maxX && minY == maxY) return false

        scroller.fling(
            panX.toInt(),
            panY.toInt(),
            velocityX.toInt(),
            velocityY.toInt(),
            minX.toInt(),
            maxX.toInt(),
            minY.toInt(),
            maxY.toInt()
        )

        postInvalidateOnAnimation()
        return true
    }

    private fun maxScrollOffset(): Float {
        val viewportHeight =
            (height - paddingTop - paddingBottom)
                .coerceAtLeast(0)

        return max(
            0f,
            contentHeight * scaleFactor - viewportHeight
        )
    }

    private fun updateCurrentPage() {
        if (pageTops.isEmpty()) {
            currentPageIndex = -1
            return
        }

        val viewportCenter =
            (
                scrollOffset +
                    (height - paddingTop - paddingBottom) / 2f -
                    panY
                ) / scaleFactor

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

        if (result != currentPageIndex) {
            currentPageIndex = result
            pageChangeListener?.invoke(result)
        } else {
            currentPageIndex = result
        }
    }

    private fun animateScale(
        target: Float,
        focusX: Float,
        focusY: Float
    ) {
        cancelScaleAnimation()

        val clampedTarget = target.coerceIn(minZoom, maxZoom)
        if (abs(clampedTarget - scaleFactor) < 0.001f) return

        zoomFocusX = focusX
        zoomFocusY = focusY

        val start = scaleFactor

        scaleAnimator = ValueAnimator.ofFloat(start, clampedTarget).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()

            addUpdateListener { animator ->
                setScale(
                    animator.animatedValue as Float,
                    zoomFocusX,
                    zoomFocusY
                )
            }

            start()
        }
    }

    private fun cancelScaleAnimation() {
        scaleAnimator?.cancel()
        scaleAnimator = null
    }

    private fun setScale(
        target: Float,
        focusX: Float,
        focusY: Float
    ) {
        val newScale = target.coerceIn(minZoom, maxZoom)

        if (abs(newScale - scaleFactor) < 0.0001f) return

        if (newScale == minZoom) {
            scaleFactor = minZoom
            panX = 0f
            panY = 0f
            scrollOffset =
                scrollOffset.coerceIn(0f, maxScrollOffset())
            pageCache.clear()
            requestVisiblePages()
            invalidate()
            return
        }

        val oldScale = scaleFactor

        val contentX =
            (focusX - paddingLeft - panX) / oldScale

        val contentY =
            (
                focusY -
                    paddingTop +
                    scrollOffset -
                    panY
                ) / oldScale

        scaleFactor = newScale

        panX =
            focusX -
                paddingLeft -
                contentX * newScale

        panY =
            focusY -
                paddingTop +
                contentY * newScale -
                scrollOffset

        clampPan()
        pageCache.clear()
        requestVisiblePages()
        invalidate()
    }

    private fun maxPanX(): Float {
        val viewportWidth =
            (width - paddingLeft - paddingRight)
                .toFloat()

        return max(
            0f,
            (viewportWidth * scaleFactor - viewportWidth) / 2f
        )
    }

    private fun maxPanY(): Float {
        val viewportHeight =
            (height - paddingTop - paddingBottom)
                .toFloat()

        val scaledHeight =
            contentHeight * scaleFactor

        return max(
            0f,
            (scaledHeight - viewportHeight) / 2f
        )
    }

    private fun clampPan() {
        if (scaleFactor <= minZoom) {
            panX = 0f
            panY = 0f
            return
        }

        panX = panX.coerceIn(-maxPanX(), maxPanX())
        panY = panY.coerceIn(-maxPanY(), maxPanY())
    }
}
