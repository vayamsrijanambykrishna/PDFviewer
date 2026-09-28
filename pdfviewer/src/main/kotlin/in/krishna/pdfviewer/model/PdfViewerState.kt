package `in`.krishna.pdfviewer.model

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class PdfViewerState(
    initialMode: ViewerLayoutMode = ViewerLayoutMode.CONTINUOUS_SCROLL,
    private val scope: CoroutineScope
) {
    var currentPage by mutableIntStateOf(0)
        internal set

    var pageCount by mutableIntStateOf(0)
        internal set

    // Backing field: Isse JVM level par automatic setter nahi banega (No Signature Clash)
    private var _layoutMode by mutableStateOf(initialMode)
    val layoutMode: ViewerLayoutMode
        get() = _layoutMode

    internal var verticalListState: LazyListState? = null
    internal var mainPagerState: PagerState? = null
    internal var deckPagerState: PagerState? = null

    fun setLayoutMode(mode: ViewerLayoutMode) {
        val targetPage = currentPage
        _layoutMode = mode
        scope.launch {
            if (mode == ViewerLayoutMode.CONTINUOUS_SCROLL) {
                verticalListState?.scrollToItem(targetPage)
            } else {
                mainPagerState?.scrollToPage(targetPage)
                deckPagerState?.scrollToPage(targetPage)
            }
        }
    }

    fun scrollToPage(page: Int) {
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (layoutMode == ViewerLayoutMode.CONTINUOUS_SCROLL) {
                verticalListState?.scrollToItem(target)
            } else {
                mainPagerState?.animateScrollToPage(target)
                deckPagerState?.animateScrollToPage(target)
            }
        }
    }
}

@Composable
fun rememberPdfViewerState(
    initialMode: ViewerLayoutMode = ViewerLayoutMode.CONTINUOUS_SCROLL,
    scope: CoroutineScope = rememberCoroutineScope()
): PdfViewerState = remember {
    PdfViewerState(initialMode = initialMode, scope = scope)
}
