package `in`.krishna.pdfviewer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmoothZoomPanBox(
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    allowVerticalPan: Boolean = true,
    canScrollBackward: () -> Boolean = { true },
    canScrollForward: () -> Boolean = { true },
    maxScale: Float = 6.0f,
    onVerticalScroll: (Float) -> Unit = {},
    onScaleChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    val animOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showZoomHud by remember { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        flingJob?.cancel()
        scale = 1f
        currentOffset = Offset.Zero
        animOffset.snapTo(Offset.Zero)
        onScaleChanged(1f)
    }

    LaunchedEffect(scale) {
        if (scale > 1.05f) {
            showZoomHud = true
            delay(1500)
            showZoomHud = false
        } else {
            showZoomHud = false
        }
    }

    val cWidth = containerSize.width.toFloat()
    val cHeight = containerSize.height.toFloat()

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize, allowVerticalPan) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        flingJob?.cancel()
                        coroutineScope.launch {
                            val newScale = when {
                                scale < 2.0f -> 2.5f
                                scale < 4.0f -> 4.5f
                                else -> 1.0f
                            }
                            scale = newScale

                            if (newScale == 1.0f) {
                                animOffset.animateTo(
                                    Offset.Zero,
                                    spring(stiffness = Spring.StiffnessMedium)
                                ) {
                                    currentOffset = value
                                }
                            } else {
                                val center = Offset(cWidth / 2f, cHeight / 2f)
                                val tapRel = tapOffset - center
                                val targetOffset = -tapRel * (newScale - 1f)

                                val maxOffsetX = (cWidth * newScale - cWidth).coerceAtLeast(0f) / 2f
                                val maxOffsetY = if (allowVerticalPan) {
                                    (cHeight * newScale - cHeight).coerceAtLeast(0f) / 2f
                                } else 0f

                                val clamped = Offset(
                                    targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    if (allowVerticalPan) targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY) else 0f
                                )
                                animOffset.animateTo(
                                    clamped,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                ) {
                                    currentOffset = value
                                }
                            }
                            onScaleChanged(newScale)
                        }
                    }
                )
            }
            .pointerInput(containerSize, allowVerticalPan) {
                val decay = exponentialDecay<Offset>(frictionMultiplier = 2.2f)
                val velocityTracker = VelocityTracker()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    flingJob?.cancel()
                    coroutineScope.launch { animOffset.stop() }
                    currentOffset = animOffset.value

                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val oldScale = scale
                            val newScale = (oldScale * zoomChange).coerceIn(1f, maxScale)
                            scale = newScale

                            if (newScale > 1.01f) {
                                val center = Offset(cWidth / 2f, cHeight / 2f)
                                val centroidRel = centroid - center
                                val target = (currentOffset - centroidRel) * (newScale / oldScale) + centroidRel + panChange

                                val maxOffsetX = (cWidth * newScale - cWidth).coerceAtLeast(0f) / 2f
                                val maxOffsetY = (cHeight * newScale - cHeight).coerceAtLeast(0f) / 2f

                                val clampedX = target.x.coerceIn(-maxOffsetX - 20f, maxOffsetX + 20f)
                                
                                val clampedY = if (allowVerticalPan) {
                                    target.y.coerceIn(-maxOffsetY - 20f, maxOffsetY + 20f)
                                } else {
                                    // HYBRID LOGIC: Normal pages = Old Code | Edge pages = Swipe Mode Pan
                                    val dy = panChange.y
                                    var y = currentOffset.y

                                    if (y > 0f) {
                                        // Top edge se neeche panned hai: wapas scroll ki taraf bhejo
                                        if (dy < 0f) {
                                            val newY = (y + dy).coerceAtLeast(0f)
                                            val remaining = dy - (newY - y)
                                            y = newY
                                            if (remaining < 0f) onVerticalScroll(remaining)
                                        } else {
                                            y = (y + dy).coerceAtMost(maxOffsetY)
                                        }
                                    } else if (y < 0f) {
                                        // Bottom edge se upar panned hai: wapas scroll ki taraf bhejo
                                        if (dy > 0f) {
                                            val newY = (y + dy).coerceAtMost(0f)
                                            val remaining = dy - (newY - y)
                                            y = newY
                                            if (remaining > 0f) onVerticalScroll(remaining)
                                        } else {
                                            y = (y + dy).coerceAtLeast(-maxOffsetY)
                                        }
                                    } else {
                                        // y == 0f: Edge detection
                                        if (dy > 0f && !canScrollBackward()) {
                                            // Page 1 ka top aa gaya: Swipe mode ki tarah pan down
                                            y = (y + dy).coerceAtMost(maxOffsetY)
                                        } else if (dy < 0f && !canScrollForward()) {
                                            // Last page ka bottom aa gaya: Swipe mode ki tarah pan up
                                            y = (y + dy).coerceAtLeast(-maxOffsetY)
                                        } else {
                                            // Beech ke saare pages: 100% Old Code Scroll
                                            onVerticalScroll(dy)
                                        }
                                    }
                                    y
                                }

                                currentOffset = Offset(clampedX, clampedY)
                                event.changes.forEach { it.consume() }
                            } else {
                                currentOffset = Offset.Zero
                            }
                        }

                        event.changes.firstOrNull()?.let {
                            velocityTracker.addPosition(it.uptimeMillis, it.position)
                        }
                    } while (event.changes.any { it.pressed })

                    val maxOffsetX = (cWidth * scale - cWidth).coerceAtLeast(0f) / 2f
                    val maxOffsetY = (cHeight * scale - cHeight).coerceAtLeast(0f) / 2f

                    val targetClampedX = currentOffset.x.coerceIn(-maxOffsetX, maxOffsetX)
                    val targetClampedY = currentOffset.y.coerceIn(-maxOffsetY, maxOffsetY)

                    onScaleChanged(scale)

                    if (scale > 1.01f) {
                        val velocity = velocityTracker.calculateVelocity()
                        val flingVelocity = if (allowVerticalPan || currentOffset.y != 0f) {
                            Offset(velocity.x, velocity.y)
                        } else {
                            Offset(velocity.x, 0f)
                        }

                        flingJob = coroutineScope.launch {
                            animOffset.snapTo(currentOffset)

                            if (currentOffset.x != targetClampedX || currentOffset.y != targetClampedY) {
                                animOffset.animateTo(
                                    Offset(targetClampedX, targetClampedY),
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                ) {
                                    currentOffset = value
                                }
                            } else if (flingVelocity.getDistance() > 150f) {
                                animOffset.animateDecay(flingVelocity, decay) {
                                    val clampedX = value.x.coerceIn(-maxOffsetX, maxOffsetX)
                                    val clampedY = value.y.coerceIn(-maxOffsetY, maxOffsetY)
                                    currentOffset = Offset(clampedX, clampedY)
                                    if (clampedX != value.x || clampedY != value.y) {
                                        coroutineScope.launch { animOffset.snapTo(currentOffset) }
                                    }
                                }
                            }
                        }
                    } else {
                        currentOffset = Offset.Zero
                        coroutineScope.launch { animOffset.snapTo(Offset.Zero) }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = currentOffset.x
                    translationY = currentOffset.y
                }
        ) {
            content()
        }

        AnimatedVisibility(
            visible = showZoomHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF222222).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}
