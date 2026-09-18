package com.sitorplay.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sitorplay.app.ui.addplayer.AddPlayerScreen
import com.sitorplay.app.ui.playerdetail.PlayerDetailScreen
import com.sitorplay.app.ui.roster.RosterScreen

@Composable
fun SitOrPlayNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.ROSTER) {
        composable(Routes.ROSTER) {
            RosterScreen(
                onAddPlayer = { navController.navigate(Routes.ADD_PLAYER) },
                onPlayerClick = { playerId -> navController.navigate(Routes.playerDetail(playerId)) }
            )
        }
        composable(Routes.ADD_PLAYER) {
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
