package `in`.krishna.pdfviewer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable

internal class PdfDocumentController(
    private val context: Context
) : Closeable {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    val isOpen: Boolean
        get() = renderer != null

    fun open(uri: Uri) {
        close()

        val descriptor = context.contentResolver
            .openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException(
                "Unable to open PDF: $uri"
            )

        open(descriptor)
    }

    fun open(descriptor: ParcelFileDescriptor) {
        close()
        fileDescriptor = descriptor
        renderer = PdfRenderer(descriptor)
    }

    fun openPage(pageIndex: Int): PdfRenderer.Page {
        val pdfRenderer = renderer
            ?: throw IllegalStateException("PDF document is not open.")

        require(pageIndex in 0 until pdfRenderer.pageCount) {
            "Invalid page index: $pageIndex"
        }

        return pdfRenderer.openPage(pageIndex)
    }

    override fun close() {
        renderer?.close()
        renderer = null
        fileDescriptor?.close()
        fileDescriptor = null
    }
}
