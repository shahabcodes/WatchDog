package com.shahaabapps.watchdog.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.shahaabapps.watchdog.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.core.ui.components.Avatar
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.feature.alerts.AlertsScreen
import com.shahaabapps.watchdog.feature.dashboard.DashboardViewModel
import com.shahaabapps.watchdog.feature.dashboard.LinkState
import com.shahaabapps.watchdog.feature.dashboard.ReconnectState
import com.shahaabapps.watchdog.feature.dashboard.QrImageDecoder
import com.shahaabapps.watchdog.feature.geofence.GeofenceScreen
import com.shahaabapps.watchdog.feature.guide.AppGuideChapterScreen
import com.shahaabapps.watchdog.feature.guide.AppGuideIndexScreen
import com.shahaabapps.watchdog.feature.guide.AppGuidePrefs
import com.shahaabapps.watchdog.feature.overview.OverviewScreen
import com.shahaabapps.watchdog.feature.settings.SettingsScreen
import com.shahaabapps.watchdog.feature.usage.UsageScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "Home", Icons.Filled.GridView),
    MAP("map", "Map", Icons.Filled.Map),
    SCREENTIME("screentime", "Activity", Icons.Filled.BarChart),
}

@Composable
fun ParentShell(
    profile: UserProfile,
    deepLink: com.shahaabapps.watchdog.ui.RequestDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val children by viewModel.children.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()

    // After signing in with a 2FA backup code, 2FA is off: prompt once to set it up again.
    var showMfaOffNotice by rememberSaveable { mutableStateOf(viewModel.consumeBackupCodeSignInNotice()) }

    // Show a one-time "connected" screen after the first child is paired during
    // onboarding (we only arrived here from the empty/first-run state).
    var justConnected by rememberSaveable { mutableStateOf(false) }
    var sawEmpty by rememberSaveable { mutableStateOf(false) }
    // "Take the tour" on the connected screen opens the App guide once the shell is up.
    var pendingTour by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(children) { if (children.isEmpty()) sawEmpty = true }
    androidx.compose.runtime.LaunchedEffect(linkState) {
        if (linkState is LinkState.Success && sawEmpty) justConnected = true
    }

    // Ask for notification permission on first launch (Android 13+).
    val notifContext = androidx.compose.ui.platform.LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            notifContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The App guide's "seen" flag (it auto-opens once per phone).
    val guidePrefs = remember { AppGuidePrefs(notifContext) }

    if (loading && children.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingView(label = "Loading your family…")
        }
        return
    }

    if (children.isEmpty()) {
        com.shahaabapps.core.ui.theme.LighthouseType {
            AddChildScreen(profile = profile, linkState = linkState, viewModel = viewModel)
        }
        return
    }

    if (justConnected) {
        com.shahaabapps.core.ui.theme.LighthouseType {
            ConnectedChildScreen(
                childName = children.firstOrNull()?.profile?.name,
                // "Open the family map" declines the tour, so the guide doesn't auto-open.
                onContinue = {
                    guidePrefs.markSeen()
                    justConnected = false
                    sawEmpty = false
                    viewModel.clearLinkState()
                },
                onTour = {
                    pendingTour = true
                    justConnected = false
                    sawEmpty = false
                    viewModel.clearLinkState()
                },
            )
        }
        return
    }

    val initialChild = rememberSaveable { children.first().childId }
    val navController = rememberNavController()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    if (signingOut) {
        com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { },
            title = "Signing out…",
            dismissText = null,
            dismissOnOutsideTouch = false,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.shahaabapps.core.ui.components.AppLoader(size = 22.dp)
                Text("  Removing this phone from your account.")
            }
        }
    }
    // Diagnostics: which screen the parent is on (route only, no ids' contents).
    androidx.compose.runtime.LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            com.shahaabapps.core.common.Diag.info("screen", entry.destination.route ?: "?")
        }
    }
    val backEntry by navController.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val currentTab = route?.substringBefore("/") ?: Tab.OVERVIEW.route
    // Routes without a child (guide, settings, …) keep the child last picked on a tab.
    val routeChildId = backEntry?.arguments?.getString("childId")
    var lastChildId by rememberSaveable { mutableStateOf(initialChild) }
    androidx.compose.runtime.SideEffect { if (routeChildId != null) lastChildId = routeChildId }
    val currentChildId = routeChildId ?: lastChildId
    val showChrome = Tab.entries.any { it.route == currentTab }

    val selectedChild = children.firstOrNull { it.childId == currentChildId } ?: children.first()

    // If the selected child no longer exists (e.g. after a reconnect replaced its
    // id, or a remove), move the UI to a child that does — otherwise the screens
    // keep observing a deleted child and show "offline / no data".
    androidx.compose.runtime.LaunchedEffect(children, currentChildId, currentTab) {
        if (children.isNotEmpty() &&
            Tab.entries.any { it.route == currentTab } &&
            children.none { it.childId == currentChildId }
        ) {
            navController.navigate("$currentTab/${children.first().childId}") {
                popUpTo("${Tab.OVERVIEW.route}/{childId}") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // A request notification (tap or Approve/Reject) deep-links into that child's
    // Alerts, carrying the request id + chosen action so the confirmation dialog
    // opens on the right ask. Consumed once so it doesn't re-fire on recomposition.
    androidx.compose.runtime.LaunchedEffect(deepLink, children) {
        val link = deepLink ?: return@LaunchedEffect
        if (children.isEmpty()) return@LaunchedEffect
        if (children.none { it.childId == link.childId }) {
            onDeepLinkHandled()
            return@LaunchedEffect
        }
        navController.navigate(
            "alerts/${link.childId}?requestId=${link.requestId}&action=${link.action}",
        ) { launchSingleTop = true }
        onDeepLinkHandled()
    }

    // One consistent way to move between tab destinations, so the saved/restored
    // back stack stays coherent however the Map was opened (bottom bar or a card).
    val switchTab: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // The App guide opens by itself once per phone, the first time the family
    // dashboard shows (not over a notification deep link), or on "Take the tour".
    // It waits for the "Two-factor is off" notice, and only opens over Home.
    var guideAutoShown by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pendingTour, showMfaOffNotice) {
        val onHome = navController.currentDestination?.route?.startsWith(Tab.OVERVIEW.route) ?: true
        val firstTime = !guideAutoShown && !guidePrefs.seen && deepLink == null &&
            !showMfaOffNotice && onHome
        if (pendingTour || firstTime) {
            guideAutoShown = true
            pendingTour = false
            // Persist now, so a process kill while it's open doesn't re-open it next launch.
            guidePrefs.markSeen()
            navController.navigate("guide?tour=true") { launchSingleTop = true }
        }
    }
    // Leaves the guide from any of its pages, back to wherever it was opened from.
    val exitGuide: () -> Unit = {
        guidePrefs.markSeen()
        if (!navController.popBackStack("guide?tour={tour}", inclusive = true)) navController.popBackStack()
    }

    if (showMfaOffNotice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMfaOffNotice = false },
            title = { Text("Two-factor is off") },
            text = {
                Text(
                    "You signed in with a backup code, so two-factor authentication was turned off. " +
                        "Set it up again with your new authenticator to keep your account protected.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showMfaOffNotice = false
                    navController.navigate("security") { launchSingleTop = true }
                }) { Text("Set up now") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMfaOffNotice = false }) { Text("Later") }
            },
        )
    }

    Scaffold(
        containerColor = AppTheme.colors.groupedBackground,
        // No top bar: each screen owns its top (status-bar) inset. Zeroing the
        // content insets keeps the Map full-bleed behind the status bar and
        // avoids double-padding the screens that add their own header insets.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showChrome) {
                ParentBottomBar(currentTab = currentTab) { tab ->
                    switchTab("${tab.route}/$currentChildId")
                }
            }
        },
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        NavHost(
            navController = navController,
            startDestination = "${Tab.OVERVIEW.route}/$initialChild",
            modifier = Modifier.fillMaxSize(),
            // Instant tab switches — no crossfade that would overlap two heavy
            // screens (the Map tab's live map) mid-transition.
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
        ) {
            val childArg = listOf(navArgument("childId") { type = NavType.StringType })
            composable("overview/{childId}", childArg) {
                OverviewScreen(
                    onOpenMap = { switchTab("${Tab.MAP.route}/$currentChildId") },
                    onOpenAlerts = { navController.navigate("alerts/$currentChildId") { launchSingleTop = true } },
                    childNames = children.map { it.childId to it.profile.name.ifBlank { "Child" } },
                    selectedId = currentChildId,
                    onSelectChild = { id ->
                        navController.navigate("${Tab.OVERVIEW.route}/$id") { launchSingleTop = true }
                    },
                    onAddChild = { navController.navigate("add") },
                    onManage = { navController.navigate("manage") },
                    onOpenGuide = { navController.navigate("guide") { launchSingleTop = true } },
                    onSettings = { navController.navigate("settings") },
                    onSignOut = { viewModel.signOut() },
                    onVerifyConnection = { navController.navigate("verify/$currentChildId") },
                    onOpenInstallGuard = { navController.navigate("installguard/$currentChildId") },
                )
            }
            composable("installguard/{childId}", childArg) {
                com.shahaabapps.watchdog.feature.installguard.InstallGuardScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable("verify/{childId}", childArg) { entry ->
                com.shahaabapps.core.ui.theme.LighthouseType {
                    VerifyConnectionScreen(
                        childId = entry.arguments?.getString("childId").orEmpty(),
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("map/{childId}", childArg) { GeofenceScreen(onBack = { }, showBack = false) }
            composable(
                "alerts/{childId}?requestId={requestId}&action={action}",
                listOf(
                    navArgument("childId") { type = NavType.StringType },
                    navArgument("requestId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("action") { type = NavType.StringType; defaultValue = "VIEW" },
                ),
            ) {
                AlertsScreen(onBack = { navController.popBackStack() })
            }
            composable("screentime/{childId}", childArg) { UsageScreen(onBack = { }, embedded = true) }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSecurity = { navController.navigate("security") },
                    onOpenChangePassword = { navController.navigate("password") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenGuide = { navController.navigate("guide") { launchSingleTop = true } },
                )
            }
            // App guide: the index (with the navy cover first when ?tour=true), then
            // one chapter pager per "guide/<chapterId>". No bottom bar on either.
            composable(
                "guide?tour={tour}",
                listOf(navArgument("tour") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                AppGuideIndexScreen(
                    showCover = entry.arguments?.getBoolean("tour") == true,
                    onOpenChapter = { id -> navController.navigate("guide/$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "guide/{chapterId}",
                listOf(navArgument("chapterId") { type = NavType.StringType }),
            ) { entry ->
                AppGuideChapterScreen(
                    startChapterId = entry.arguments?.getString("chapterId").orEmpty(),
                    childName = selectedChild.profile.name,
                    onBack = { navController.popBackStack() },
                    onExit = exitGuide,
                )
            }
            composable("security") {
                com.shahaabapps.watchdog.feature.security.MfaScreen(onBack = { navController.popBackStack() })
            }
            composable("password") {
                com.shahaabapps.watchdog.feature.security.ChangePasswordScreen(onBack = { navController.popBackStack() })
            }
            composable("diagnostics") {
                com.shahaabapps.watchdog.feature.diagnostics.DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable("manage") {
                ManageChildrenScreen(
                    children = children,
                    viewModel = viewModel,
                    onRemove = { viewModel.removeChild(it) },
                    onAdd = { navController.navigate("add") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("add") {
                com.shahaabapps.core.ui.theme.LighthouseType {
                    AddChildScreen(profile = profile, linkState = linkState, viewModel = viewModel, onBack = { navController.popBackStack() })
                }
            }
        }
        val syncPending by viewModel.syncPending.collectAsStateWithLifecycle()
        val online by viewModel.online.collectAsStateWithLifecycle()
        SyncPill(
            pending = syncPending,
            online = online,
            childName = selectedChild.profile.name,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
        )
      }
    }
}

/**
 * Tells the parent when changes haven't reached the child's phone yet: right after
 * an offline save, "Saved – will reach Emma's phone when you're back online",
 * then a small "Waiting to sync"; "Syncing…" once back online, then "Synced".
 */
@Composable
private fun SyncPill(pending: Boolean, online: Boolean, childName: String, modifier: Modifier = Modifier) {
    var justSaved by remember { mutableStateOf(false) }
    var justSynced by remember { mutableStateOf(false) }
    var wasPending by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pending) {
        if (pending && !wasPending) {
            justSynced = false
            justSaved = true
            wasPending = true
            kotlinx.coroutines.delay(5_000)
            justSaved = false
        } else if (!pending && wasPending) {
            wasPending = false
            justSaved = false
            justSynced = true
            kotlinx.coroutines.delay(2_500)
            justSynced = false
        }
    }
    val who = childName.ifBlank { "your child" }
    val text = when {
        pending && justSaved && !online -> "Saved – will reach $who's phone when you're back online"
        pending && online -> "Syncing…"
        pending -> "Waiting to sync"
        justSynced -> "Synced"
        else -> null
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = text != null,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(50))
                .background(if (pending) Color(0xFF00133F) else AppTheme.colors.success)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            androidx.compose.material3.Icon(
                if (pending) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text ?: "",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ParentBottomBar(currentTab: String, onSelect: (Tab) -> Unit) {
    // Floating navy pill with a gold chip on the active tab (sign-in palette).
    val barColor = Color(0xFF00133F)
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = barColor,
        shadowElevation = 12.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
        ) {
            Tab.entries.forEach { tab ->
                val selected = currentTab == tab.route
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) Color(0xFFF5B83D) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = if (selected) 18.dp else 12.dp),
                ) {
                    Text(
                        tab.label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) Color(0xFF00133F) else Color(0xFFC7D3F0),
                    )
                }
            }
        }
    }
}

/** Lighthouse-styled "child connected" success screen after first pairing. */
@Composable
private fun ConnectedChildScreen(childName: String?, onContinue: () -> Unit, onTour: () -> Unit) {
    val navy = Color(0xFF00133F)
    val gold = Color(0xFFF5B83D)
    val subtitle = Color(0xFFC7D3F0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(navy)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lighthouse_scene),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(230.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ALL SET", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                "${childName?.ifBlank { null } ?: "Your child"} is connected",
                color = Color.White,
                fontFamily = AppFonts.lighthouse,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Shield is running on their phone. You can adjust their rules any time.",
                color = subtitle,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(gold)
                .clickable(onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Text("Open the family map", color = navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // Borderless secondary action, white on navy (the accent text button would vanish here).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onTour),
            contentAlignment = Alignment.Center,
        ) {
            Text("Take the tour", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddChildScreen(
    profile: UserProfile,
    linkState: LinkState,
    viewModel: DashboardViewModel,
    onBack: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCamera by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasCamera) cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contents = withContext(Dispatchers.IO) { QrImageDecoder.decodeFromUri(context, uri) }
                if (contents != null) viewModel.onQrScanned(contents) else viewModel.onImageDecodeFailed()
            }
        }
    }

    val navy = Color(0xFF00133F)
    val gold = Color(0xFFF5B83D)
    val subtitle = Color(0xFFC7D3F0)
    Column(modifier = Modifier.fillMaxSize().background(navy)) {
        // Navy header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.5.dp, Color(0xFF3A5AA8)), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pair your child's phone",
                    fontFamily = AppFonts.lighthouse,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    color = Color.White,
                )
                Text(
                    "On the child's phone, open Shield and show its code. Point your camera at it.",
                    fontSize = 14.sp,
                    color = subtitle,
                )
            }
        }
        // White sheet.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (linkState is LinkState.AwaitingApproval) {
                // Code accepted; nothing is linked until the child's phone approves.
                Text(
                    "Almost done",
                    color = navy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "On your child's phone, Shield is asking to connect your account. Tap Approve there.",
                    color = Color(0xFF5C4A1E),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                com.shahaabapps.core.ui.components.AppLoader(size = 28.dp, color = navy, strokeWidth = 3.dp)
                Text(
                    "Cancel",
                    color = navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.clearLinkState() }.padding(8.dp),
                )
                return@Column
            }
            if (linkState is LinkState.Error) {
                Text(linkState.message, color = Color(0xFFC1362B), textAlign = TextAlign.Center)
            }
            QrScanner(
                hasCamera = hasCamera,
                onRequestPermission = { cameraPermLauncher.launch(android.Manifest.permission.CAMERA) },
                onDecoded = { viewModel.onQrScanned(it) },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(gold)
                    .clickable {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Scan QR from gallery", color = navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Guided re-scan to verify an already-linked child's E2EE key out-of-band: scan
 * the QR shown on the child's phone to pin its key, without re-linking. Pops back
 * on success.
 */
@Composable
private fun VerifyConnectionScreen(
    childId: String,
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val verifyState by viewModel.verifyState.collectAsStateWithLifecycle()
    var hasCamera by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val contents = withContext(Dispatchers.IO) { QrImageDecoder.decodeFromUri(context, uri) }
                viewModel.verifyKeyFromQr(contents, expectedChildId = childId)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasCamera) cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
    }
    androidx.compose.runtime.LaunchedEffect(verifyState) {
        if (verifyState is com.shahaabapps.watchdog.feature.dashboard.VerifyState.Success) {
            viewModel.clearVerifyState()
            onBack()
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.clearVerifyState() } }

    val navy = Color(0xFF00133F)
    val subtitle = Color(0xFFC7D3F0)
    Column(modifier = Modifier.fillMaxSize().background(navy)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, Color(0xFF3A5AA8)), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Verify secure connection",
                    fontFamily = AppFonts.lighthouse,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    color = Color.White,
                )
                Text(
                    "On the child's phone, open Shield → Settings → \"Show secure code\", then scan it here.",
                    fontSize = 14.sp,
                    color = subtitle,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            (verifyState as? com.shahaabapps.watchdog.feature.dashboard.VerifyState.Error)?.let {
                Text(it.message, color = Color(0xFFC1362B), textAlign = TextAlign.Center)
            }
            QrScanner(
                hasCamera = hasCamera,
                onRequestPermission = { cameraPermLauncher.launch(android.Manifest.permission.CAMERA) },
                onDecoded = { viewModel.verifyKeyFromQr(it, expectedChildId = childId) },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5B83D))
                    .clickable {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Scan QR from gallery", color = navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // Recovery path: if encrypted data (location/history) won't load — e.g.
            // after this phone was reinstalled and its key rotated — re-request the
            // child to re-wrap the key, without needing a QR scan.
            val reconcileBusy by viewModel.reconcileBusy.collectAsStateWithLifecycle()
            Spacer(Modifier.height(4.dp))
            Text(
                "Location or history stuck on “unknown”?",
                color = AppTheme.colors.secondaryLabel,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.5.dp, Color(0xFF3A5AA8)), RoundedCornerShape(16.dp))
                    .clickable(enabled = !reconcileBusy) { viewModel.reconcileSecureLink(childId) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (reconcileBusy) "Reconnecting…" else "Reconcile secure link",
                    color = navy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (reconcileBusy) {
                Text(
                    "Asked the child device to re-share its key. If it's online, location should return in a moment.",
                    color = AppTheme.colors.secondaryLabel,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Live QR scanner inside a dark rounded viewport with a teal frame (prototype
 * Pair). The camera preview runs in-screen via an embedded barcode view, paused
 * and resumed with the composition lifecycle. Falls back to an "Enable camera"
 * prompt until the CAMERA permission is granted.
 */
@Composable
private fun QrScanner(
    hasCamera: Boolean,
    onRequestPermission: () -> Unit,
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(260.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF00133F)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasCamera) {
            val barcodeView = remember { mutableStateOf<com.journeyapps.barcodescanner.CompoundBarcodeView?>(null) }
            val decoded = remember { mutableStateOf(false) }
            val currentOnDecoded by androidx.compose.runtime.rememberUpdatedState(onDecoded)
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    com.journeyapps.barcodescanner.CompoundBarcodeView(ctx).apply {
                        setStatusText("")
                        this.barcodeView.decoderFactory =
                            com.journeyapps.barcodescanner.DefaultDecoderFactory(listOf(com.google.zxing.BarcodeFormat.QR_CODE))
                        decodeContinuous(object : com.journeyapps.barcodescanner.BarcodeCallback {
                            override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                                val text = result.text ?: return
                                if (!decoded.value) {
                                    decoded.value = true
                                    currentOnDecoded(text)
                                }
                            }
                        })
                        barcodeView.value = this
                    }
                },
            )
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> barcodeView.value?.resume()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> barcodeView.value?.pause()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                barcodeView.value?.resume()
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    barcodeView.value?.pause()
                }
            }
            // Teal scan frame over the preview.
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .border(4.dp, AppTheme.colors.accent, RoundedCornerShape(16.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .border(4.dp, AppTheme.colors.accent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(56.dp),
                )
            }
            Text(
                "Enable camera",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppTheme.colors.accent)
                    .clickable(onClick = onRequestPermission)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SimpleHeader(title: String, onBack: () -> Unit) {
    Surface(color = AppTheme.colors.card) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Who supervises a child, with a way to ask its phone to remove another parent. */
@Composable
private fun ParentsDialog(
    state: DashboardViewModel.ParentsDialog,
    myUid: String?,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf<com.shahaabapps.core.firebase.child.ParentInfo?>(null) }
    confirm?.let { p ->
        com.shahaabapps.core.ui.components.ConfirmDialog(
            title = "Remove ${p.email.ifBlank { "this parent" }}?",
            message = "${state.childName}'s phone will be asked to approve. Only then does this account stop " +
                "seeing ${state.childName}'s location and activity. Every parent is told.",
            confirmText = "Ask to remove",
            onConfirm = { onRemove(p.uid); confirm = null },
            onDismiss = { confirm = null },
        )
    }
    com.shahaabapps.core.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "Parents of ${state.childName}",
        icon = Icons.Filled.People,
        dismissText = "Close",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val parents = state.parents
            if (parents == null) {
                com.shahaabapps.core.ui.components.AppLoader(size = 22.dp)
            } else {
                parents.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.email.ifBlank { "A parent account" } + if (p.uid == myUid) " (you)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (p.uid in state.pendingRemovals) {
                                Text(
                                    "Removal waiting for approval on ${state.childName}'s phone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.secondaryLabel,
                                )
                            }
                        }
                        if (p.uid != myUid && p.uid !in state.pendingRemovals) {
                            androidx.compose.material3.TextButton(enabled = !state.busy, onClick = { confirm = p }) {
                                Text("Remove", color = AppTheme.colors.destructive)
                            }
                        }
                    }
                }
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
            }
        }
    }
}

@Composable
private fun ManageChildrenScreen(
    children: List<ChildAccount>,
    viewModel: DashboardViewModel,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf<ChildAccount?>(null) }
    var pendingReconnectId by remember { mutableStateOf<String?>(null) }
    val reconnectState by viewModel.reconnectState.collectAsStateWithLifecycle()
    val parentsDialog by viewModel.parentsDialog.collectAsStateWithLifecycle()
    val pendingRestores by viewModel.pendingRestores.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val id = pendingReconnectId
        if (id != null) viewModel.reconnectChild(id, result.contents)
        pendingReconnectId = null
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = pendingReconnectId
        pendingReconnectId = null
        if (uri != null && id != null) {
            scope.launch {
                val contents = withContext(Dispatchers.IO) { QrImageDecoder.decodeFromUri(context, uri) }
                viewModel.reconnectChild(id, contents)
            }
        }
    }
    fun launchReconnectScan(childId: String) {
        pendingReconnectId = childId
        scanLauncher.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the QR on the reinstalled Shield").setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
    fun launchReconnectFromGallery(childId: String) {
        pendingReconnectId = childId
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    confirm?.let { child ->
        com.shahaabapps.core.ui.components.ConfirmDialog(
            title = "Remove ${child.profile.name.ifBlank { "this child" }}?",
            message = "This unlinks your account from this phone. If you're its last parent, Shield's restrictions turn off and it shows as no longer supervised. You can re-pair anytime.",
            confirmText = "Remove",
            onConfirm = { onRemove(child.childId); confirm = null },
            onDismiss = { confirm = null },
        )
    }

    parentsDialog?.let { pd ->
        ParentsDialog(pd, myUid = viewModel.myUid, onRemove = viewModel::requestRemoval, onDismiss = viewModel::closeParents)
    }

    when (val rs = reconnectState) {
        is ReconnectState.Working -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { },
            title = "Restoring…",
            dismissText = null,
            dismissOnOutsideTouch = false,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.shahaabapps.core.ui.components.AppLoader(size = 22.dp)
                Text("  Handing this child's data back to the phone.")
            }
        }
        is ReconnectState.Success -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = viewModel::clearReconnectState,
            title = if (rs.waitMinutes > 0) "Restore waiting" else "Phone restored",
            icon = Icons.Filled.CheckCircle,
            dismissText = "Done",
        ) {
            Text(
                if (rs.waitMinutes > 0)
                    "Another parent also supervises this child, so the restore waits about ${rs.waitMinutes} minutes " +
                        "in case they want to stop it. Keep Shield open on the reinstalled phone; it restarts once when it's done."
                else
                    "The reinstalled phone is becoming this child again, with all its places, rules and history. It takes a minute and Shield restarts once on that phone.",
                textAlign = TextAlign.Center,
            )
        }
        is ReconnectState.Error -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = viewModel::clearReconnectState,
            title = "Couldn't restore",
            dismissText = "OK",
        ) {
            Text(rs.message, textAlign = TextAlign.Center)
        }
        ReconnectState.Idle -> Unit
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        SimpleHeader(title = "Children", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(children, key = { it.childId }) { child ->
                val online = child.device.lastSeenMillis > 0 &&
                    System.currentTimeMillis() - child.device.lastSeenMillis < 5 * 60_000L
                com.shahaabapps.core.ui.components.CupertinoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = child.profile.name, photoUrl = child.profile.photoUrl, size = 48.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(child.profile.name.ifBlank { "Child" }, style = MaterialTheme.typography.titleMedium)
                            com.shahaabapps.core.ui.components.StatusChip(
                                text = if (online) "Online" else "Offline",
                                color = if (online) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
                            )
                            if (child.childId in pendingRestores) {
                                androidx.compose.material3.TextButton(onClick = { viewModel.cancelRestore(child.childId) }) {
                                    Text("Restore waiting · Cancel", color = AppTheme.colors.destructive)
                                }
                            }
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            androidx.compose.material3.IconButton(onClick = { menu = true }) {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = AppTheme.colors.secondaryLabel,
                                )
                            }
                            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Parents") },
                                    onClick = {
                                        menu = false
                                        viewModel.openParents(child.childId, child.profile.name.ifBlank { "your child" })
                                    },
                                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.People, contentDescription = null, tint = AppTheme.colors.accent) },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Restore reinstalled phone · scan code") },
                                    onClick = { menu = false; launchReconnectScan(child.childId) },
                                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = AppTheme.colors.accent) },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Restore reinstalled phone · from gallery") },
                                    onClick = { menu = false; launchReconnectFromGallery(child.childId) },
                                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Image, contentDescription = null, tint = AppTheme.colors.accent) },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Remove", color = AppTheme.colors.destructive) },
                                    onClick = { menu = false; confirm = child },
                                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = null, tint = AppTheme.colors.destructive) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Shield reinstalled on a child's phone? Don't add it as a new child: use ⋮ → \"Restore reinstalled phone\" on that child and scan its QR. Its places, rules and history stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
                CupertinoPrimaryButton(
                    text = "Add a child",
                    onClick = onAdd,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
