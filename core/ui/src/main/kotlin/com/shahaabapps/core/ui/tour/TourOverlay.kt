package com.shahaabapps.core.ui.tour

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.shahaabapps.core.ui.components.rememberReducedMotion
import com.shahaabapps.core.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Runs the tour and draws it over the whole screen (bottom bar included). Put
 * it last inside the screen's root Box. While a sheet or menu with a
 * [TourLayer] is open, that layer draws instead, since it sits in a window above.
 * System Back steps back (and leaves the tour on step 1).
 */
@Composable
fun TourHost(controller: TourController, navigator: TourNavigator, modifier: Modifier = Modifier) {
    val currentNavigator by rememberUpdatedState(navigator)
    LaunchedEffect(controller) { controller.drive { currentNavigator } }
    if (!controller.active) return
    BackHandler(enabled = controller.layers.isEmpty()) { controller.back() }
    if (controller.layers.isEmpty()) TourSpotlight(controller, modifier.fillMaxSize())
}

/**
 * Put inside a ModalBottomSheet's or DropdownMenu's content: while the tour runs,
 * it opens a full-screen popup above that window, so the spotlight isn't hidden
 * under the sheet. Back in the popup steps the tour back.
 */
@Composable
fun TourLayer() {
    val tour = LocalTour.current ?: return
    if (!tour.active) return
    val token = remember { Any() }
    DisposableEffect(tour, token) {
        tour.layers.add(token)
        onDispose { tour.layers.remove(token) }
    }
    if (tour.layers.lastOrNull() !== token) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val screen = remember(context) { screenSize(context) }
    Popup(
        popupPositionProvider = FullScreenPosition,
        onDismissRequest = { tour.back() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        with(density) {
            TourSpotlight(tour, Modifier.requiredSize(screen.width.toDp(), screen.height.toDp()))
        }
    }
}

private object FullScreenPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

// The activity's full window size (a sheet's or menu's own window can be smaller).
private fun screenSize(context: Context): IntSize {
    var c: Context? = context
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    val decor = (c as? Activity)?.window?.decorView
    if (decor != null && decor.width > 0) return IntSize(decor.width, decor.height)
    val metrics = context.resources.displayMetrics
    return IntSize(metrics.widthPixels, metrics.heightPixels)
}

/**
 * The dimmed scrim with a rounded cut-out and glow ring around the current
 * target, and the explanation bubble beside it. Swallows every touch: the tour
 * is driven by its buttons only.
 */
@Composable
private fun TourSpotlight(tour: TourController, modifier: Modifier) {
    val step = tour.current ?: return
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    // This overlay's own top-left on screen; targets report screen coordinates.
    var origin by remember { mutableStateOf<Offset?>(null) }
    val pad = with(density) { 8.dp.toPx() }
    val corner = with(density) { 18.dp.toPx() }

    // The target, in this overlay's coordinates, with breathing room.
    val target = if (tour.ready) tour.bounds[step.target] else null
    val hole = origin?.let { o -> target?.translate(-o)?.inflate(pad) }

    // Slide the cut-out from the last target to the new one (snap with reduced motion).
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(hole) {
        if (hole == null) return@LaunchedEffect
        if (!placed || reduced) rect.snapTo(hole) else rect.animateTo(hole, tween(280, easing = FastOutSlowInEasing))
        placed = true
    }
    val holeAlpha by animateFloatAsState(
        targetValue = if (hole != null && placed) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(160),
        label = "tourHole",
    )

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrim = Color.Black.copy(alpha = if (dark) 0.74f else 0.62f)
    val glow = AppTheme.colors.gold

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInWindow() + view.windowOrigin() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(scrim)
            if (holeAlpha > 0f) {
                val r = rect.value
                val radius = CornerRadius(corner, corner)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = r.topLeft,
                    size = r.size,
                    cornerRadius = radius,
                    alpha = holeAlpha,
                    blendMode = BlendMode.DstOut,
                )
                // Soft glow: a wide faint ring under a crisp one.
                val wide = 10.dp.toPx()
                drawRoundRect(
                    color = glow.copy(alpha = 0.22f * holeAlpha),
                    topLeft = r.topLeft - Offset(wide / 2, wide / 2),
                    size = Size(r.width + wide, r.height + wide),
                    cornerRadius = CornerRadius(corner + wide / 2, corner + wide / 2),
                    style = Stroke(width = wide),
                )
                drawRoundRect(
                    color = glow.copy(alpha = holeAlpha),
                    topLeft = r.topLeft,
                    size = r.size,
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        if (hole != null) {
            TourBubbleLayout(hole = hole) {
                TourBubble(tour, step)
                BubbleArrow()
            }
        }
    }
}

/** Places the bubble below the target (or above, whichever has room), clamped to the screen, with its arrow. */
@Composable
private fun TourBubbleLayout(hole: Rect, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val margin = 16.dp.roundToPx()
        val gap = 6.dp.roundToPx()
        val top = insets.getTop(this) + margin
        val bottom = h - insets.getBottom(this) - margin
        val bubble = measurables[0].measure(
            Constraints(maxWidth = (w - 2 * margin).coerceAtMost(360.dp.roundToPx()).coerceAtLeast(0)),
        )
        val arrow = measurables[1].measure(Constraints())
        val need = bubble.height + arrow.height + gap
        val fitsBelow = bottom - hole.bottom >= need
        val fitsAbove = hole.top - top >= need
        val below = fitsBelow || (!fitsAbove && bottom - hole.bottom >= hole.top - top)
        val y = when {
            fitsBelow && below -> hole.bottom.roundToInt() + gap + arrow.height
            fitsAbove -> hole.top.roundToInt() - gap - arrow.height - bubble.height
            // A target taller than the room left: float the bubble near the bottom.
            else -> bottom - bubble.height
        }.coerceIn(top, (bottom - bubble.height).coerceAtLeast(top))
        val x = (hole.center.x.roundToInt() - bubble.width / 2)
            .coerceIn(margin, (w - margin - bubble.width).coerceAtLeast(margin))
        val inset = 22.dp.roundToPx()
        val arrowX = hole.center.x.roundToInt()
            .coerceIn(x + inset, (x + bubble.width - inset).coerceAtLeast(x + inset)) - arrow.width / 2
        val showArrow = fitsBelow || fitsAbove
        layout(w, h) {
            bubble.place(x, y)
            if (showArrow) {
                val arrowY = if (below) y - arrow.height else y + bubble.height
                arrow.placeWithLayer(arrowX, arrowY) { rotationZ = if (below) 180f else 0f }
            }
        }
    }
}

/** A small triangle pointing down (rotated to point up when the bubble sits below). */
@Composable
private fun BubbleArrow() {
    val color = AppTheme.colors.card
    Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2, size.height)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun TourBubble(tour: TourController, step: TourStep) {
    val last = tour.index == tour.steps.lastIndex
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.card,
        shadowElevation = 8.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Row {
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${tour.index + 1}/${tour.steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                )
            }
            Text(
                step.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                if (!last) BubbleTextButton("Skip tour", AppTheme.colors.secondaryLabel) { tour.skip() }
                Spacer(Modifier.weight(1f))
                if (tour.index > 0) BubbleTextButton("Back", AppTheme.colors.accent) { tour.back() }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppTheme.colors.gold)
                        .clickable { tour.next() }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(
                        if (last) "Done" else "Next",
                        color = AppTheme.colors.onGold,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            val link = tour.finalLink
            if (last && link != null) {
                BubbleTextButton(link, AppTheme.colors.accent, Modifier.padding(top = 2.dp)) { tour.end(TourExit.FinalLink) }
            }
        }
    }
}

@Composable
private fun BubbleTextButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}
