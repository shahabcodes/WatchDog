package com.shahaabapps.watchdog.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One child as the map widget shows them. */
data class WidgetChild(
    val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAtMillis: Long,
    val batteryPercent: Int,
    val charging: Boolean,
)

/** A recently installed app on a child's phone. */
data class WidgetApp(
    val childId: String,
    val childName: String,
    val packageName: String,
    val label: String,
    /** Base64 PNG launcher icon, or null. */
    val icon: String?,
    val installedAtMillis: Long,
    /** Waiting for the parent's review. */
    val pending: Boolean,
    /** Blocked by a rule, or held blocked while pending. */
    val blocked: Boolean,
)

/** A child's ask waiting for an answer. */
data class WidgetRequest(
    val childId: String,
    val childName: String,
    val requestId: String,
    val text: String,
    val createdAtMillis: Long,
)

data class WidgetSnapshot(
    val signedIn: Boolean = false,
    val children: List<WidgetChild> = emptyList(),
    val apps: List<WidgetApp> = emptyList(),
    val requests: List<WidgetRequest> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

/**
 * The widgets' data, saved in Watch Dog's private storage on this phone (the
 * widgets can't decrypt Firestore themselves while they draw). Holds the last
 * refresh plus one map picture per child.
 */
object WidgetStore {
    private const val DIR = "widgets"
    private const val FILE = "snapshot.json"
    private const val PREFS = "widget_prefs"
    private const val KEY_MAP_CHILD = "map_child"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    @Synchronized
    fun load(context: Context): WidgetSnapshot = runCatching {
        val f = File(dir(context), FILE)
        if (!f.exists()) return WidgetSnapshot()
        val o = JSONObject(f.readText())
        WidgetSnapshot(
            signedIn = o.optBoolean("signedIn"),
            updatedAtMillis = o.optLong("updatedAt"),
            children = o.optJSONArray("children").objects().map {
                WidgetChild(
                    id = it.getString("id"),
                    name = it.optString("name"),
                    latitude = if (it.has("lat")) it.getDouble("lat") else null,
                    longitude = if (it.has("lng")) it.getDouble("lng") else null,
                    locationAtMillis = it.optLong("locAt"),
                    batteryPercent = it.optInt("battery"),
                    charging = it.optBoolean("charging"),
                )
            },
            apps = o.optJSONArray("apps").objects().map {
                WidgetApp(
                    childId = it.getString("childId"),
                    childName = it.optString("childName"),
                    packageName = it.getString("pkg"),
                    label = it.optString("label"),
                    icon = it.optString("icon").ifBlank { null },
                    installedAtMillis = it.optLong("at"),
                    pending = it.optBoolean("pending"),
                    blocked = it.optBoolean("blocked"),
                )
            },
            requests = o.optJSONArray("requests").objects().map {
                WidgetRequest(
                    childId = it.getString("childId"),
                    childName = it.optString("childName"),
                    requestId = it.getString("id"),
                    text = it.optString("text"),
                    createdAtMillis = it.optLong("at"),
                )
            },
        )
    }.getOrDefault(WidgetSnapshot())

    @Synchronized
    fun save(context: Context, snapshot: WidgetSnapshot) {
        val o = JSONObject()
            .put("signedIn", snapshot.signedIn)
            .put("updatedAt", snapshot.updatedAtMillis)
            .put(
                "children",
                JSONArray(
                    snapshot.children.map {
                        JSONObject().put("id", it.id).put("name", it.name)
                            .apply {
                                if (it.latitude != null && it.longitude != null) {
                                    put("lat", it.latitude).put("lng", it.longitude)
                                }
                            }
                            .put("locAt", it.locationAtMillis)
                            .put("battery", it.batteryPercent)
                            .put("charging", it.charging)
                    },
                ),
            )
            .put(
                "apps",
                JSONArray(
                    snapshot.apps.map {
                        JSONObject().put("childId", it.childId).put("childName", it.childName)
                            .put("pkg", it.packageName).put("label", it.label)
                            .put("icon", it.icon.orEmpty()).put("at", it.installedAtMillis)
                            .put("pending", it.pending).put("blocked", it.blocked)
                    },
                ),
            )
            .put(
                "requests",
                JSONArray(
                    snapshot.requests.map {
                        JSONObject().put("childId", it.childId).put("childName", it.childName)
                            .put("id", it.requestId).put("text", it.text).put("at", it.createdAtMillis)
                    },
                ),
            )
        val f = File(dir(context), FILE)
        val tmp = File(dir(context), "$FILE.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    /** Drops a request once it's answered from the widget, so it disappears at once. */
    fun removeRequest(context: Context, requestId: String) {
        val s = load(context)
        save(context, s.copy(requests = s.requests.filterNot { it.requestId == requestId }))
    }

    /** Signed out: forget everything shown on the home screen. */
    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun mapFile(context: Context, childId: String) =
        File(dir(context), "map_${childId.hashCode().toUInt()}.png")

    fun saveMap(context: Context, childId: String, bitmap: Bitmap) {
        runCatching {
            mapFile(context, childId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * The map picture, kept small (at most [MAP_MAX_W] wide, 16-bit colour): a
     * widget's pictures travel to the home screen in one message of limited size.
     */
    fun loadMap(context: Context, childId: String): Bitmap? = runCatching {
        val f = mapFile(context, childId)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val b = BitmapFactory.decodeFile(f.path, opts) ?: return null
        if (b.width <= MAP_MAX_W) b
        else Bitmap.createScaledBitmap(b, MAP_MAX_W, b.height * MAP_MAX_W / b.width, true)
    }.getOrNull()

    private const val MAP_MAX_W = 540

    /** Which child the map widgets show (cycled with the arrow button). */
    fun mapChildId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAP_CHILD, null)

    fun setMapChildId(context: Context, childId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MAP_CHILD, childId).apply()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
