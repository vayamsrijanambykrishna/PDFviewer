package `in`.krishna.pdfviewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.krishna.pdfviewer.core.PdfEngine
import `in`.krishna.pdfviewer.model.CarouselConfig
import `in`.krishna.pdfviewer.model.PdfViewerColors
import kotlin.math.absoluteValue

@Composable
fun ThumbnailCarousel3D(
    deckPagerState: PagerState,
    pageCount: Int,
    engine: PdfEngine,
    colors: PdfViewerColors,
    config: CarouselConfig = CarouselConfig(),
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!config.enabled) return

    val density = LocalDensity.current
    val thumbPixelWidth = remember(config.cardWidth, density) {
        with(density) { (config.cardWidth * 2.5f).roundToPx().coerceAtLeast(240) }
    }

    // Pura outer container transparent hai (No white solid strip)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(config.height)
            .background(colors.carouselBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (config.showPageIndicator) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "${deckPagerState.currentPage + 1}/$pageCount",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalPager(
            state = deckPagerState,
            contentPadding = PaddingValues(horizontal = 140.dp),
            pageSpacing = config.cardSpacing,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageOffset = (deckPagerState.currentPage - pageIndex) + deckPagerState.currentPageOffsetFraction
            val isSelected = pageIndex == deckPagerState.currentPage

            var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(pageIndex, thumbPixelWidth) {
                thumbBitmap = engine.renderPageBackground(pageIndex, thumbPixelWidth, 1.0f)
            }

            Box(
                modifier = Modifier
                    .width(config.cardWidth)
                    .height(config.cardHeight)
                    .graphicsLayer {
                        cameraDistance = 14 * density.density
                        rotationY = (pageOffset * config.maxRotationY).coerceIn(-45f, 45f)
                        val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 0.3f)
                        scaleX = scale
                        scaleY = scale
                        alpha = if (isSelected) 1f else 0.65f
                    }
                    .shadow(
                        elevation = if (isSelected) 8.dp else 4.dp,
                        shape = config.cardShape,
                        clip = false
                    )
                    .clip(config.cardShape)
                    .background(Color.White)
                    // ULTRA-THIN BORDER (Crisp floating page look)
                    .border(
                        border = if (isSelected) {
                            BorderStroke(config.selectedBorderWidth, colors.carouselSelectedBorderColor)
                        } else {
                            BorderStroke(config.unselectedBorderWidth, colors.carouselUnselectedBorderColor)
                        },
                        shape = config.cardShape
                    )
                    .clickable { onThumbnailClick(pageIndex) },
                contentAlignment = Alignment.Center
            ) {
                thumbBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Thumbnail ${pageIndex + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
