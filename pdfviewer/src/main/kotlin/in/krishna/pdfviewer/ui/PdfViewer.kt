package `in`.krishna.pdfviewer.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.krishna.pdfviewer.core.PdfEngine
import `in`.krishna.pdfviewer.model.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PdfViewer(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit = {},
    state: PdfViewerState = rememberPdfViewerState(),
    colors: PdfViewerColors = PdfViewerDefaults.colors(),
    config: PdfViewerConfig = PdfViewerConfig(),
    carouselConfig: CarouselConfig = CarouselConfig(),
    topBar: (@Composable (state: PdfViewerState, title: String, onBack: () -> Unit) -> Unit)? = { s, docTitle, onBack ->
        DefaultPdfTopBar(state = s, title = docTitle, onBackClick = onBack, colors = colors)
    },
    customCarousel: (@Composable (state: PdfViewerState, pageCount: Int) -> Unit)? = null,
    loadingContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primaryAccentColor)
        }
    },
    errorContent: @Composable (String) -> Unit = { message ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
) {
    val context = LocalContext.current
    val engine = remember { PdfEngine(context) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val resolvedTitle = remember(uri, title) {
        title ?: extractFileName(context, uri)
    }

    val coroutineScope = rememberCoroutineScope()
    val verticalListState = rememberLazyListState()
    val mainPagerState = rememberPagerState(pageCount = { state.pageCount })
    val deckPagerState = rememberPagerState(pageCount = { state.pageCount })

    var activeScrollZoom by remember { mutableFloatStateOf(1f) }
    var activeSwipeZoom by remember { mutableFloatStateOf(1f) }
    var isFastScrolling by remember { mutableStateOf(false) }

    val currentZoom = if (state.layoutMode == ViewerLayoutMode.CONTINUOUS_SCROLL) activeScrollZoom else activeSwipeZoom
    val isTopBarVisible = remember(currentZoom, isFastScrolling, config.autoHideTopBarOnZoom) {
        if (!config.autoHideTopBarOnZoom) true
        else (currentZoom <= 1.05f) || isFastScrolling
    }

    LaunchedEffect(verticalListState, mainPagerState, deckPagerState) {
        state.verticalListState = verticalListState
        state.mainPagerState = mainPagerState
        state.deckPagerState = deckPagerState
    }

    LaunchedEffect(mainPagerState.currentPage) {
        if (!deckPagerState.isScrollInProgress && deckPagerState.currentPage != mainPagerState.currentPage) {
            deckPagerState.animateScrollToPage(mainPagerState.currentPage)
        }
        if (state.layoutMode == ViewerLayoutMode.HORIZONTAL_SWIPE) {
            state.currentPage = mainPagerState.currentPage
        }
    }
    LaunchedEffect(deckPagerState.currentPage) {
        if (!mainPagerState.isScrollInProgress && mainPagerState.currentPage != deckPagerState.currentPage) {
            mainPagerState.animateScrollToPage(deckPagerState.currentPage)
        }
    }

    DisposableEffect(uri) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        scope.launch {
            isLoading = true
            val result = engine.open(uri)
            if (result.isSuccess) {
                state.pageCount = result.getOrNull() ?: 0
                isLoading = false
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Error opening PDF"
                isLoading = false
            }
        }
        onDispose { engine.close() }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize().background(colors.backgroundColor)) { loadingContent() }
        return
    }
    if (errorMessage != null) {
        Box(modifier = modifier.fillMaxSize().background(colors.backgroundColor)) { errorContent(errorMessage ?: "Error") }
        return
    }

    val focusedPageIndex by remember {
        derivedStateOf {
            val layoutInfo = verticalListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf verticalListState.firstVisibleItemIndex
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            visibleItems.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                abs(itemCenter - viewportCenter)
            }?.index ?: verticalListState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(focusedPageIndex) {
        if (state.layoutMode == ViewerLayoutMode.CONTINUOUS_SCROLL) {
            state.currentPage = focusedPageIndex
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundColor)) {
        when (state.layoutMode) {
            // MODE 1: CONTINUOUS SCROLL
            ViewerLayoutMode.CONTINUOUS_SCROLL -> {
                SmoothZoomPanBox(
                    modifier = Modifier.fillMaxSize(),
                    resetKey = state.layoutMode,
                    allowVerticalPan = false,
                    canScrollBackward = { verticalListState.canScrollBackward },
                    canScrollForward = { verticalListState.canScrollForward },
                    maxScale = config.maxZoomScale,
                    onVerticalScroll = { delta ->
                        verticalListState.dispatchRawDelta(-delta)
                    },
                    onScaleChanged = { scale ->
                        activeScrollZoom = scale
                    }
                ) {
                    LazyColumn(
                        state = verticalListState,
                        userScrollEnabled = (activeScrollZoom <= 1.02f),
                        verticalArrangement = Arrangement.spacedBy(config.pageSpacing),
                        // 100% STATIC PADDING (Zoom par kabhi jump nahi karega)
                        contentPadding = config.contentPadding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.pageCount) { index ->
                            val distance = abs(index - focusedPageIndex)
                            val targetScale = when (distance) {
                                0 -> (2.0f * activeScrollZoom).coerceIn(2.0f, 4.0f)
                                1 -> 1.0f
                                2 -> 0.75f
                                3 -> 0.5f
                                else -> 0.0f
                            }
                            val isPriority = (distance == 0)

                            CrispPdfPage(
                                pageIndex = index,
                                pageCount = state.pageCount,
                                engine = engine,
                                colors = colors,
                                targetScale = targetScale,
                                isPriority = isPriority
                            )
                        }
                    }
                }

                // FAST SCROLLBAR (Top Header ke neeche se start)
                if (config.enableFastScrollbar) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 58.dp, bottom = 16.dp)
                    ) {
                        val trackHeightPx = constraints.maxHeight.toFloat()
                        val thumbHeightDp = 48.dp
                        val thumbHeightPx = with(LocalDensity.current) { thumbHeightDp.toPx() }
                        val maxScrollDistance = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

                        val currentFraction = if (state.pageCount > 1) {
                            (verticalListState.firstVisibleItemIndex.toFloat() / (state.pageCount - 1)).coerceIn(0f, 1f)
                        } else 0f

                        val yOffsetPx = (currentFraction * maxScrollDistance).roundToInt()
                        var dragAccumulator by remember { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(0, yOffsetPx) }
                                .pointerInput(trackHeightPx, state.pageCount) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isFastScrolling = true
                                            dragAccumulator = currentFraction * maxScrollDistance
                                        },
                                        onDragEnd = { isFastScrolling = false },
                                        onDragCancel = { isFastScrolling = false }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragAccumulator = (dragAccumulator + dragAmount).coerceIn(0f, maxScrollDistance)
                                        val targetFraction = (dragAccumulator / maxScrollDistance).coerceIn(0f, 1f)
                                        val targetPage = (targetFraction * (state.pageCount - 1)).roundToInt()
                                        coroutineScope.launch {
                                            verticalListState.scrollToItem(targetPage)
                                        }
                                    }
                                }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(thumbHeightDp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isFastScrolling) colors.scrollbarPillColor else colors.scrollbarPillColor.copy(alpha = 0.75f),
                                    contentColor = colors.scrollbarPillTextColor,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .wrapContentWidth()
                                ) {
                                    Text(
                                        text = "${verticalListState.firstVisibleItemIndex + 1} /${state.pageCount}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(36.dp)
                                        .background(
                                            color = if (isFastScrolling) colors.scrollbarHandleColor else colors.scrollbarHandleColor.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // MODE 2: HORIZONTAL SWIPE
            ViewerLayoutMode.HORIZONTAL_SWIPE -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = mainPagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        userScrollEnabled = (activeSwipeZoom <= 1.05f)
                    ) { pageIndex ->
                        val isCurrent = (pageIndex == mainPagerState.currentPage)

                        SmoothZoomPanBox(
                            modifier = Modifier.fillMaxSize(),
                            resetKey = pageIndex,
                            allowVerticalPan = true,
                            maxScale = config.maxZoomScale,
                            onScaleChanged = { scale ->
                                if (isCurrent) {
                                    activeSwipeZoom = scale
                                }
                            }
                        ) {
                            val targetScale = if (isCurrent) {
                                (2.0f * activeSwipeZoom).coerceIn(2.0f, 4.0f)
                            } else {
                                1.0f
                            }

                            CrispPdfPage(
                                pageIndex = pageIndex,
                                pageCount = state.pageCount,
                                engine = engine,
                                colors = colors,
                                targetScale = targetScale,
                                isPriority = isCurrent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Floating 3D Carousel
                    if (carouselConfig.enabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            customCarousel?.invoke(state, state.pageCount) ?: run {
                                ThumbnailCarousel3D(
                                    deckPagerState = deckPagerState,
                                    pageCount = state.pageCount,
                                    engine = engine,
                                    colors = colors,
                                    config = carouselConfig,
                                    onThumbnailClick = { targetPage ->
                                        coroutineScope.launch {
                                            mainPagerState.animateScrollToPage(targetPage)
                                            deckPagerState.animateScrollToPage(targetPage)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // SMART AUTO-HIDE TOPBAR
        AnimatedVisibility(
            visible = isTopBarVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            topBar?.invoke(state, resolvedTitle, onBackClick)
        }
    }
}

@Composable
fun DefaultPdfTopBar(
    state: PdfViewerState,
    title: String,
    onBackClick: () -> Unit,
    colors: PdfViewerColors,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colors.topBarBackgroundColor,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.topBarContentColor)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = title,
                color = colors.topBarContentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1500,
                        velocity = 40.dp
                    )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { state.setLayoutMode(ViewerLayoutMode.CONTINUOUS_SCROLL) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (state.layoutMode == ViewerLayoutMode.CONTINUOUS_SCROLL) {
                            colors.primaryAccentColor
                        } else colors.topBarContentColor.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Scroll Mode"
                    )
                }

                IconButton(
                    onClick = { state.setLayoutMode(ViewerLayoutMode.HORIZONTAL_SWIPE) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (state.layoutMode == ViewerLayoutMode.HORIZONTAL_SWIPE) {
                            colors.primaryAccentColor
                        } else colors.topBarContentColor.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Swipe Mode"
                    )
                }
            }
        }
    }
}

private fun extractFileName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        } catch (_: Exception) {}
    }
    if (name == null) {
        val path = uri.path
        val cut = path?.lastIndexOf('/') ?: -1
        if (cut != -1) name = path?.substring(cut + 1)
    }
    return name ?: "PDF Document"
}
