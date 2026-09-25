package `in`.krishna.pdfviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewerConfigTest {
    @Test
    fun defaults_are_stable() {
        val config = PdfViewerConfig()
        assertEquals(8f, config.pageSpacingDp, 0f)
        assertEquals(android.graphics.Color.WHITE, config.backgroundColor)
        assertEquals(1f, config.minZoom, 0f)
        assertEquals(3f, config.maxZoom, 0f)
    }
}
