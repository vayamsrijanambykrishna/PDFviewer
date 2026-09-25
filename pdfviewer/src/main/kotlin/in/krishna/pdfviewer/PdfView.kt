package `in`.krishna.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.View

class PdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val document = PdfDocumentController(context)
    private val pageRenderer = PdfPageRenderer()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var bitmap: Bitmap? = null
    private var currentPageIndex = -1

    val pageCount: Int
        get() = document.pageCount

    val currentPage: Int
        get() = currentPageIndex

    init {
        setBackgroundColor(android.graphics.Color.WHITE)
    }

    fun setDocument(uri: Uri) {
        clearBitmap()
        document.open(uri)
        currentPageIndex = -1
        invalidate()
    }

    fun showPage(pageIndex: Int) {
        require(pageIndex in 0 until document.pageCount) {
            "Invalid page index: $pageIndex"
        }

        if (width <= 0 || height <= 0) return

        clearBitmap()

        val page = document.openPage(pageIndex)

        bitmap = try {
            val pageWidth = page.width
            val pageHeight = page.height
            val scale = width.toFloat() / pageWidth.toFloat()
            val targetWidth = width
            val targetHeight = (pageHeight * scale).toInt()

            pageRenderer.render(
                page = page,
                width = targetWidth,
                height = targetHeight
            )
        } finally {
            page.close()
        }

        currentPageIndex = pageIndex
        invalidate()
    }

    fun closeDocument() {
        clearBitmap()
        document.close()
        currentPageIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentBitmap = bitmap ?: return
        val left = (width - currentBitmap.width) / 2f

        canvas.drawBitmap(currentBitmap, left, 0f, paint)
    }

    override fun onDetachedFromWindow() {
        closeDocument()
        super.onDetachedFromWindow()
    }

    private fun clearBitmap() {
        bitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        bitmap = null
    }
}
