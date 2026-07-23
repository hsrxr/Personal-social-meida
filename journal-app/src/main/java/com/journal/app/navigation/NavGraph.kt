package com.journal.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.journal.app.ui.screen.ai.SummaryScreen
import com.journal.app.ui.screen.ai.SocialCopyScreen
import com.journal.app.ui.screen.annotate.AnnotateScreen
import com.journal.app.ui.screen.home.CalendarScreen
import com.journal.app.ui.screen.home.HomeScreen
import com.journal.app.ui.screen.match.IceBreakScreen
import com.journal.app.ui.screen.match.MatchScreen
import com.journal.app.ui.screen.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onEntryClick = { entryId ->
                    navController.navigate(Screen.Annotate.createRoute(entryId))
                },
                onCalendarClick = {
                    navController.navigate(Screen.Calendar.route)
                },
                onSummaryClick = {
                    navController.navigate(Screen.Summary.route)
                },
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                onDateClick = { /* navigate back to home with date */ },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Summary.route) {
            SummaryScreen(
                onSocialCopyClick = {
                    navController.navigate(Screen.SocialCopy.route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.SocialCopy.route) {
            SocialCopyScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Match.route) {
            MatchScreen(
                onMatchClick = { matchId ->
                    navController.navigate(Screen.IceBreak.createRoute(matchId))
                },
                onFeedClick = {
                    navController.navigate(Screen.Feed.route)
                },
            )
        }

        composable(
            route = Screen.IceBreak.route,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) {
            IceBreakScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Feed.route) {
            // FeedScreen placeholder — will be implemented in Sprint 4
        }

        composable(
            route = Screen.Annotate.route,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) {
            AnnotateScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Profile.route) {
            // ProfileScreen placeholder — will be implemented in Sprint 5
        }
    }
}
