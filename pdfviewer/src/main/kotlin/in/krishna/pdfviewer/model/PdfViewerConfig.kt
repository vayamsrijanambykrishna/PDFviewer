package `in`.krishna.pdfviewer.model

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ViewerLayoutMode {
    CONTINUOUS_SCROLL,
    HORIZONTAL_SWIPE
}

@Immutable
data class PdfViewerColors(
    val backgroundColor: Color,
    val primaryAccentColor: Color,
    val topBarBackgroundColor: Color,
    val topBarContentColor: Color,
    val pageBadgeBackgroundColor: Color,
    val pageBadgeTextColor: Color,
    val scrollbarPillColor: Color,
    val scrollbarPillTextColor: Color,
    val scrollbarHandleColor: Color,
    val carouselBackgroundColor: Color,
    val carouselSelectedBorderColor: Color,
    val carouselUnselectedBorderColor: Color,
    val carouselPageIndicatorTextColor: Color
)

@Immutable
data class CarouselConfig(
    val enabled: Boolean = true,
    val height: Dp = 140.dp,
    val cardWidth: Dp = 85.dp,
    val cardHeight: Dp = 115.dp,
    val cardSpacing: Dp = (-24).dp,
    val maxRotationY: Float = 32f,
    val selectedBorderWidth: Dp = 2.dp,
    val unselectedBorderWidth: Dp = 0.6.dp, // Ultra-thin border
    val cardShape: Shape = RoundedCornerShape(8.dp),
    val showPageIndicator: Boolean = true
)

@Immutable
data class PdfViewerConfig(
    val maxZoomScale: Float = 6.0f,
    val initialMode: ViewerLayoutMode = ViewerLayoutMode.CONTINUOUS_SCROLL,
    val pageSpacing: Dp = 10.dp,
    // Page 1 header aur last page bottom clipping fix ke liye insets
    val contentPadding: PaddingValues = PaddingValues(top = 56.dp, bottom = 64.dp),
    val enableFastScrollbar: Boolean = true,
    val autoHideTopBarOnZoom: Boolean = true
)

object PdfViewerDefaults {

    @Composable
    fun colors(
        backgroundColor: Color = MaterialTheme.colorScheme.background,
        primaryAccentColor: Color = MaterialTheme.colorScheme.primary,
        topBarBackgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        topBarContentColor: Color = MaterialTheme.colorScheme.onSurface,
        pageBadgeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        pageBadgeTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        scrollbarPillColor: Color = MaterialTheme.colorScheme.primary,
        scrollbarPillTextColor: Color = MaterialTheme.colorScheme.onPrimary,
        scrollbarHandleColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        // FLOATING TRANSPARENT CAROUSEL (Koi solid background block nahi)
        carouselBackgroundColor: Color = Color.Transparent,
        carouselSelectedBorderColor: Color = MaterialTheme.colorScheme.primary,
        carouselUnselectedBorderColor: Color = Color.Black.copy(alpha = 0.65f), // Ultra-thin crisp border
        carouselPageIndicatorTextColor: Color = MaterialTheme.colorScheme.onSurface
    ): PdfViewerColors = PdfViewerColors(
        backgroundColor = backgroundColor,
        primaryAccentColor = primaryAccentColor,
        topBarBackgroundColor = topBarBackgroundColor,
        topBarContentColor = topBarContentColor,
        pageBadgeBackgroundColor = pageBadgeBackgroundColor,
        pageBadgeTextColor = pageBadgeTextColor,
        scrollbarPillColor = scrollbarPillColor,
        scrollbarPillTextColor = scrollbarPillTextColor,
        scrollbarHandleColor = scrollbarHandleColor,
        carouselBackgroundColor = carouselBackgroundColor,
        carouselSelectedBorderColor = carouselSelectedBorderColor,
        carouselUnselectedBorderColor = carouselUnselectedBorderColor,
        carouselPageIndicatorTextColor = carouselPageIndicatorTextColor
    )
}
