package com.shahaabapps.shield.feature.rules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.FocusWindow
import com.shahaabapps.core.model.GeofenceTrigger
import com.shahaabapps.core.model.MultiWindowBlock
import com.shahaabapps.core.model.TrackingMode
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.shield.feature.kid.MyApp
import com.shahaabapps.shield.feature.kid.MyAppsViewModel

/**
 * Every rule the parent set in Watch Dog, laid out like Watch Dog's own controls,
 * but read-only: switches are locked and choices only show a tick.
 */
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
    appsViewModel: MyAppsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val s by viewModel.state.collectAsStateWithLifecycle()
    val apps by appsViewModel.state.collectAsStateWithLifecycle()
    RulesContent(s, apps, onBack)
}

/** The Rules screen for a given state (no view models), so it can be previewed. */
@Composable
fun RulesContent(s: RulesState, apps: com.shahaabapps.shield.feature.kid.MyAppsState, onBack: () -> Unit) {
    fun label(pkg: String) = s.appLabels[pkg] ?: pkg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        GuardianHeader(title = "Rules", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = AppTheme.colors.secondaryLabel, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "These are the rules your parent set in Watch Dog. Only they can change them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            if (s.phones > 1) {
                Note(
                    if (s.phones == 2) "They're shared with your other phone. Time limits count on each phone."
                    else "They're shared with your other ${s.phones - 1} phones. Time limits count on each phone.",
                )
            }

            // ---- Downtime ------------------------------------------------------
            Section("Bedtime & School")
            CupertinoCard {
                FocusRow("Bedtime", Icons.Filled.Bedtime, s.focus.bedtime, ::label, divider = true)
                FocusRow("School", Icons.Filled.School, s.focus.school, ::label, divider = false)
            }
            s.timeZone?.takeIf { it.isNotBlank() }?.let { zone ->
                Note("Times follow ${zone.replace('_', ' ')}.")
            }

            // ---- Apps ------------------------------------------------------------
            Section("Blocked apps")
            AppList(apps.blocked, "No apps are blocked.")
            Section("Time limits and hours")
            AppList(apps.restricted, "No apps have a time limit or set hours.")
            Section("Supervised apps")
            AppList(apps.supervised, "No apps are supervised.")
            if (apps.supervised.isNotEmpty()) {
                Note("These work, but your parent sees screenshots of them, taken at random moments while you use them.")
            }

            // ---- New apps ----------------------------------------------------------
            Section("New apps")
            val newApps = when {
                !s.protection.blockNewApps -> 2
                s.protection.newAppsBlocked -> 0
                else -> 1
            }
            CupertinoCard {
                Choice("Block until reviewed", "Newly installed apps stay blocked until your parent allows them", newApps == 0, divider = true)
                Choice("Supervise until reviewed", "They work straight away, with a screenshot every minute, until your parent reviews them", newApps == 1, divider = true)
                Choice("Allow", "They work straight away with no review", newApps == 2, divider = false)
            }

            // ---- Pop-up and split screen ------------------------------------------
            Section("Pop-up and split-screen view")
            val mw = s.protection.multiWindowBlock
            CupertinoCard {
                Choice("Off", "Apps can open in a pop-up, split screen or picture-in-picture.", mw == MultiWindowBlock.OFF, divider = true)
                Choice("During Bedtime & School", "Only during focus times, any app opened that way is covered and closed.", mw == MultiWindowBlock.FOCUS, divider = true)
                Choice("Always", "Any app opened that way is always covered and closed. Calls still work.", mw == MultiWindowBlock.ALWAYS, divider = false)
            }

            // ---- Protection ----------------------------------------------------------
            Section("Protection")
            CupertinoCard {
                Locked("Prevent uninstall", "Shield can't be removed without your parent's PIN", s.protection.uninstallBlocked, divider = true)
                Locked(
                    "Lock Shield's settings",
                    "Keeps the phone's Settings pages that switch Shield off (accessibility, usage access, app info) closed",
                    s.protection.lockSettings,
                    divider = false,
                )
            }

            // ---- App installs ----------------------------------------------------------
            Section("App installs")
            val guard = s.installGuard
            CupertinoCard {
                Locked("Control sideloaded installs", "Apps installed or updated from outside the Play Store are checked", guard.enabled, divider = guard.enabled)
                if (guard.enabled) {
                    Locked("Allow only picked apps", "Only the apps your parent picked can be sideloaded", guard.allowlist, divider = false)
                }
            }
            if (guard.enabled) {
                Section(if (guard.allowlist) "Allowed to install" else "Blocked from installing")
                if (guard.packages.isEmpty()) {
                    Note(if (guard.allowlist) "None yet, so no sideloaded app can be installed." else "None yet.")
                } else {
                    CupertinoCard {
                        val pkgs = guard.packages.sortedBy { label(it).lowercase() }
                        pkgs.forEachIndexed { i, pkg ->
                            CupertinoListRow(
                                title = label(pkg),
                                subtitle = pkg.takeIf { s.appLabels[it] != null },
                                showDivider = i < pkgs.lastIndex,
                                trailing = { AppIcon(base64 = s.appIcons[pkg], label = label(pkg), size = 32.dp) },
                            )
                        }
                    }
                }
            }

            // ---- Places ----------------------------------------------------------------
            Section("Places")
            if (s.places.isEmpty()) {
                Note("Your parent hasn't saved any places.")
            } else {
                CupertinoCard {
                    s.places.forEachIndexed { i, place ->
                        CupertinoListRow(
                            title = place.name.ifBlank { "Place" },
                            subtitle = placeDetail(place.active, place.trigger),
                            leadingIcon = placeIcon(place.icon),
                            showDivider = i < s.places.lastIndex,
                        )
                    }
                }
            }

            // ---- Location ----------------------------------------------------------------
            Section("Location accuracy")
            CupertinoCard {
                Choice("Saver", "Lowest battery use. The path is less precise, especially while travelling.", s.tracking == TrackingMode.SAVER, divider = true)
                Choice("Balanced", "Precise while moving, light on battery when still.", s.tracking == TrackingMode.ADAPTIVE, divider = true)
                Choice("High", "Most precise everywhere. Uses the most battery.", s.tracking == TrackingMode.HIGH, divider = false)
            }

            // ---- Alerts ------------------------------------------------------------------
            Section("Alerts your parent gets")
            val a = s.alerts
            CupertinoCard {
                Locked("Protection turned off", "App blocking, screen-time tracking or uninstall protection was switched off", a.protectionOff, divider = true)
                Locked("Date or time changed", "The phone's clock was set by hand", a.clockChanged, divider = true)
                Locked("Fake GPS", "A fake-location app is feeding made-up positions", a.fakeGps, divider = true)
                Locked("Travelling", "The phone has been on another time zone for 2 hours", a.travel, divider = true)
                Locked("Not checking in", "No contact with the phone for 3 hours, and when it's back", a.noCheckIn, divider = true)
                Locked("Blocked app attempts", "When you try to open a blocked app", a.blockedApps, divider = false)
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    SectionHeader(text = text, modifier = Modifier.padding(start = Spacing.md))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
}

/** A switch the child can see but not move. */
@Composable
private fun Locked(title: String, subtitle: String, on: Boolean, divider: Boolean) {
    CupertinoListRow(
        title = title,
        subtitle = subtitle,
        showDivider = divider,
        modifier = Modifier.semantics { contentDescription = "$title: ${if (on) "on" else "off"}, set by your parent" },
        trailing = { LockedSwitch(on) },
    )
}

/**
 * The switch in its normal colours (so "on" reads as on), with every touch
 * swallowed so the child can't move it.
 */
@Composable
private fun LockedSwitch(on: Boolean) {
    androidx.compose.foundation.layout.Box {
        CupertinoSwitch(checked = on, onCheckedChange = {})
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        )
    }
}

/** One option of a parent's choice; the chosen one has a tick. */
@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, divider: Boolean) {
    CupertinoListRow(
        title = title,
        subtitle = subtitle,
        showDivider = divider,
        trailing = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Chosen by your parent", tint = AppTheme.colors.accent)
        },
    )
}

@Composable
private fun FocusRow(name: String, icon: ImageVector, w: FocusWindow, label: (String) -> String, divider: Boolean) {
    val subtitle = if (!w.enabled) {
        "Off"
    } else {
        buildString {
            append("${clock(w.startMinute)} – ${clock(w.endMinute)} · ${daysLabel(w.days)}")
            append("\nApps pause")
            if (w.allowedPackages.isNotEmpty()) {
                append(", except ")
                append(w.allowedPackages.map(label).sortedBy { it.lowercase() }.joinToString(", "))
            }
        }
    }
    CupertinoListRow(
        title = name,
        subtitle = subtitle,
        leadingIcon = icon,
        showDivider = divider,
        modifier = Modifier.semantics { contentDescription = "$name: $subtitle" },
        trailing = { LockedSwitch(w.enabled) },
    )
}

@Composable
private fun AppList(apps: List<MyApp>, empty: String) {
    if (apps.isEmpty()) {
        Note(empty)
        return
    }
    CupertinoCard {
        apps.forEachIndexed { i, app ->
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    AppIcon(base64 = app.iconBase64, label = app.label, size = 36.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(app.detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                    }
                }
                if (i < apps.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        color = AppTheme.colors.separator,
                        modifier = Modifier.padding(start = Spacing.md + 36.dp + Spacing.md),
                    )
                }
            }
        }
    }
}

private fun placeDetail(active: Boolean, trigger: GeofenceTrigger): String = when {
    !active -> "Alerts off"
    trigger == GeofenceTrigger.ENTER -> "Your parent is told when you arrive"
    trigger == GeofenceTrigger.EXIT -> "Your parent is told when you leave"
    else -> "Your parent is told when you arrive and leave"
}

private fun placeIcon(key: String?): ImageVector = when (key) {
    "home" -> Icons.Filled.Home
    "school" -> Icons.Filled.School
    "work" -> Icons.Filled.Work
    "mosque" -> Icons.Filled.Mosque
    "gym" -> Icons.Filled.FitnessCenter
    "park" -> Icons.Filled.Park
    "shop" -> Icons.Filled.ShoppingBag
    "food" -> Icons.Filled.Restaurant
    "hospital" -> Icons.Filled.LocalHospital
    "airport" -> Icons.Filled.LocalAirport
    "metro" -> Icons.Filled.DirectionsSubway
    "friends" -> Icons.Filled.Groups
    else -> Icons.Filled.Place
}

private fun clock(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60) % 24
    val m = minuteOfDay % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

private fun daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "No days"
    else -> {
        val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        days.sorted().joinToString(", ") { names.getOrElse(it) { "" } }
    }
}
