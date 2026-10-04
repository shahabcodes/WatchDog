package com.shahaabapps.watchdog.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Place
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
import com.shahaabapps.watchdog.feature.dashboard.QrImageDecoder
import com.shahaabapps.watchdog.feature.geofence.GeofenceScreen
import com.shahaabapps.watchdog.feature.overview.OverviewScreen
import com.shahaabapps.watchdog.feature.rules.AppsScreen
import com.shahaabapps.watchdog.feature.settings.SettingsScreen
import com.shahaabapps.watchdog.feature.travel.TravelHistoryScreen
import com.shahaabapps.watchdog.feature.usage.UsageScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "Overview", Icons.Filled.GridView),
    APPS("apps", "Apps", Icons.Filled.Apps),
    PLACES("places", "Places", Icons.Filled.Place),
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
                        onAvatarClick = { navController.navigate("settings") },
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
                    onOpenPlaces = { navController.navigate("${Tab.PLACES.route}/$currentChildId") { launchSingleTop = true } },
                    onOpenTravel = { navController.navigate("travel/$currentChildId") },
                )
            }
            composable("apps/{childId}", childArg) { AppsScreen() }
            composable("places/{childId}", childArg) { GeofenceScreen(onBack = { }, showBack = false) }
            composable("screentime/{childId}", childArg) { UsageScreen(onBack = { }, embedded = true) }
            composable("travel/{childId}", childArg) {
                TravelHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
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
    onAvatarClick: () -> Unit,
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
            Box(modifier = Modifier.size(40.dp)) {
                Avatar(
                    name = parentName,
                    photoUrl = parentPhoto,
                    size = 40.dp,
                    modifier = Modifier.clickableAvatar(onAvatarClick),
                )
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
    NavigationBar(containerColor = AppTheme.colors.card) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppTheme.colors.accent,
                    selectedTextColor = AppTheme.colors.accent,
                    indicatorColor = AppTheme.colors.accent.copy(alpha = 0.14f),
                    unselectedIconColor = AppTheme.colors.secondaryLabel,
                    unselectedTextColor = AppTheme.colors.secondaryLabel,
                ),
            )
        }
    }
}

@Composable
private fun AddChildScreen(
    profile: UserProfile,
    linkState: LinkState,
    viewModel: DashboardViewModel,
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
        FlatTopBar(
            greeting = "Hi, ${profile.name}",
            title = "Watch Dog",
            parentName = profile.name,
            parentPhoto = profile.photoUrl,
            onAvatarClick = { },
        )
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
