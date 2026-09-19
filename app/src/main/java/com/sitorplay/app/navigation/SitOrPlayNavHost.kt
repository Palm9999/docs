package com.sitorplay.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.sitorplay.app.ui.addplayer.AddPlayerScreen
import com.sitorplay.app.ui.compare.CompareScreen
import com.sitorplay.app.ui.playerdetail.PlayerDetailScreen
import com.sitorplay.app.ui.roster.RosterScreen
import com.sitorplay.app.ui.settings.SettingsScreen
import com.sitorplay.app.ui.waiver.WaiverWireScreen

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Routes.ROSTER, "Roster", Icons.Filled.Home),
    BottomNavItem(Routes.WAIVER_WIRE, "Waiver", Icons.Filled.TrendingUp),
    BottomNavItem(Routes.COMPARE, "Compare", Icons.Filled.CompareArrows),
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun SitOrPlayNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BOTTOM_NAV_ITEMS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(
                                        item.route,
                                        navOptions {
                                            popUpTo(Routes.ROSTER) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    )
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ROSTER,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ROSTER) {
                RosterScreen(
                    onAddPlayer = { navController.navigate(Routes.addPlayer()) },
                    onPlayerClick = { playerId -> navController.navigate(Routes.playerDetail(playerId)) }
                )
            }
            composable(Routes.WAIVER_WIRE) {
                WaiverWireScreen(
                    onAddPlayer = { externalId -> navController.navigate(Routes.addPlayer(externalId)) }
                )
            }
            composable(Routes.COMPARE) {
                CompareScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.ADD_PLAYER,
                arguments = listOf(
                    navArgument("prefillExternalId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AddPlayerScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PLAYER_DETAIL,
                arguments = listOf(navArgument("playerId") { type = NavType.LongType })
            ) {
                PlayerDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
