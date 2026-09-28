package `in`.krishna.pdfviewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.krishna.pdfviewer.core.PdfEngine
import `in`.krishna.pdfviewer.model.PdfViewerColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CrispPdfPage(
    pageIndex: Int,
    pageCount: Int,
    engine: PdfEngine,
    colors: PdfViewerColors,
    targetScale: Float = 1.0f,
    isPriority: Boolean = false,
    onAspectRatioLoaded: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(1.414f) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pageIndex) {
        engine.getPageDimensions(pageIndex)?.let { dims ->
            val ratio = dims.second.toFloat() / dims.first.toFloat()
            aspectRatio = ratio
            onAspectRatioLoaded(ratio)
        }
    }

    DisposableEffect(pageIndex, targetScale, isPriority) {
        var renderJob: Job? = null

        if (targetScale <= 0.05f) {
            currentBitmap = null
        } else {
            renderJob = coroutineScope.launch {
                val newBitmap = if (isPriority) {
                    engine.renderPageImmediate(pageIndex, screenWidthPx, targetScale)
                } else {
                    engine.renderPageBackground(pageIndex, screenWidthPx, targetScale)
                }

                if (newBitmap != null) {
                    currentBitmap = newBitmap
                }
            }
        }

        onDispose {
            renderJob?.cancel()
        }
    }

    val pageHeightDp = with(density) { (screenWidthPx * aspectRatio).toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(pageHeightDp),
        contentAlignment = Alignment.Center
    ) {
        currentBitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize()
                )

                // Themed page watermark badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp)
                        .background(
                            color = colors.pageBadgeBackgroundColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${pageIndex + 1}",
                        color = colors.pageBadgeTextColor,
                        fontSize = 11.sp
                    )
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .background(colors.pageBadgeBackgroundColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (targetScale > 0.05f) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.primaryAccentColor
                    )
                }
            }
        }
    }
}
