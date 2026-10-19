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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

    // Show a one-time "connected" screen after the first child is paired during
    // onboarding (we only arrived here from the empty/first-run state).
    var justConnected by rememberSaveable { mutableStateOf(false) }
    var sawEmpty by rememberSaveable { mutableStateOf(false) }
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
                onContinue = { justConnected = false; sawEmpty = false; viewModel.clearLinkState() },
            )
        }
        return
    }

    val initialChild = rememberSaveable { children.first().childId }
    val navController = rememberNavController()
    val backEntry by navController.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val currentTab = route?.substringBefore("/") ?: Tab.OVERVIEW.route
    val currentChildId = backEntry?.arguments?.getString("childId") ?: initialChild
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
        NavHost(
            navController = navController,
            startDestination = "${Tab.OVERVIEW.route}/$initialChild",
            modifier = Modifier.padding(padding),
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
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }
            composable("security") {
                com.shahaabapps.watchdog.feature.security.MfaScreen(onBack = { navController.popBackStack() })
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
private fun ConnectedChildScreen(childName: String?, onContinue: () -> Unit) {
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
                .setPrompt("Scan the reinstalled Shield's code").setBeepEnabled(false)
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
            message = "This unlinks the device. Its restrictions turn off and it shows as no longer supervised. You can re-pair anytime.",
            confirmText = "Remove",
            onConfirm = { onRemove(child.childId); confirm = null },
            onDismiss = { confirm = null },
        )
    }

    when (val rs = reconnectState) {
        is ReconnectState.Working -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { },
            title = "Reconnecting…",
            dismissText = null,
            dismissOnOutsideTouch = false,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.shahaabapps.core.ui.components.AppLoader(size = 22.dp)
                Text("  Migrating this child's data.")
            }
        }
        is ReconnectState.Success -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = viewModel::clearReconnectState,
            title = "Device reconnected",
            icon = Icons.Filled.CheckCircle,
            dismissText = "Done",
        ) {
            Text(
                "The reinstalled device is linked again with all its places, rules and settings.",
                textAlign = TextAlign.Center,
            )
        }
        is ReconnectState.Error -> com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = viewModel::clearReconnectState,
            title = "Couldn't reconnect",
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
                                    text = { Text("Reconnect · scan code") },
                                    onClick = { menu = false; launchReconnectScan(child.childId) },
                                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = AppTheme.colors.accent) },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Reconnect · from gallery") },
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
                    "Reinstalled a child's phone? Use \"Reconnect device\" on that child to scan its new code — its places, rules and history move over automatically.",
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
