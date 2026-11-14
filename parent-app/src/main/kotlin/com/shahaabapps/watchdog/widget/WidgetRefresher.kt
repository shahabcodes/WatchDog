package com.shahaabapps.watchdog.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.shahaabapps.core.common.Diag
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import com.shahaabapps.watchdog.util.MapStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun auth(): AuthRepository
    fun children(): ChildRepository
    fun device(): DeviceRepository
    fun rules(): AppRulesRepository
    fun pending(): PendingAppsRepository
    fun protection(): ProtectionRepository
    fun requests(): RequestRepository
}

/**
 * Reads the latest location, recent installs and open requests of every linked
 * child (decrypted on this phone), renders a small map per child, saves it all for
 * the widgets and redraws them.
 */
object WidgetRefresher {
    private const val READ_TIMEOUT_MS = 12_000L
    private const val RECENT_APPS_DAYS = 14
    private const val MAX_APPS = 12
    private const val MAP_W = 360
    private const val MAP_H = 200
    private const val PIXEL_RATIO = 1.5f
    private const val MAP_ZOOM = 15.0

    fun deps(context: Context): WidgetEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val d = deps(app)
        val uid = d.auth().currentUserIdOrNull()
        if (uid == null) {
            WidgetStore.clear(app)
            WidgetStore.save(app, WidgetSnapshot(signedIn = false))
            Widgets.updateAll(app)
            return
        }
        val linked = d.children().observeLinkedChildren(uid).firstOrNull().orEmpty()
        val old = WidgetStore.load(app)
        val now = System.currentTimeMillis()
        val children = ArrayList<WidgetChild>()
        val apps = ArrayList<WidgetApp>()
        val requests = ArrayList<WidgetRequest>()
        for (child in linked) {
            val id = child.childId
            val name = child.profile.name.ifBlank { "Child" }
            val location = d.device().observeLocation(id).firstOrNull()
            val previous = old.children.firstOrNull { it.id == id }
            val hasFix = location != null && location.timestampMillis > 0
            children += WidgetChild(
                id = id,
                name = name,
                latitude = if (hasFix) location!!.latitude else previous?.latitude,
                longitude = if (hasFix) location!!.longitude else previous?.longitude,
                locationAtMillis = if (hasFix) location!!.timestampMillis else previous?.locationAtMillis ?: 0L,
                batteryPercent = child.device.batteryPercent,
                charging = child.device.charging,
            )
            if (hasFix && (previous == null || previous.latitude != location!!.latitude ||
                    previous.longitude != location.longitude || WidgetStore.loadMap(app, id) == null)
            ) {
                renderMap(app, location!!.latitude, location.longitude)?.let { WidgetStore.saveMap(app, id, it) }
            }

            val installed = d.rules().observeInstalledApps(id).firstOrNull().orEmpty()
            val rules = d.rules().observeAppRules(id).firstOrNull().orEmpty().associateBy { it.packageName }
            val pending = d.pending().observe(id).firstOrNull().orEmpty().map { it.packageName }.toSet()
            val protection = d.protection().observe(id).firstOrNull()
            val holdBlocked = protection != null && protection.blockNewApps && protection.newAppsBlocked
            val since = now - RECENT_APPS_DAYS * 86_400_000L
            installed.filter { it.installedAtMillis >= since || it.packageName in pending }.forEach { a ->
                val rule = rules[a.packageName]
                val isPending = a.packageName in pending && rule == null
                apps += WidgetApp(
                    childId = id,
                    childName = name,
                    packageName = a.packageName,
                    label = a.label.ifBlank { a.packageName },
                    icon = a.icon,
                    installedAtMillis = a.installedAtMillis,
                    pending = isPending,
                    blocked = rule?.blocked == true || (isPending && holdBlocked),
                )
            }

            d.requests().observe(id, 30).firstOrNull().orEmpty()
                .filter { it.status == RequestStatus.PENDING }
                .forEach { r ->
                    requests += WidgetRequest(id, name, r.id, askText(r), r.createdAtMillis)
                }
        }
        WidgetStore.save(
            app,
            WidgetSnapshot(
                signedIn = true,
                children = children,
                apps = apps.sortedByDescending { it.installedAtMillis }.take(MAX_APPS),
                requests = requests.sortedByDescending { it.createdAtMillis },
                updatedAtMillis = now,
            ),
        )
        Widgets.updateAll(app)
    }

    /** "asks to unlock YouTube", "asks for 15 more minutes of YouTube". */
    private fun askText(r: TimeRequest): String {
        val s = r.summary
        return if (s.startsWith("to ")) "asks $s" else "asks for $s"
    }

    private suspend fun <T> Flow<T>.firstOrNull(): T? =
        withTimeoutOrNull(READ_TIMEOUT_MS) { runCatching { first() }.getOrNull() }

    /** A light or dark street map centred on the spot, with the child's dot drawn on. */
    private suspend fun renderMap(context: Context, lat: Double, lng: Double): Bitmap? =
        withTimeoutOrNull(20_000L) {
            withContext(Dispatchers.Main) {
                runCatching {
                    MapLibre.getInstance(context)
                    // Logical size at 2x pixels, so streets and labels stay crisp on
                    // the home screen; render a strip taller and crop the baked-in
                    // attribution off the bottom (it lives in the app's About screen).
                    val cropDp = 22
                    val h = MAP_H + cropDp
                    val latRad = Math.toRadians(lat)
                    val metersPerPx = 156543.03392 * kotlin.math.cos(latRad) / Math.pow(2.0, MAP_ZOOM)
                    val dLat = metersPerPx * h / 2.0 / 111_320.0
                    val dLng = metersPerPx * MAP_W / 2.0 / (111_320.0 * kotlin.math.cos(latRad).coerceAtLeast(1e-6))
                    val bounds = LatLngBounds.from(
                        (lat + dLat).coerceAtMost(85.0),
                        lng + dLng,
                        (lat - dLat).coerceAtLeast(-85.0),
                        lng - dLng,
                    )
                    val options = MapSnapshotter.Options(MAP_W, h)
                        .withPixelRatio(PIXEL_RATIO)
                        .withStyle(MapStyle.AUTO.resolvedStyleUrl())
                        .withRegion(bounds)
                        .withLogo(false)
                    val snapshotter = MapSnapshotter(context, options)
                    suspendCancellableCoroutine { cont ->
                        cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                        snapshotter.start(
                            { snap ->
                                val full = snap.bitmap
                                val keep = (MAP_H * PIXEL_RATIO).toInt().coerceAtMost(full.height)
                                val out = Bitmap.createBitmap(full, 0, 0, full.width, keep)
                                    .copy(Bitmap.Config.ARGB_8888, true)
                                drawDot(out)
                                if (cont.isActive) cont.resume(out)
                            },
                            { error ->
                                Diag.warn("widget", "map snapshot failed: $error")
                                if (cont.isActive) cont.resume(null)
                            },
                        )
                    }
                }.getOrNull()
            }
        }

    private fun drawDot(bitmap: Bitmap) {
        val c = Canvas(bitmap)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x334285F4
        c.drawCircle(cx, cy, 56f, p)
        p.color = 0x55000000
        c.drawCircle(cx, cy + 2f, 22f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, 21f, p)
        p.color = 0xFFF5B83D.toInt()
        c.drawCircle(cx, cy, 15f, p)
        p.color = 0xFF00133F.toInt()
        c.drawCircle(cx, cy, 6f, p)
    }
}
