package `in`.krishna.pdfviewer

data class PdfViewerConfig(
    val pageSpacingDp: Float = 8f,
    val backgroundColor: Int = android.graphics.Color.WHITE,
    val minZoom: Float = 1f,
    val maxZoom: Float = 3f
)
