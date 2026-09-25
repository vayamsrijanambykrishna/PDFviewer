package `in`.krishna.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import java.io.Closeable

internal class PdfDocumentController(
    private val context: Context
) : Closeable {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var cachedPageCount = 0

    val pageCount: Int
        @Synchronized get() = cachedPageCount

    val isOpen: Boolean
        @Synchronized get() = renderer != null

    @Synchronized
    fun open(
        uri: Uri,
        onError: ((Throwable) -> Unit)? = null
    ): Boolean {
        close()

        return try {
            val descriptor = context.contentResolver
                .openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Unable to open PDF: $uri")

            val pdfRenderer = PdfRenderer(descriptor)
            fileDescriptor = descriptor
            renderer = pdfRenderer
            cachedPageCount = pdfRenderer.pageCount
            true
        } catch (error: Throwable) {
            close()
            onError?.invoke(error)
            false
        }
    }

    @Synchronized
    fun open(
        descriptor: ParcelFileDescriptor,
        onError: ((Throwable) -> Unit)? = null
    ): Boolean {
        close()

        return try {
            val pdfRenderer = PdfRenderer(descriptor)
            fileDescriptor = descriptor
            renderer = pdfRenderer
            cachedPageCount = pdfRenderer.pageCount
            true
        } catch (error: Throwable) {
            close()
            onError?.invoke(error)
            false
        }
    }

    @Synchronized
    fun openPage(pageIndex: Int): PdfRenderer.Page {
        val pdfRenderer = renderer
            ?: throw IllegalStateException("PDF document is not open.")

        require(pageIndex in 0 until cachedPageCount) {
            "Invalid page index: $pageIndex"
        }

        return pdfRenderer.openPage(pageIndex)
    }

    @Synchronized
    fun pageSize(pageIndex: Int): Size {
        val page = openPage(pageIndex)
        return try {
            Size(page.width, page.height)
        } finally {
            page.close()
        }
    }

    @Synchronized
    fun renderPage(
        pageIndex: Int,
        targetWidth: Int,
        pageRenderer: PdfPageRenderer
    ): Bitmap {
        require(targetWidth > 0) { "Target width must be greater than zero." }

        val page = openPage(pageIndex)
        return try {
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

            pageRenderer.render(
                page = page,
                width = targetWidth,
                height = targetHeight
            )
        } finally {
            page.close()
        }
    }

    @Synchronized
    override fun close() {
        renderer?.close()
        renderer = null
        cachedPageCount = 0

        fileDescriptor?.close()
        fileDescriptor = null
    }
}
