package com.shahaabapps.watchdog.util

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

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
                isAttributionEnabled = true
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
    drawCircle(color = ring, radius = r, center = o)
    drawCircle(color = accent, radius = r * 0.84f, center = o)
    if (icon != null) {
        translate(left = o.x - iconPx / 2f, top = o.y - iconPx / 2f) {
            with(icon) { draw(size = Size(iconPx, iconPx), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}

/** A Google-Maps-style "current location" dot: soft halo + white-bordered core. */
fun DrawScope.drawLiveDot(
    controller: OpenFreeMapController,
    lat: Double,
    lng: Double,
    accent: Color,
    coreDiameterPx: Float,
    haloDiameterPx: Float = coreDiameterPx * 2.8f,
) {
    val o = controller.toScreen(lat, lng) ?: return
    val core = coreDiameterPx / 2f
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
