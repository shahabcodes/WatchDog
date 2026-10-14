package com.shahaabapps.watchdog.util

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/** OpenFreeMap "liberty" vector style — free, OpenStreetMap-based, no API key. */
const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Controls an [OpenFreeMap]: holds the live [MapLibreMap], exposes camera moves,
 * and projects geographic coordinates to screen pixels so overlays (markers,
 * geofence circles, paths) can be drawn in a Compose Canvas on top of the tiles.
 */
class OpenFreeMapController internal constructor(
    initialLat: Double,
    initialLng: Double,
    initialZoom: Double,
) {
    internal var map: MapLibreMap? = null
    /** Bumped on every camera move so overlays re-project. */
    internal val tick = mutableIntStateOf(0)
    internal val initial = Triple(initialLat, initialLng, initialZoom)

    fun hasMap(): Boolean = map != null

    fun moveTo(lat: Double, lng: Double, zoom: Double) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    fun animateTo(lat: Double, lng: Double, zoom: Double, durationMs: Int = 700) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom), durationMs)
    }

    /** Frame all [points] (route + stops) within the viewport with padding. */
    fun fitBounds(points: List<Pair<Double, Double>>, paddingPx: Int = 140, durationMs: Int = 700) {
        val m = map ?: return
        if (points.isEmpty()) return
        if (points.size == 1) {
            animateTo(points[0].first, points[0].second, 16.5, durationMs)
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.first, it.second)) }
        runCatching {
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx), durationMs)
        }
    }

    /** Screen pixel position of a coordinate, or null before the map is ready. */
    fun toScreen(lat: Double, lng: Double): Offset? {
        val m = map ?: return null
        val p = m.projection.toScreenLocation(LatLng(lat, lng))
        return Offset(p.x, p.y)
    }

    /** How many screen pixels a distance in metres spans at the given latitude. */
    fun pixelsForMeters(lat: Double, meters: Double): Float {
        val m = map ?: return 0f
        val metersPerPixel = m.projection.getMetersPerPixelAtLatitude(lat)
        if (metersPerPixel <= 0.0) return 0f
        return (meters / metersPerPixel).toFloat()
    }
}

@Composable
fun rememberOpenFreeMapController(
    lat: Double,
    lng: Double,
    zoom: Double,
): OpenFreeMapController = remember { OpenFreeMapController(lat, lng, zoom) }

/**
 * A lightweight, non-interactive map preview rendered as a one-shot bitmap via
 * [MapSnapshotter] — no persistent GL surface. Use this for small previews (e.g.
 * the dashboard) so there's only ever one live [MapView] (the Map tab), which
 * keeps tab switches smooth. A "current location" dot is drawn at the centre.
 */
@Composable
fun OpenFreeMapSnapshot(
    lat: Double,
    lng: Double,
    zoom: Double,
    modifier: Modifier = Modifier,
    dotColor: Color = Color(0xFF4285F4),
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val snapshotter = remember { androidx.compose.runtime.mutableStateOf<MapSnapshotter?>(null) }
    DisposableEffect(Unit) {
        onDispose { runCatching { snapshotter.value?.cancel() } }
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        // The snapshotter bakes a small attribution ("OpenFreeMap") into the
        // bottom of the bitmap. Render a little taller and crop that band off;
        // attribution lives in the app's About screen instead.
        val cropPx = with(density) { 22.dp.toPx() }.toInt()

        // Defer the GL snapshot so it doesn't contend with the tab-switch transition
        // (the Map tab's live map is tearing down at the same moment). The card shows
        // a light placeholder until the image is ready.
        LaunchedEffect(lat, lng, zoom, wPx, hPx) {
            kotlinx.coroutines.delay(400)
            runCatching { snapshotter.value?.cancel() }
            MapLibre.getInstance(context)
            val renderedH = hPx + cropPx
            // Force the zoom deterministically: the snapshotter can let the
            // style's default camera override withCameraPosition, so compute the
            // exact lat/lng bounds this zoom covers for the image size and fit
            // that region instead.
            val latRad = Math.toRadians(lat)
            val metersPerPx = 156543.03392 * kotlin.math.cos(latRad) / Math.pow(2.0, zoom)
            val halfWMeters = metersPerPx * wPx / 2.0
            val halfHMeters = metersPerPx * renderedH / 2.0
            val dLat = halfHMeters / 111_320.0
            val dLng = halfWMeters / (111_320.0 * kotlin.math.cos(latRad).coerceAtLeast(1e-6))
            val bounds = LatLngBounds.from(
                (lat + dLat).coerceAtMost(85.0),
                lng + dLng,
                (lat - dLat).coerceAtLeast(-85.0),
                lng - dLng,
            )
            val options = MapSnapshotter.Options(wPx, renderedH)
                .withStyle(OPENFREEMAP_STYLE)
                .withRegion(bounds)
                .withLogo(false)
            val s = MapSnapshotter(context, options)
            snapshotter.value = s
            s.start(
                { snap ->
                    val full = snap.bitmap
                    bitmap = runCatching {
                        android.graphics.Bitmap.createBitmap(full, 0, 0, full.width, hPx.coerceAtMost(full.height))
                    }.getOrDefault(full)
                },
                { /* ignore snapshot errors */ },
            )
        }

        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE8EEF3)))
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val core = with(density) { 6.dp.toPx() }
            drawCircle(color = Color.White, radius = core * 1.6f, center = c)
            drawCircle(color = dotColor, radius = core, center = c)
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = core * 0.32f, center = c)
        }
    }
}

@Composable
fun OpenFreeMap(
    controller: OpenFreeMapController,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    onMapClick: ((lat: Double, lng: Double) -> Unit)? = null,
    overlay: (DrawScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clickState = rememberUpdatedState(onMapClick)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.getMapAsync { map ->
            controller.map = map
            map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE))
            val (lat, lng, zoom) = controller.initial
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(zoom)
                .build()
            map.uiSettings.apply {
                setAllGesturesEnabled(gesturesEnabled)
                isTiltGesturesEnabled = false
                isRotateGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
            }
            map.addOnCameraMoveListener { controller.tick.intValue++ }
            map.addOnCameraIdleListener { controller.tick.intValue++ }
            map.addOnMapClickListener { point ->
                clickState.value?.invoke(point.latitude, point.longitude)
                true
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.map = null
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(gesturesEnabled) {
        controller.map?.uiSettings?.apply {
            setAllGesturesEnabled(gesturesEnabled)
            isTiltGesturesEnabled = false
            isRotateGesturesEnabled = false
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (overlay != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                controller.tick.intValue // observe → redraw when the camera moves
                if (controller.map != null) overlay()
            }
        }
    }
}

// ---- Draw helpers (call inside the OpenFreeMap `overlay` DrawScope) ----

fun DrawScope.drawGeoCircle(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    radiusMeters: Double,
    fill: Color,
    stroke: Color,
    strokeWidthPx: Float,
) {
    val center = controller.toScreen(lat, lng) ?: return
    val r = controller.pixelsForMeters(lat, radiusMeters)
    if (r <= 0f) return
    drawCircle(color = fill, radius = r, center = center)
    drawCircle(color = stroke, radius = r, center = center, style = Stroke(width = strokeWidthPx))
}

fun DrawScope.drawGeoPolygon(
    controller: OpenFreeMapController,
    points: List<Pair<Double, Double>>,
    fill: Color,
    stroke: Color,
    strokeWidthPx: Float,
) {
    if (points.size < 3) return
    val path = Path()
    points.forEachIndexed { i, (la, ln) ->
        val o = controller.toScreen(la, ln) ?: return
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color = fill)
    drawPath(path, color = stroke, style = Stroke(width = strokeWidthPx))
}

fun DrawScope.drawGeoPolyline(
    controller: OpenFreeMapController,
    points: List<Pair<Double, Double>>,
    color: Color,
    widthPx: Float,
) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { i, (la, ln) ->
        val o = controller.toScreen(la, ln) ?: return
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    drawPath(path, color = color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A Google-Maps-navigation-style route line: a darker casing under a lighter
 * core, smoothed into a flowing curve (Catmull-Rom spline) instead of jagged
 * straight segments, with rounded caps/joins.
 */
fun DrawScope.drawGeoRoute(
    controller: OpenFreeMapController,
    points: List<Pair<Double, Double>>,
    core: Color,
    casing: Color,
    coreWidthPx: Float,
    casingWidthPx: Float,
) {
    if (points.size < 2) return
    val screen = ArrayList<Offset>(points.size)
    for ((la, ln) in points) {
        val o = controller.toScreen(la, ln) ?: return
        screen.add(o)
    }
    val path = smoothPath(screen)
    drawPath(path, color = casing, style = Stroke(width = casingWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color = core, style = Stroke(width = coreWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Builds a smooth curve through [pts] using a Catmull-Rom → cubic Bézier conversion. */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size < 3) {
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i - 1 < 0) 0 else i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 >= pts.size) pts.size - 1 else i + 2]
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}

/** A circular themed badge with a tinted icon, like the old Google map markers. */
fun DrawScope.drawMapMarker(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    accent: Color,
    diameterPx: Float,
    icon: Painter? = null,
    iconPx: Float = diameterPx * 0.55f,
    ring: Color = Color.White,
) {
    val o = controller.toScreen(lat, lng) ?: return
    val r = diameterPx / 2f
    // Soft drop shadow for depth.
    drawCircle(color = Color.Black.copy(alpha = 0.16f), radius = r * 0.98f, center = Offset(o.x, o.y + r * 0.32f))
    drawCircle(color = ring, radius = r, center = o)
    drawCircle(color = accent, radius = r * 0.84f, center = o)
    if (icon != null) {
        translate(left = o.x - iconPx / 2f, top = o.y - iconPx / 2f) {
            with(icon) { draw(size = Size(iconPx, iconPx), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}

/** A stop pin with a chronological number, matching the timeline list. */
fun DrawScope.drawNumberedMarker(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    accent: Color,
    diameterPx: Float,
    number: Int,
    ring: Color = Color.White,
) {
    val o = controller.toScreen(lat, lng) ?: return
    val r = diameterPx / 2f
    drawCircle(color = Color.Black.copy(alpha = 0.16f), radius = r * 0.98f, center = Offset(o.x, o.y + r * 0.32f))
    drawCircle(color = ring, radius = r, center = o)
    drawCircle(color = accent, radius = r * 0.84f, center = o)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = diameterPx * 0.5f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val baseline = o.y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(number.toString(), o.x, baseline, paint)
}

/** A Google-Maps-style "current location" dot: soft halo + white-bordered core. */
fun DrawScope.drawLiveDot(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    accent: Color,
    coreDiameterPx: Float,
    haloDiameterPx: Float = coreDiameterPx * 2.8f,
    pulse: Float = 0f,
) {
    val o = controller.toScreen(lat, lng) ?: return
    val core = coreDiameterPx / 2f
    // Expanding pulse ring (0..1), fading as it grows.
    if (pulse > 0f) {
        val pr = core * 1.1f + pulse * coreDiameterPx * 1.8f
        drawCircle(color = accent.copy(alpha = 0.32f * (1f - pulse)), radius = pr, center = o)
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.08f), Color.Transparent),
            center = o,
            radius = haloDiameterPx / 2f,
        ),
        radius = haloDiameterPx / 2f,
        center = o,
    )
    drawCircle(color = Color.White, radius = core * 1.5f, center = o)
    drawCircle(color = accent, radius = core, center = o)
    drawCircle(color = Color.White.copy(alpha = 0.85f), radius = core * 0.32f, center = o)
}

/** A small hollow vertex dot for area drawing. */
fun DrawScope.drawVertex(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    accent: Color,
    diameterPx: Float,
) {
    val o = controller.toScreen(lat, lng) ?: return
    val r = diameterPx / 2f
    drawCircle(color = Color.White, radius = r, center = o)
    drawCircle(color = accent, radius = r, center = o, style = Stroke(width = diameterPx * 0.12f))
    drawCircle(color = accent, radius = r * 0.5f, center = o)
}
