package com.shahaabapps.watchdog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.watchdog.feature.dashboard.DashboardScreen
import com.shahaabapps.watchdog.feature.geofence.GeofenceScreen
import com.shahaabapps.watchdog.feature.rules.ChildDetailScreen
import com.shahaabapps.watchdog.feature.settings.SettingsScreen
import com.shahaabapps.watchdog.feature.travel.TravelHistoryScreen
import com.shahaabapps.watchdog.feature.usage.UsageScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val CHILD_DETAIL = "child/{childId}"
    const val GEOFENCES = "child/{childId}/geofences"
    const val USAGE = "child/{childId}/usage"
    const val TRAVEL = "child/{childId}/travel"
    const val SETTINGS = "settings"
    fun childDetail(childId: String) = "child/$childId"
    fun geofences(childId: String) = "child/$childId/geofences"
    fun usage(childId: String) = "child/$childId/usage"
    fun travel(childId: String) = "child/$childId/travel"
}

@Composable
fun ParentNavGraph(profile: UserProfile) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                profile = profile,
                onChildClick = { childId -> navController.navigate(Routes.childDetail(childId)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.CHILD_DETAIL,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { entry ->
            val childId = entry.arguments?.getString("childId").orEmpty()
            ChildDetailScreen(
                onOpenGeofences = { navController.navigate(Routes.geofences(childId)) },
                onOpenUsage = { navController.navigate(Routes.usage(childId)) },
                onOpenTravel = { navController.navigate(Routes.travel(childId)) },
            )
        }
        composable(
            route = Routes.GEOFENCES,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) {
            GeofenceScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.USAGE,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) {
            UsageScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.TRAVEL,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) {
            TravelHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
