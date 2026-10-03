package com.shahaabapps.watchdog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.watchdog.feature.dashboard.DashboardScreen
import com.shahaabapps.watchdog.feature.rules.ChildDetailScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val CHILD_DETAIL = "child/{childId}"
    fun childDetail(childId: String) = "child/$childId"
}

@Composable
fun ParentNavGraph(profile: UserProfile) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                profile = profile,
                onChildClick = { childId -> navController.navigate(Routes.childDetail(childId)) },
            )
        }
        composable(
            route = Routes.CHILD_DETAIL,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) {
            // childId is read from SavedStateHandle inside ChildDetailViewModel.
            ChildDetailScreen()
        }
    }
}
