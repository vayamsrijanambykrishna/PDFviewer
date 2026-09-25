package `in`.krishna.pdfviewer.compose

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import `in`.krishna.pdfviewer.PdfView
import `in`.krishna.pdfviewer.PdfViewerState

@Composable
fun PdfViewer(
    uri: Uri,
    modifier: Modifier = Modifier,
    state: PdfViewerState = remember { PdfViewerState() }
) {
    val context = LocalContext.current
    val pdfView = remember { PdfView(context) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { pdfView },
        update = { view ->
            state.currentPage = view.currentPage
            state.pageCount = view.pageCount
            state.zoom = view.zoom
        }
    )

    DisposableEffect(pdfView) {
        pdfView.setOnPageChangedListener { page ->
            state.currentPage = page
            state.pageCount = pdfView.pageCount
            state.zoom = pdfView.zoom
        }
        onDispose {
            pdfView.setOnPageChangedListener(null)
            pdfView.closeDocument()
        }
    }

    DisposableEffect(uri, pdfView) {
        pdfView.setDocument(uri)
        onDispose { }
    }
}
