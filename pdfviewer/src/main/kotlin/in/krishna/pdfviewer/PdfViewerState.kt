package `in`.krishna.pdfviewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class PdfViewerState {
    var currentPage: Int by mutableIntStateOf(-1)
    var pageCount: Int by mutableIntStateOf(0)
    var zoom: Float by mutableFloatStateOf(1f)
}
