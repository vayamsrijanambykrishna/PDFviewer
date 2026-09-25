package `in`.krishna.pdfviewer.compose

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import `in`.krishna.pdfviewer.PdfView
import `in`.krishna.pdfviewer.PdfViewerState

@Composable
fun PdfViewer(
    uri: Uri,
    modifier: Modifier = Modifier,
    state: PdfViewerState = remember { PdfViewerState() }
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PdfView(context).also { view ->
                view.setDocument(uri)
                view.setOnPageChangedListener { page ->
                    state.currentPage = page
                    state.pageCount = view.pageCount
                    state.zoom = view.zoom
                }
            }
        },
        update = { view ->
            state.currentPage = view.currentPage
            state.pageCount = view.pageCount
            state.zoom = view.zoom
        }
    )

    DisposableEffect(uri) {
        onDispose { }
    }
}
