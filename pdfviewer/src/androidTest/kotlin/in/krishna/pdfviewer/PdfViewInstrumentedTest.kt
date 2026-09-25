package `in`.krishna.pdfviewer

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfViewInstrumentedTest {

    @Test
    fun pdfView_canOpenLocalPdf_andExposePageCount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "instrumented-test.pdf")
        createPdf(file)

        val view = PdfView(context)
        view.setDocument(Uri.fromFile(file))

        assertEquals(2, view.pageCount)
        assertEquals(0, view.currentPage)

        view.setDocument(Uri.fromFile(file))
        assertEquals(2, view.pageCount)
        assertEquals(0, view.currentPage)

        view.goToPage(1)
        assertEquals(1, view.currentPage)

        view.closeDocument()
        file.delete()
    }


    @Test
    fun pdfView_renders_whenDocumentIsOpenedBeforeFirstLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "render-after-layout-test.pdf")
        createPdf(file)

        val view = PdfView(context)
        val errors = java.util.concurrent.atomic.AtomicReference<Throwable?>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.setOnErrorListener { errors.set(it) }
            // Open before the first layout pass. This exercises the lifecycle
            // path where onSizeChanged() can cancel the initial layout request.
            view.setDocument(Uri.fromFile(file))
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    600,
                    android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    800,
                    android.view.View.MeasureSpec.EXACTLY
                )
            )
            view.layout(0, 0, 600, 800)
        }

        val deadline = System.currentTimeMillis() + 5_000L
        var rendered = false

        while (System.currentTimeMillis() < deadline && !rendered) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                600,
                800,
                android.graphics.Bitmap.Config.ARGB_8888
            )

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                view.draw(android.graphics.Canvas(bitmap))
            }

            for (y in 0 until bitmap.height step 8) {
                for (x in 0 until bitmap.width step 8) {
                    if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 245) {
                        rendered = true
                        break
                    }
                }
                if (rendered) break
            }

            bitmap.recycle()

            if (!rendered) {
                android.os.SystemClock.sleep(50L)
            }
        }

        assertEquals(null, errors.get())
        org.junit.Assert.assertTrue(
            "Expected the first PDF page to be rendered after the initial layout",
            rendered
        )

        view.closeDocument()
        file.delete()
    }

    @Test
    fun pdfView_rotation_and_navigation_areStateful() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "rotation-test.pdf")
        createPdf(file)

        val view = PdfView(context)
        view.setDocument(Uri.fromFile(file))

        view.rotate()
        assertEquals(90, view.rotation)

        view.rotate()
        assertEquals(180, view.rotation)

        view.resetRotation()
        assertEquals(0, view.rotation)

        view.nextPage()
        assertEquals(1, view.currentPage)

        view.previousPage()
        assertEquals(0, view.currentPage)

        view.closeDocument()
        file.delete()
    }

    private fun createPdf(file: File) {
        val document = PdfDocument()
        repeat(2) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(600, 800, it).create()
            )
            page.canvas.drawText("PDFviewer test page $it", 40f, 80f, android.graphics.Paint())
            document.finishPage(page)
        }

        file.outputStream().use { output ->
            document.writeTo(output)
        }
        document.close()
    }
}
