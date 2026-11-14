package com.shahaabapps.watchdog.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.compose.ui.unit.DpSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
import com.shahaabapps.core.common.Diag
import com.shahaabapps.watchdog.MainActivity
import com.shahaabapps.watchdog.R
import com.shahaabapps.watchdog.messaging.WatchdogMessagingService
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------- shared look

private val Ink = ColorProvider(day = Color(0xFF0B1630), night = Color(0xFFFFFFFF))
private val Muted = ColorProvider(day = Color(0xFF6B7488), night = Color(0xFF9AA4BD))
private val Accent = ColorProvider(day = Color(0xFF00133F), night = Color(0xFFF5B83D))
private val OnPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF00133F))
private val White = FixedColor(Color.White)
private val WhiteSoft = FixedColor(Color(0xD9FFFFFF))
private val Gold = FixedColor(Color(0xFFB7791F))
private val Red = FixedColor(Color(0xFFD64045))
private val Navy = FixedColor(Color(0xFF00133F))

private fun ago(millis: Long): String {
    if (millis <= 0L) return "a while ago"
    val m = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

/** Opens Watch Dog at a child's map, request or app rule (MainActivity's deep links). */
private fun openApp(context: Context, childId: String?, action: String?, requestId: String? = null, appPackage: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setAction("widget:$action:$childId:${requestId.orEmpty()}:${appPackage.orEmpty()}")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply {
            if (childId != null && action != null) {
                putExtra(WatchdogMessagingService.EXTRA_CHILD_ID, childId)
                putExtra(WatchdogMessagingService.EXTRA_ACTION, action)
                requestId?.let { putExtra(WatchdogMessagingService.EXTRA_REQUEST_ID, it) }
                appPackage?.let { putExtra(WatchdogMessagingService.EXTRA_APP_PACKAGE, it) }
            }
        }

private fun decodeIcon(base64: String?): Bitmap? = base64?.let {
    runCatching {
        val bytes = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

// Sizes each widget lays itself out for; the launcher picks the closest that fits.
private val SIZE_SMALL = DpSize(110.dp, 110.dp)
private val SIZE_WIDE = DpSize(250.dp, 110.dp)
private val SIZE_TALL = DpSize(110.dp, 180.dp)
private val SIZE_LARGE = DpSize(250.dp, 180.dp)
private val RESPONSIVE = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_WIDE, SIZE_TALL, SIZE_LARGE))

/** Narrow (about 2 cells wide). */
@Composable
private fun narrow(): Boolean = LocalSize.current.width < 200.dp

/** Short (about 2 cells high). */
@Composable
private fun short(): Boolean = LocalSize.current.height < 170.dp

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(ImageProvider(R.drawable.widget_card))
            .padding(if (narrow()) 12.dp else 14.dp),
    ) { content() }
}

@Composable
private fun Header(title: String, subtitle: String?, badge: Int? = null, refresh: Boolean = true) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (badge != null && badge > 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        "$badge",
                        modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_count)).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = TextStyle(color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            if (subtitle != null) Text(subtitle, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
        }
        if (refresh) {
            Text(
                "↻",
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(ImageProvider(R.drawable.widget_btn_secondary))
                    .clickable(actionRunCallback<RefreshAction>())
                    .padding(top = 4.dp),
                style = TextStyle(color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun Empty(icon: String, title: String, body: String?, onClick: Intent? = null) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp)
            .let { m -> if (onClick != null) m.clickable(actionStartActivity(onClick)) else m },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center))
        Spacer(GlanceModifier.height(4.dp))
        Text(title, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
        if (body != null && !short()) Text(body, style = TextStyle(color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** The 2 x 2 look: one big number, a label and a line under it. */
@Composable
private fun BigNumber(number: Int, label: String, line: String?, onClick: Intent) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(4.dp).clickable(actionStartActivity(onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = TextStyle(color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (line != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(line, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 2)
        }
    }
}

@Composable
private fun Avatar(name: String, sizeDp: Int) {
    Text(
        name.trim().take(1).uppercase().ifBlank { "?" },
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .background(ImageProvider(R.drawable.widget_avatar))
            .padding(top = (sizeDp / 5).dp),
        style = TextStyle(color = Navy, fontSize = (sizeDp * 0.45f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
    )
}

@Composable
private fun SignedOut() {
    val context = LocalContext.current
    Empty("🔒", "Open Watch Dog", "Sign in to see your family here", openApp(context, null, null))
}

// ---------------------------------------------------------------- map widget

/** A child's live spot on a street map, with when it was seen and their battery. */
class ChildMapWidget : GlanceAppWidget() {
    override val sizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read inside the content: an open widget session recomposes on each
        // update instead of calling provideGlance again.
        provideContent {
            val snapshot = WidgetStore.load(context)
            val chosen = WidgetStore.mapChildId(context)
            val child = snapshot.children.firstOrNull { it.id == chosen } ?: snapshot.children.firstOrNull()
            val map = child?.let { WidgetStore.loadMap(context, it.id) }
            MapContent(snapshot, child, map)
        }
    }
}

@Composable
private fun MapContent(snapshot: WidgetSnapshot, child: WidgetChild?, map: Bitmap?) {
    val context = LocalContext.current
    val narrow = narrow()
    val large = !narrow && !short()
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(ImageProvider(R.drawable.widget_map_bg))
            .clickable(actionStartActivity(openApp(context, child?.id, if (child != null) WatchdogMessagingService.ACTION_OPEN_MAP else null))),
    ) {
        if (map != null) {
            Image(
                provider = ImageProvider(map),
                contentDescription = "Map",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(if (narrow) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (narrow) {
                    if (child != null && child.batteryPercent > 0) Battery(child)
                } else {
                    Text(
                        "●  WATCH DOG",
                        modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_glass_pill)).padding(horizontal = 10.dp, vertical = 5.dp),
                        style = TextStyle(color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                if (!narrow) GlassButton("↻", actionRunCallback<RefreshAction>(), 34)
                if (snapshot.children.size > 1) {
                    if (!narrow) Spacer(GlanceModifier.width(8.dp))
                    GlassButton("›", actionRunCallback<NextChildAction>(), if (narrow) 28 else 34)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_scrim))
                    .padding(
                        start = if (narrow) 10.dp else 16.dp,
                        end = if (narrow) 10.dp else 16.dp,
                        top = if (narrow) 18.dp else 28.dp,
                        bottom = if (narrow) 10.dp else 14.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !snapshot.signedIn -> Text("Open Watch Dog to sign in", style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    child == null -> Text("No children linked yet", style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    else -> {
                        Avatar(child.name, if (narrow) 26 else if (large) 44 else 38)
                        Spacer(GlanceModifier.width(if (narrow) 8.dp else 10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                child.name,
                                style = TextStyle(color = White, fontSize = if (narrow) 14.sp else if (large) 19.sp else 17.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Text(
                                when {
                                    child.latitude == null -> if (narrow) "No location yet" else "Waiting for a location"
                                    narrow -> ago(child.locationAtMillis)
                                    else -> "Seen ${ago(child.locationAtMillis)}"
                                },
                                style = TextStyle(color = WhiteSoft, fontSize = if (narrow) 11.sp else 12.sp),
                                maxLines = 1,
                            )
                        }
                        if (!narrow && child.batteryPercent > 0) Battery(child)
                    }
                }
            }
        }
    }
}

@Composable
private fun Battery(child: WidgetChild) {
    Text(
        (if (child.charging) "⚡ " else "") + "${child.batteryPercent}%",
        modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_glass_pill)).padding(horizontal = 9.dp, vertical = 4.dp),
        style = TextStyle(
            color = if (child.batteryPercent <= 15 && !child.charging) FixedColor(Color(0xFFFF8A8A)) else White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun GlassButton(glyph: String, action: androidx.glance.action.Action, sizeDp: Int) {
    Text(
        glyph,
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .background(ImageProvider(R.drawable.widget_glass_circle))
            .clickable(action)
            .padding(top = (sizeDp / 12).dp),
        style = TextStyle(color = White, fontSize = (sizeDp / 2).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
    )
}

// ---------------------------------------------------------------- recent apps widget

/** Apps installed on the children's phones in the last two weeks, newest first. */
class RecentAppsWidget : GlanceAppWidget() {
    override val sizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot = WidgetStore.load(context)
            val icons = snapshot.apps.associate { it.childId + "/" + it.packageName to decodeIcon(it.icon) }
            AppsContent(snapshot, icons)
        }
    }
}

@Composable
private fun AppsContent(snapshot: WidgetSnapshot, icons: Map<String, Bitmap?>) {
    val context = LocalContext.current
    val narrow = narrow()
    val short = short()
    val toReview = snapshot.apps.count { it.pending }
    fun rules(app: WidgetApp?) = openApp(
        context,
        app?.childId ?: snapshot.children.firstOrNull()?.id,
        WatchdogMessagingService.ACTION_APP_RULES,
        appPackage = app?.packageName,
    )
    Card {
        when {
            !snapshot.signedIn -> SignedOut()
            // 2 x 2: how many, and the newest.
            narrow && short -> {
                val first = snapshot.apps.firstOrNull()
                if (first == null) {
                    Empty("✨", "No new apps", null)
                } else {
                    BigNumber(
                        if (toReview > 0) toReview else snapshot.apps.size,
                        if (toReview > 0) "to review" else "new apps",
                        "${first.label} · ${first.childName}",
                        rules(first),
                    )
                }
            }
            snapshot.apps.isEmpty() -> {
                Header("New apps", if (short) null else "Installed in the last 14 days")
                Empty("✨", "Nothing new", "No apps installed in the last 14 days")
            }
            // 4 x 2: a row of tiles.
            short -> {
                Header("New apps", null, badge = toReview)
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    snapshot.apps.take(4).forEachIndexed { i, app ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(ImageProvider(R.drawable.widget_row))
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .clickable(actionStartActivity(rules(app))),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, icons, 34)
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                app.label,
                                style = TextStyle(color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                maxLines = 1,
                            )
                            Text(
                                when {
                                    app.blocked -> "Blocked"
                                    app.pending -> "Review"
                                    else -> ago(app.installedAtMillis)
                                },
                                style = TextStyle(
                                    color = when {
                                        app.blocked -> Red
                                        app.pending -> Gold
                                        else -> Muted
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // Tall: a list (narrow ones leave out the chips).
            else -> {
                Header(
                    "New apps",
                    if (narrow) null else if (toReview > 0) "$toReview waiting for your review" else "Installed in the last 14 days",
                    badge = toReview,
                    refresh = !narrow,
                )
                LazyColumn {
                    items(snapshot.apps, itemId = { (it.childId + it.packageName).hashCode().toLong() }) { app ->
                        Column {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(ImageProvider(R.drawable.widget_row))
                                    .padding(if (narrow) 8.dp else 10.dp)
                                    .clickable(actionStartActivity(rules(app))),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app, icons, if (narrow) 32 else 38)
                                Spacer(GlanceModifier.width(if (narrow) 8.dp else 10.dp))
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(app.label, style = TextStyle(color = Ink, fontSize = if (narrow) 13.sp else 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Text(
                                        when {
                                            narrow && app.blocked -> "Blocked"
                                            narrow && app.pending -> "To review"
                                            narrow -> app.childName
                                            else -> "${app.childName} · ${ago(app.installedAtMillis)}"
                                        },
                                        style = TextStyle(
                                            color = when {
                                                narrow && app.blocked -> Red
                                                narrow && app.pending -> Gold
                                                else -> Muted
                                            },
                                            fontSize = 12.sp,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                                if (!narrow) {
                                    when {
                                        app.blocked -> Chip("Blocked", R.drawable.widget_chip_blocked, Red)
                                        app.pending -> Chip("Review", R.drawable.widget_chip_review, Gold)
                                    }
                                }
                            }
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: WidgetApp, icons: Map<String, Bitmap?>, sizeDp: Int) {
    val icon = icons[app.childId + "/" + app.packageName]
    Box(
        modifier = GlanceModifier.size(sizeDp.dp).background(ImageProvider(R.drawable.widget_icon_bg)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size((sizeDp * 0.78f).dp))
        } else {
            Text(app.label.take(1).uppercase(), style = TextStyle(color = Accent, fontSize = (sizeDp * 0.42f).sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun Chip(text: String, background: Int, color: FixedColor) {
    Spacer(GlanceModifier.width(6.dp))
    Text(
        text,
        modifier = GlanceModifier.background(ImageProvider(background)).padding(horizontal = 9.dp, vertical = 4.dp),
        style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

// ---------------------------------------------------------------- requests widget

/** The children's open asks, answered right from the home screen. */
class RequestsWidget : GlanceAppWidget() {
    override val sizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { RequestsContent(WidgetStore.load(context)) }
    }
}

@Composable
private fun RequestsContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val narrow = narrow()
    val short = short()
    val n = snapshot.requests.size
    fun view(r: WidgetRequest) = openApp(context, r.childId, WatchdogMessagingService.ACTION_VIEW, requestId = r.requestId)
    Card {
        when {
            !snapshot.signedIn -> SignedOut()
            // 2 x 2: how many, and the newest ask (tap to answer in the app).
            narrow && short -> {
                val first = snapshot.requests.firstOrNull()
                if (first == null) {
                    Empty("✅", "No requests", null)
                } else {
                    BigNumber(n, if (n == 1) "request" else "requests", "${first.childName} ${first.text}", view(first))
                }
            }
            n == 0 -> {
                Header("Requests", if (short) null else "All caught up")
                Empty("✅", "No requests", "Your children's asks will show up here")
            }
            // 4 x 2: the newest ask with its buttons; the rest are counted.
            short -> {
                Header("Requests", if (n > 1) "+${n - 1} more" else null, badge = n)
                RequestCard(snapshot.requests.first(), compactButtons = false, onOpen = view(snapshot.requests.first()))
            }
            else -> {
                Header(
                    "Requests",
                    if (narrow) null else if (n == 1) "1 ask waiting" else "$n asks waiting",
                    badge = n,
                    refresh = !narrow,
                )
                LazyColumn {
                    items(snapshot.requests, itemId = { it.requestId.hashCode().toLong() }) { r ->
                        Column {
                            RequestCard(r, compactButtons = narrow, onOpen = view(r))
                            Spacer(GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(r: WidgetRequest, compactButtons: Boolean, onOpen: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_row))
            .padding(if (compactButtons) 10.dp else 12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(onOpen)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compactButtons) {
                Avatar(r.childName, 32)
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(r.childName, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(r.text, style = TextStyle(color = Ink, fontSize = 13.sp), maxLines = if (compactButtons) 3 else 2)
            }
            if (!compactButtons) Text(ago(r.createdAtMillis), style = TextStyle(color = Muted, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(if (compactButtons) 8.dp else 10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            ActionButton(if (compactButtons) "✓" else "Approve", primary = true, r, approved = true)
            Spacer(GlanceModifier.width(8.dp))
            ActionButton(if (compactButtons) "✕" else "Not now", primary = false, r, approved = false)
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.ActionButton(label: String, primary: Boolean, r: WidgetRequest, approved: Boolean) {
    Text(
        label,
        modifier = GlanceModifier
            .defaultWeight()
            .background(ImageProvider(if (primary) R.drawable.widget_btn_primary else R.drawable.widget_btn_secondary))
            .padding(vertical = 9.dp)
            .clickable(
                actionRunCallback<RespondAction>(
                    actionParametersOf(
                        RespondAction.CHILD to r.childId,
                        RespondAction.REQUEST to r.requestId,
                        RespondAction.APPROVED to approved,
                    ),
                ),
            ),
        style = TextStyle(
            color = if (primary) OnPrimary else Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
    )
}

// ---------------------------------------------------------------- actions

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Redraw from what's saved at once (relative times move on), then fetch.
        Widgets.updateAll(context)
        WidgetRefreshWorker.refreshNow(context)
    }
}

/** Shows the next child on the map widgets. */
class NextChildAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val children = WidgetStore.load(context).children
        if (children.isEmpty()) return
        val current = children.indexOfFirst { it.id == WidgetStore.mapChildId(context) }.coerceAtLeast(0)
        WidgetStore.setMapChildId(context, children[(current + 1) % children.size].id)
        ChildMapWidget().updateAll(context)
    }
}

/** Approve / Not now on a request, straight from the widget. */
class RespondAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val childId = parameters[CHILD] ?: return
        val requestId = parameters[REQUEST] ?: return
        val approved = parameters[APPROVED] ?: return
        // Hide it at once; a failed answer comes back with the next refresh.
        WidgetStore.removeRequest(context, requestId)
        RequestsWidget().updateAll(context)
        val result = withTimeoutOrNull(8_000L) {
            WidgetRefresher.deps(context).requests().respond(childId, requestId, approved)
        }
        Diag.action("widget.respond", "approved=$approved ok=${result is com.shahaabapps.core.common.AppResult.Success}")
        if (result !is com.shahaabapps.core.common.AppResult.Success) WidgetRefreshWorker.refreshNow(context)
    }

    companion object {
        val CHILD = ActionParameters.Key<String>("childId")
        val REQUEST = ActionParameters.Key<String>("requestId")
        val APPROVED = ActionParameters.Key<Boolean>("approved")
    }
}

// ---------------------------------------------------------------- receivers

abstract class WatchDogWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!Widgets.anyPlaced(context)) WidgetRefreshWorker.cancel(context)
    }
}

class ChildMapWidgetReceiver : WatchDogWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChildMapWidget()
}

class RecentAppsWidgetReceiver : WatchDogWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentAppsWidget()
}

class RequestsWidgetReceiver : WatchDogWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RequestsWidget()
}

object Widgets {
    private val receivers = listOf(
        ChildMapWidgetReceiver::class.java,
        RecentAppsWidgetReceiver::class.java,
        RequestsWidgetReceiver::class.java,
    )

    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return receivers.any { runCatching { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }.getOrDefault(false) }
    }

    suspend fun updateAll(context: Context) {
        runCatching { ChildMapWidget().updateAll(context) }
        runCatching { RecentAppsWidget().updateAll(context) }
        runCatching { RequestsWidget().updateAll(context) }
    }
}
