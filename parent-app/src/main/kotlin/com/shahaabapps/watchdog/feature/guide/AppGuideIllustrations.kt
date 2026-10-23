package com.shahaabapps.watchdog.feature.guide

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.components.Avatar
import com.shahaabapps.core.ui.components.rememberReducedMotion
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.LighthouseColors
import com.shahaabapps.watchdog.R
import kotlin.math.cos
import kotlin.math.sin

// Illustrations always sit on the navy chapter header, in light and dark mode, so
// they use the fixed Lighthouse colours plus the status hues of the light theme.
private val Gold = LighthouseColors.Gold
private val NavyDeep = LighthouseColors.NavyDeep
private val Navy = LighthouseColors.Navy
private val NavyLight = LighthouseColors.NavyLight
private val NavyMid = LighthouseColors.NavyMid
private val Outline = LighthouseColors.Outline
private val Subtitle = LighthouseColors.Subtitle
private val Night = LighthouseColors.Night
private val Lilac = LighthouseColors.Lilac
private val Success = Color(0xFF1E8E5A)
private val SuccessOnNavy = Color(0xFF7FD1A3)
private val Warn = Color(0xFFD9622B)
private val Destructive = Color(0xFFC1362B)
private val StrengthAmber = Color(0xFFB7860B)
private val LogAmber = Color(0xFFE5A50A)
private val Grey = Color(0xFF8A919E)
private val Ink = Color(0xFF1A1C1E)
private val Muted = Color(0xFF4A5878)
private val FieldLine = Color(0xFFD4DDF0)
private val FieldFill = Color(0xFFF7F9FD)

/** Home's per-app usage colours (USAGE_PALETTE). */
private val UsagePalette = listOf(Navy, Gold, NavyMid, NavyLight, Grey)

/**
 * The 180dp hero drawing for a chapter: Compose shapes and Material icons on the
 * hero gradient. Each one runs a single gentle loop, and holds its final frame
 * when the system has animations turned off. Read out as [contentDescription].
 */
@Composable
internal fun GuideIllustration(
    chapterId: String,
    contentDescription: String,
    childName: String?,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val name = childName?.trim()?.ifBlank { null } ?: "Sara"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.colors.heroBrush, alpha = 0.6f)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when (chapterId) {
            "welcome" -> WelcomeArt(reduced)
            "account" -> AccountArt(reduced)
            "add_child" -> AddChildArt(reduced)
            "child_phone" -> ChildPhoneArt(reduced)
            "home" -> HomeArt(reduced, name)
            "quick_actions" -> QuickActionsArt(reduced)
            "phone_health" -> PhoneHealthArt(reduced)
            "screen_capture" -> ScreenCaptureArt(reduced)
            "map_places" -> MapPlacesArt(reduced)
            "today_history" -> TodayHistoryArt(reduced)
            "activity" -> ActivityArt(reduced)
            "app_rules" -> AppRulesArt(reduced)
            "requests" -> RequestsArt(reduced, name)
            "focus_modes" -> FocusModesArt(reduced)
            "protection" -> ProtectionArt(reduced)
            "app_installs" -> AppInstallsArt(reduced)
            "alerts" -> AlertsArt(reduced, name)
            "manage_children" -> ManageChildrenArt(reduced, name)
            "settings" -> SettingsArt(reduced)
            "security" -> SecurityArt(reduced)
            "diagnostics" -> DiagnosticsArt(reduced)
            "child_view" -> ChildViewArt(reduced)
            else -> TroubleshootingArt(reduced)
        }
    }
}

/**
 * A 0→1 progress that loops forever, or a constant [hold] (the final frame) under
 * reduced motion. Returned as State so callers read it in the draw/layer phase.
 */
@Composable
private fun loop(
    reduced: Boolean,
    durationMillis: Int,
    hold: Float = 1f,
    reverse: Boolean = false,
    easing: Easing = LinearEasing,
): State<Float> {
    if (reduced) return rememberUpdatedState(hold)
    val transition = rememberInfiniteTransition(label = "guideLoop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, easing = easing),
            if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "guideLoopProgress",
    )
}

// ---- Shared pieces ------------------------------------------------------------

/** A rounded phone outline (16dp corners, 1.5dp light stroke) with a small speaker slot. */
@Composable
private fun PhoneOutline(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Subtitle, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(16.dp, 3.dp)
                .clip(CircleShape)
                .background(Subtitle.copy(alpha = 0.6f)),
        )
        content()
    }
}

private fun shieldPath(w: Float, h: Float, x: Float = 0f, y: Float = 0f) = Path().apply {
    moveTo(x + w / 2f, y)
    lineTo(x + w, y + h * 0.16f)
    lineTo(x + w, y + h * 0.48f)
    cubicTo(x + w, y + h * 0.76f, x + w * 0.74f, y + h * 0.92f, x + w / 2f, y + h)
    cubicTo(x + w * 0.26f, y + h * 0.92f, x, y + h * 0.76f, x, y + h * 0.48f)
    lineTo(x, y + h * 0.16f)
    close()
}

/** Shield's mark, as the child app draws it: a navy shield with a gold rim and tick. */
@Composable
private fun ShieldMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rim = size.width * 0.08f
        val w = size.width - rim
        val h = size.height - rim
        val path = shieldPath(w, h, rim / 2f, rim / 2f)
        drawPath(path, Navy)
        drawPath(path, Gold, style = Stroke(width = rim))
        val tick = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.5f)
            lineTo(size.width * 0.45f, size.height * 0.64f)
            lineTo(size.width * 0.71f, size.height * 0.36f)
        }
        drawPath(tick, Gold, style = Stroke(width = size.width * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A white illustration card (22dp corners), the stand-in for a Watch Dog card. */
@Composable
private fun WhiteCard(width: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ---- Chapters -------------------------------------------------------------------

/** Watch Dog and Shield phones linked by an encrypted (locked) gold arc, lighthouse between. */
@Composable
private fun WelcomeArt(reduced: Boolean) {
    val dash = loop(reduced, 1400, hold = 0f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f + 12.dp.toPx()
            val dx = 85.dp.toPx()
            val arc = Path().apply {
                moveTo(cx - dx, cy - 52.dp.toPx())
                quadraticTo(cx, cy - 110.dp.toPx(), cx + dx, cy - 52.dp.toPx())
            }
            val on = 6.dp.toPx()
            val off = 5.dp.toPx()
            drawPath(
                arc,
                Gold,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), phase = -dash.value * (on + off)),
                ),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).offset(y = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhoneOutline(58.dp, 96.dp) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Gold),
                ) {
                    Text("W", color = NavyDeep, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Image(
                painter = painterResource(R.drawable.ic_lighthouse),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp),
            )
            PhoneOutline(58.dp, 96.dp) { ShieldMark(Modifier.size(28.dp, 32.dp)) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 10.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(NavyDeep)
                .border(1.5.dp, Gold, CircleShape),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Gold, modifier = Modifier.size(12.dp))
        }
    }
}

/** Sign-up card: two fields, the 4-step strength meter filling, and six OTP boxes. */
@Composable
private fun AccountArt(reduced: Boolean) {
    val p = loop(reduced, 2400)
    val filled by remember(p) { derivedStateOf { (p.value * 4f).toInt().coerceIn(1, 4) } }
    val strength = listOf(Destructive, Warn, StrengthAmber, Success)
    WhiteCard(width = 214.dp) {
        repeat(2) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 0) 1f else 0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FieldFill)
                    .border(1.dp, FieldLine, RoundedCornerShape(7.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            strength.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (i < filled) c else FieldLine),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier
                        .size(24.dp, 28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (i == 0) Gold else FieldFill)
                        .border(1.dp, if (i == 0) Gold else FieldLine, RoundedCornerShape(7.dp)),
                )
            }
        }
    }
}

/** The Pair screen's QR viewport with a gold line sweeping, beside Shield's phone. */
@Composable
private fun AddChildArt(reduced: Boolean) {
    val p = loop(reduced, 1800, reverse = true, easing = FastOutSlowInEasing)
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(NavyDeep)
                .border(1.dp, Outline, RoundedCornerShape(28.dp)),
        ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(64.dp))
            Box(Modifier.size(96.dp).border(4.dp, NavyLight, RoundedCornerShape(16.dp)))
            Box(Modifier.size(96.dp)) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp, start = 10.dp, end = 10.dp)
                        .graphicsLayer { translationY = p.value * 81.dp.toPx() }
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Gold),
                )
            }
        }
        PhoneOutline(64.dp, 108.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldMark(Modifier.size(30.dp, 34.dp))
                Spacer(Modifier.height(6.dp))
                Text("Shield", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Shield's SET UP checklist, each row turning into a green "On" chip in turn. */
@Composable
private fun ChildPhoneArt(reduced: Boolean) {
    val p = loop(reduced, 4000)
    val done by remember(p) { derivedStateOf { (p.value * 5f).toInt() } }
    val rows = listOf("Usage access", "Accessibility", "Location", "Battery")
    WhiteCard(width = 232.dp) {
        rows.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (reduced || i < done) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape).background(Success).padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(" On", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Enable",
                        color = Navy,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, Navy, CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** A mini Home hero: map grid, pulsing child dot, name pill and the bell with its dot. */
@Composable
private fun HomeArt(reduced: Boolean, name: String) {
    val p = loop(reduced, 1600, hold = 0f)
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 36.dp, bottomEnd = 36.dp)
    Box(
        Modifier
            .size(260.dp, 150.dp)
            .clip(shape)
            .background(Color(0xFFE8EEF8)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color(0xFFCBD6EC), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0xFFCBD6EC), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
            drawLine(Color.White, Offset(0f, size.height * 0.8f), Offset(size.width, size.height * 0.3f), 7.dp.toPx())
            drawLine(Color.White, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.55f, size.height), 6.dp.toPx())
            val c = Offset(size.width * 0.5f, size.height * 0.56f)
            drawCircle(Navy.copy(alpha = 0.45f * (1f - p.value)), radius = 8.dp.toPx() + p.value * 20.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
            drawCircle(Navy, radius = 6.dp.toPx(), center = c)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, FieldLine, CircleShape)
                .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).clip(CircleShape).background(Navy.copy(alpha = 0.16f)),
            ) {
                Text(name.first().uppercaseChar().toString(), color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                " $name ▾",
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MaxNameWidth),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Navy, modifier = Modifier.size(16.dp))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Warn),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    ),
                ),
        )
        Text(
            "At Home",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 10.dp),
        )
    }
}

/** Keeps a long child name from pushing the bell off the mini hero. */
private val MaxNameWidth = 96.dp

/** The four quick-action tiles; Ring sends out two expanding gold waves. */
@Composable
private fun QuickActionsArt(reduced: Boolean) {
    val p = loop(reduced, 1800, hold = 0.35f)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                repeat(2) { k ->
                    val q = (p.value + k * 0.5f) % 1f
                    drawCircle(
                        Gold.copy(alpha = (1f - q) * 0.6f),
                        radius = size.minDimension / 2f + q * 22.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            QuickTile("Ring", Icons.Filled.NotificationsActive, primary = true)
        }
        QuickTile("Locate", Icons.Filled.MyLocation)
        QuickTile("Refresh", Icons.Filled.Refresh)
        QuickTile("Focus", Icons.Filled.Bedtime)
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(shape)
            .background(if (primary) Navy else Color.White)
            .border(1.dp, if (primary) Outline else FieldLine, shape),
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) Gold else Navy, modifier = Modifier.size(22.dp))
        Text(label, color = if (primary) Color.White else Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Three StatusChip-style health pills; the location dot breathes. */
@Composable
private fun PhoneHealthArt(reduced: Boolean) {
    val p = loop(reduced, 1400, reverse = true, easing = FastOutSlowInEasing)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HealthPill(Icons.Filled.CheckCircle, SuccessOnNavy, "Location", "Updating") { p.value }
        HealthPill(Icons.Filled.BatteryStd, Gold, "Battery", "82%")
        HealthPill(Icons.Filled.Thermostat, NavyLight, "Temperature", "Normal")
    }
}

@Composable
private fun HealthPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    value: String,
    dotAlpha: () -> Float = { 1f },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(236.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = 0.35f + 0.65f * dotAlpha() }
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Three tilted screenshots; the front one flashes like a fresh capture. */
@Composable
private fun ScreenCaptureArt(reduced: Boolean) {
    val p = loop(reduced, 2200)
    Box(Modifier.size(220.dp, 160.dp), contentAlignment = Alignment.Center) {
        listOf(Triple(-6f, -30.dp, 0.45f), Triple(6f, 30.dp, 0.65f)).forEach { (angle, dx, a) ->
            Box(
                Modifier
                    .offset(x = dx)
                    .graphicsLayer { rotationZ = angle }
                    .size(78.dp, 136.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = a)),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(78.dp, 136.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(6.dp)).background(NavyLight.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(CircleShape).background(FieldLine))
                Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(CircleShape).background(FieldLine))
            }
            Icon(Icons.Filled.Screenshot, contentDescription = null, tint = Navy, modifier = Modifier.padding(top = 34.dp).size(26.dp))
            // Capture flash: bright for the first sixth of the loop, then gone.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val v = p.value
                        alpha = if (v < 0.16f) (1f - v / 0.16f) * 0.9f else 0f
                    }
                    .background(Color.White),
            )
        }
        Text(
            "just now",
            color = NavyDeep,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 34.dp, bottom = 6.dp)
                .clip(CircleShape)
                .background(Gold)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/** A circular geofence around a School pin, and a drawn 4-point area. */
@Composable
private fun MapPlacesArt(reduced: Boolean) {
    val p = loop(reduced, 2000, hold = 0f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val step = 26.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
            val c = Offset(size.width * 0.32f, size.height * 0.5f)
            val r = 50.dp.toPx()
            drawCircle(NavyLight.copy(alpha = 0.2f), radius = r, center = c)
            drawCircle(NavyLight, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(
                NavyLight.copy(alpha = 0.5f * (1f - p.value)),
                radius = r * (1f + 0.3f * p.value),
                center = c,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            val pts = listOf(
                Offset(size.width * 0.62f, size.height * 0.28f),
                Offset(size.width * 0.88f, size.height * 0.22f),
                Offset(size.width * 0.9f, size.height * 0.72f),
                Offset(size.width * 0.66f, size.height * 0.8f),
            )
            val poly = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(poly, Gold.copy(alpha = 0.12f))
            drawPath(poly, Gold, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            pts.forEach {
                drawCircle(Color.White, radius = 4.5.dp.toPx(), center = it)
                drawCircle(Gold, radius = 4.5.dp.toPx(), center = it, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = w * 0.32f - 16.dp, y = h * 0.5f - 16.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Gold),
        ) {
            Icon(Icons.Filled.School, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(18.dp))
        }
    }
}

/** A day's route drawing itself in green, numbered stops, a dashed gap and a playback bar. */
@Composable
private fun TodayHistoryArt(reduced: Boolean) {
    val p = loop(reduced, 3200, easing = FastOutSlowInEasing)
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        val w = size.width
        val trackY = size.height - 24.dp.toPx()
        val top = 18.dp.toPx()
        val hh = trackY - 24.dp.toPx() - top
        fun pt(fx: Float, fy: Float) = Offset(w * fx, top + hh * fy)
        val s0 = pt(0.07f, 0.8f)
        val s1 = pt(0.3f, 0.15f)
        val s2 = pt(0.52f, 0.72f)
        val g = pt(0.66f, 0.42f)
        val s3 = pt(0.9f, 0.18f)
        val travelled = Path().apply {
            moveTo(s0.x, s0.y)
            cubicTo(w * 0.12f, top + hh * 0.2f, w * 0.2f, top, s1.x, s1.y)
            cubicTo(w * 0.38f, top + hh * 0.3f, w * 0.42f, top + hh * 0.9f, s2.x, s2.y)
        }
        val ahead = Path().apply {
            moveTo(g.x, g.y)
            cubicTo(w * 0.74f, top + hh * 0.2f, w * 0.8f, top + hh * 0.5f, s3.x, s3.y)
        }
        val line = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(travelled, NavyLight, style = line)
        drawPath(ahead, NavyLight, style = line)
        drawLine(
            Grey,
            s2,
            g,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )
        val measure = PathMeasure().apply { setPath(travelled, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * p.value, drawn, true)
        drawPath(drawn, Success, style = line)
        drawCircle(Color.White, radius = 5.dp.toPx(), center = s0)
        listOf(s1, s2, s3).forEachIndexed { i, s ->
            drawCircle(Gold, radius = 11.dp.toPx(), center = s)
            drawCircle(NavyDeep, radius = 11.dp.toPx(), center = s, style = Stroke(width = 1.5.dp.toPx()))
            val label = measurer.measure("${i + 1}", TextStyle(color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            drawText(label, topLeft = Offset(s.x - label.size.width / 2f, s.y - label.size.height / 2f))
        }
        // Playback bar with white stop ticks.
        val left = w * 0.04f
        val right = w * 0.96f
        val barH = 6.dp.toPx()
        drawRoundRect(
            Color.White.copy(alpha = 0.22f),
            topLeft = Offset(left, trackY - barH / 2f),
            size = Size(right - left, barH),
            cornerRadius = CornerRadius(barH / 2f),
        )
        val progressX = left + (right - left) * 0.55f * p.value
        drawRoundRect(
            Gold,
            topLeft = Offset(left, trackY - barH / 2f),
            size = Size(progressX - left, barH),
            cornerRadius = CornerRadius(barH / 2f),
        )
        listOf(0.22f, 0.5f, 0.82f).forEach { f ->
            val tx = left + (right - left) * f
            drawLine(Color.White, Offset(tx, trackY - 6.dp.toPx()), Offset(tx, trackY + 6.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(progressX, trackY))
    }
}

/** Home's screen-time card: five usage bars in the usage palette, growing in. */
@Composable
private fun ActivityArt(reduced: Boolean) {
    val p = loop(reduced, 2600, easing = FastOutSlowInEasing)
    val fractions = listOf(0.92f, 0.7f, 0.5f, 0.34f, 0.18f)
    WhiteCard(width = 240.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Screen time today", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("3h 12m", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fractions.forEachIndexed { i, f ->
                val color = UsagePalette[i % UsagePalette.size]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(FieldLine.copy(alpha = 0.6f))) {
                        Box(
                            Modifier
                                .fillMaxWidth(f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = p.value
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

/** A Timer in a gold circle with a ticking ring, above the daily-limit chips. */
@Composable
private fun AppRulesArt(reduced: Boolean) {
    val p = loop(reduced, 3000)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val inset = sw / 2f
                drawCircle(Color.White.copy(alpha = 0.14f), radius = size.minDimension / 2f - inset, style = Stroke(sw))
                drawArc(
                    Gold,
                    startAngle = -90f,
                    sweepAngle = 360f * p.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(58.dp).clip(CircleShape).background(Gold),
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(30.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("15m", "30m", "1h").forEach { label ->
                val selected = label == "1h"
                Text(
                    label,
                    color = if (selected) NavyDeep else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) NavyLight else Color.White.copy(alpha = 0.10f))
                        .border(1.dp, if (selected) NavyLight else Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** A child's request card with a gently pulsing gold Approve. */
@Composable
private fun RequestsArt(reduced: Boolean, name: String) {
    val p = loop(reduced, 1400, hold = 0f, reverse = true, easing = FastOutSlowInEasing)
    WhiteCard(width = 252.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Navy.copy(alpha = 0.12f)),
            ) {
                Text(name.first().uppercaseChar().toString(), color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("$name is asking…", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("15 more minutes · YouTube", color = Muted, fontSize = 11.sp, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .graphicsLayer {
                        val s = 1f + 0.05f * p.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Gold),
            ) {
                Text("Approve", color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, FieldLine, CircleShape),
            ) {
                Text("Not now", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The Bedtime banner: a lilac moon in a 24h ring, gold from 9 PM to 7 AM. */
@Composable
private fun FocusModesArt(reduced: Boolean) {
    val p = loop(reduced, 3000, easing = FastOutSlowInEasing)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Night)
            .padding(16.dp),
    ) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 9.dp.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                // 24h dial: midnight at the top, 15° per hour. 9 PM = 225°, 10 hours = 150°.
                val start = -90f + 21 * 15f
                drawArc(Gold, start, 150f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                val angle = Math.toRadians((start + 150f * p.value).toDouble())
                val r = (size.minDimension - sw) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat()),
                )
            }
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Lilac, modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bedtime", color = Lilac, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("9:00 PM – 7:00 AM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                "MTWTFSS".forEach { d ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Lilac),
                    ) {
                        Text(d.toString(), color = Night, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** A gold Lock inside a shield outline, beside three switches (the last one flips on). */
@Composable
private fun ProtectionArt(reduced: Boolean) {
    val p = loop(reduced, 2400, reverse = true, easing = FastOutSlowInEasing)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.size(104.dp, 120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 2.dp.toPx()
                val path = shieldPath(size.width - sw, size.height - sw, sw / 2f, sw / 2f)
                drawPath(path, Color.White.copy(alpha = 0.06f))
                drawPath(path, Subtitle, style = Stroke(width = sw))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.offset(y = (-4).dp).size(56.dp).clip(CircleShape).background(Gold),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(28.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp, 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(10.dp))
                    SwitchTrack { if (i == 2) p.value else 1f }
                }
            }
        }
    }
}

@Composable
private fun SwitchTrack(on: () -> Float) {
    Box(
        Modifier
            .size(44.dp, 26.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = on()
            drawRoundRect(
                lerp(Color.White.copy(alpha = 0.2f), NavyLight, t),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            val r = size.height / 2f - 3.dp.toPx()
            val x0 = size.height / 2f
            val x1 = size.width - size.height / 2f
            drawCircle(Color.White, radius = r, center = Offset(x0 + (x1 - x0) * t, size.height / 2f))
        }
    }
}

/** An install arrow dropping into a phone, beside a slashed red APK tile. */
@Composable
private fun AppInstallsArt(reduced: Boolean) {
    val p = loop(reduced, 1800, easing = FastOutSlowInEasing)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.size(84.dp, 156.dp)) {
            PhoneOutline(70.dp, 110.dp, Modifier.align(Alignment.BottomCenter))
            Icon(
                Icons.Filled.GetApp,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = p.value * 60.dp.toPx()
                        alpha = 0.4f + 0.6f * p.value
                    }
                    .size(40.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.5.dp, Destructive, RoundedCornerShape(16.dp)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Android, contentDescription = null, tint = NavyLight, modifier = Modifier.size(26.dp))
                Text("APK", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(Modifier.fillMaxSize()) {
                val m = 9.dp.toPx()
                drawLine(Destructive, Offset(m, m), Offset(size.width - m, size.height - m), 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** Three notification cards arriving in turn: arrival, request, protection. */
@Composable
private fun AlertsArt(reduced: Boolean, name: String) {
    val p = loop(reduced, 3600)
    val items = listOf(
        Triple(Success, "$name arrived at School", "now"),
        Triple(Gold, "$name is asking for more time", "2m"),
        Triple(Destructive, "Shield protection changed", "5m"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(256.dp)) {
        items.forEachIndexed { i, (dot, text, time) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = (p.value * 4f - i).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * -12.dp.toPx()
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(time, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

/** Two parents linked to one child, with People and restore (QR) badges. */
@Composable
private fun ManageChildrenArt(reduced: Boolean, name: String) {
    val p = loop(reduced, 1200, hold = 0f)
    Box(Modifier.size(250.dp, 150.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val child = Offset(190.dp.toPx(), 75.dp.toPx())
            val on = 6.dp.toPx()
            val off = 5.dp.toPx()
            listOf(Offset(40.dp.toPx(), 40.dp.toPx()), Offset(40.dp.toPx(), 110.dp.toPx())).forEach { parent ->
                drawLine(
                    Gold,
                    parent,
                    child,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), phase = -p.value * (on + off)),
                )
            }
        }
        Avatar(name = "Alex", photoUrl = null, size = 48.dp, modifier = Modifier.offset(16.dp, 16.dp))
        Avatar(name = "Jo", photoUrl = null, size = 48.dp, modifier = Modifier.offset(16.dp, 86.dp))
        Box(Modifier.offset(160.dp, 45.dp)) {
            Avatar(name = name, photoUrl = null, size = 60.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(6.dp, 6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Gold)
                    .border(2.dp, NavyDeep, CircleShape),
            ) {
                Icon(Icons.Filled.People, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(14.dp))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(8.dp, (-6).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, NavyDeep, CircleShape),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** The System / Light / Dark track (thumb stepping across) above three map swatches. */
@Composable
private fun SettingsArt(reduced: Boolean) {
    val p = loop(reduced, 4500, hold = 0f)
    val index by remember(p) { derivedStateOf { (p.value * 3f).toInt().coerceIn(0, 2) } }
    val thumbX by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = if (reduced) tween(0) else tween(320, easing = FastOutSlowInEasing),
        label = "guideSegment",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(240.dp, 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = thumbX * 78.dp.toPx() }
                    .size(78.dp, 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            )
            Row(Modifier.fillMaxSize()) {
                listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            color = if (i == index) NavyDeep else Subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MapSwatch("Standard", Color(0xFFE8EEF8), Color.White, Navy, selected = true)
            MapSwatch("Bright", Color(0xFFF6F1E4), Color(0xFFFFD98A), Navy)
            MapSwatch("Dark", Night, Outline, NavyLight)
        }
    }
}

@Composable
private fun MapSwatch(label: String, ground: Color, road: Color, pin: Color, selected: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .size(68.dp, 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, if (selected) Gold else Color.Transparent, RoundedCornerShape(12.dp)),
        ) {
            drawRect(ground)
            drawLine(road, Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.25f), 5.dp.toPx())
            drawLine(road, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.45f, size.height), 4.dp.toPx())
            drawCircle(pin, radius = 5.dp.toPx(), center = Offset(size.width * 0.62f, size.height * 0.48f))
        }
        Text(label, color = Subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A VerifiedUser glyph over six OTP boxes filling in, beside masked backup codes. */
@Composable
private fun SecurityArt(reduced: Boolean) {
    val p = loop(reduced, 3000)
    val filled by remember(p) { derivedStateOf { (p.value * 7f).toInt().coerceAtMost(6) } }
    val digits = "482915"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Gold, modifier = Modifier.size(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(6) { i ->
                    val on = reduced || i < filled
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp, 30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.5.dp, if (on) Gold else NavyLight.copy(alpha = 0.6f), RoundedCornerShape(7.dp)),
                    ) {
                        if (on) Text(digits[i].toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(5) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(2) {
                        Text(
                            "••••",
                            color = Subtitle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A six-line diagnostics log with red, amber and grey level dots, written line by line. */
@Composable
private fun DiagnosticsArt(reduced: Boolean) {
    val p = loop(reduced, 3000)
    val levels = listOf(Destructive, Grey, LogAmber, Grey, Grey, LogAmber)
    val widths = listOf(0.9f, 0.65f, 0.8f, 0.5f, 0.72f, 0.6f)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        levels.forEachIndexed { i, dot ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = (p.value * 7f - i).coerceIn(0f, 1f) },
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text("12:0${9 - i}", color = Subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(widths[i])
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/** Shield's block screen on the child's phone, with the gold "ask" pill. */
@Composable
private fun ChildViewArt(reduced: Boolean) {
    val p = loop(reduced, 1400, hold = 0f, reverse = true, easing = FastOutSlowInEasing)
    PhoneOutline(136.dp, 164.dp) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Night)
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.HourglassBottom, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
            Text("Time's up", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("for YouTube", color = Lilac, fontSize = 10.sp, maxLines = 1)
            Text(
                "Ask for 15 more minutes",
                color = NavyDeep,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        val s = 1f + 0.05f * p.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gold)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** A wrench over a location pin that crossfades from LocationOff to LocationOn. */
@Composable
private fun TroubleshootingArt(reduced: Boolean) {
    val p = loop(reduced, 2400, reverse = true, easing = FastOutSlowInEasing)
    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Icon(
            Icons.Filled.LocationOff,
            contentDescription = null,
            tint = Warn,
            modifier = Modifier.size(58.dp).graphicsLayer { alpha = 1f - p.value },
        )
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = SuccessOnNavy,
            modifier = Modifier.size(58.dp).graphicsLayer { alpha = p.value },
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(44.dp)
                .graphicsLayer { rotationZ = -20f + 40f * p.value }
                .clip(CircleShape)
                .background(Gold),
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(24.dp))
        }
    }
}
