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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
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
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
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
    OVERVIEW("overview", "Overview", Icons.Filled.GridView),
    MAP("map", "Map", Icons.Filled.Map),
    SCREENTIME("screentime", "Screen time", Icons.Filled.BarChart),
}

@Composable
fun ParentShell(
    profile: UserProfile,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val children by viewModel.children.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()

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
        AddChildScreen(profile = profile, linkState = linkState, viewModel = viewModel)
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

    Scaffold(
        containerColor = AppTheme.colors.groupedBackground,
        topBar = {
            if (showChrome) {
                Column {
                    FlatTopBar(
                        greeting = "Hi, ${profile.name}",
                        title = selectedChild.profile.name.ifBlank { "Child" },
                        parentName = profile.name,
                        parentPhoto = profile.photoUrl,
                        onManage = { navController.navigate("manage") },
                        onSettings = { navController.navigate("settings") },
                    )
                    if (children.size > 1) {
                        ChildChipRow(
                            children = children,
                            selectedId = currentChildId,
                            onSelect = { id ->
                                navController.navigate("$currentTab/$id") { launchSingleTop = true }
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (showChrome) {
                ParentBottomBar(currentTab = currentTab) { tab ->
                    navController.navigate("${tab.route}/$currentChildId") {
                        popUpTo("${Tab.OVERVIEW.route}/{childId}") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "${Tab.OVERVIEW.route}/$initialChild",
            modifier = Modifier.padding(padding),
        ) {
            val childArg = listOf(navArgument("childId") { type = NavType.StringType })
            composable("overview/{childId}", childArg) {
                OverviewScreen(
                    onOpenMap = { navController.navigate("${Tab.MAP.route}/$currentChildId") { launchSingleTop = true } },
                )
            }
            composable("map/{childId}", childArg) { GeofenceScreen(onBack = { }, showBack = false) }
            composable("screentime/{childId}", childArg) { UsageScreen(onBack = { }, embedded = true) }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
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
                AddChildScreen(profile = profile, linkState = linkState, viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun FlatTopBar(
    greeting: String,
    title: String,
    parentName: String,
    parentPhoto: String?,
    onManage: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = AppTheme.colors.card) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting, fontSize = 13.sp, color = AppTheme.colors.secondaryLabel)
                Text(
                    title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                Avatar(
                    name = parentName,
                    photoUrl = parentPhoto,
                    size = 40.dp,
                    modifier = Modifier.clickableAvatar { menu = true },
                )
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Manage children") },
                        onClick = { menu = false; onManage() },
                        leadingIcon = {
                            androidx.compose.material3.Icon(Icons.Filled.Group, contentDescription = null)
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menu = false; onSettings() },
                        leadingIcon = {
                            androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableAvatar(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun ChildChipRow(
    children: List<ChildAccount>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Surface(color = AppTheme.colors.card) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            items(children, key = { it.childId }) { child ->
                FilterChip(
                    selected = child.childId == selectedId,
                    onClick = { onSelect(child.childId) },
                    label = { Text(child.profile.name.ifBlank { "Child" }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppTheme.colors.accent.copy(alpha = 0.15f),
                        selectedLabelColor = AppTheme.colors.accent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ParentBottomBar(currentTab: String, onSelect: (Tab) -> Unit) {
    // Floating, rounded pill bar with a pastel highlight on the active tab.
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = AppTheme.colors.card,
        shadowElevation = 12.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(6.dp),
        ) {
            Tab.entries.forEach { tab ->
                val selected = currentTab == tab.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                    )
                    if (selected) {
                        Text(
                            tab.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.accent,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
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
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.onQrScanned(result.contents)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        if (onBack != null) {
            SimpleHeader(title = "Add a child", onBack = onBack)
        } else {
            FlatTopBar(
                greeting = "Hi, ${profile.name}",
                title = "Watch Dog",
                parentName = profile.name,
                parentPhoto = profile.photoUrl,
                onManage = { },
                onSettings = { },
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (linkState is LinkState.Error) {
                Text(linkState.message, color = AppTheme.colors.destructive, textAlign = TextAlign.Center)
            }
            Text(
                "Add your first child",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            Text(
                "Open Shield on your child's phone and scan the code it shows.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            CupertinoPrimaryButton(
                text = "Scan child's QR",
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the child's pairing code").setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            CupertinoTextButton(
                text = "Choose QR from gallery",
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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
        is ReconnectState.Working -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            confirmButton = {},
            title = { Text("Reconnecting…") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(22.dp)); Text("  Migrating this child's data.") } },
        )
        is ReconnectState.Success -> androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::clearReconnectState,
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::clearReconnectState) { Text("Done") } },
            title = { Text("Device reconnected") },
            text = { Text("The reinstalled device is linked again with all its places, rules and settings.") },
        )
        is ReconnectState.Error -> androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::clearReconnectState,
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::clearReconnectState) { Text("OK") } },
            title = { Text("Couldn't reconnect") },
            text = { Text(rs.message) },
        )
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
