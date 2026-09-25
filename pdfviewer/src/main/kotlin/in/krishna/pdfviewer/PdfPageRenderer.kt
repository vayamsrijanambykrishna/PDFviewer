package `in`.krishna.pdfviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer

internal class PdfPageRenderer {

    fun render(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        require(width > 0) { "Width must be greater than zero." }
        require(height > 0) { "Height must be greater than zero." }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.eraseColor(Color.WHITE)

        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        return bitmap
    }
}
