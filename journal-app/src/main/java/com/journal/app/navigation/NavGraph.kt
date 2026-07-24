package com.journal.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.journal.app.ui.components.BottomNavBar
import com.journal.app.ui.components.bottomNavRoutes
import com.journal.app.ui.screen.ai.SummaryScreen
import com.journal.app.ui.screen.ai.SocialCopyScreen
import com.journal.app.ui.screen.annotate.AnnotateScreen
import com.journal.app.ui.screen.discover.DiscoverScreen
import com.journal.app.ui.screen.home.CalendarScreen
import com.journal.app.ui.screen.home.HomeScreen
import com.journal.app.ui.screen.journal.FullJournalScreen
import com.journal.app.ui.screen.match.IceBreakScreen
import com.journal.app.ui.screen.match.MatchScreen
import com.journal.app.ui.screen.messages.MessagesScreen
import com.journal.app.ui.screen.search.FindSimilarScreen
import com.journal.app.ui.screen.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        // Inner screens own their status-bar insets via their own Scaffolds;
        // this outer Scaffold only reserves space for the bottom nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onDayClick = { dateStr ->
                        navController.navigate(Screen.Calendar.route)
                    },
                    onViewFullJournalClick = {
                        navController.navigate(Screen.FullJournal.route)
                    },
                    onGenerateClick = {
                        navController.navigate(Screen.Summary.route)
                    },
                    onManualEditClick = {
                        navController.navigate(Screen.Summary.route)
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                )
            }

            composable(Screen.Discover.route) {
                DiscoverScreen(
                    onSearchClick = {
                        navController.navigate(Screen.FindSimilar.route)
                    },
                )
            }

            composable(Screen.Messages.route) {
                MessagesScreen()
            }

            composable(Screen.FullJournal.route) {
                FullJournalScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.FindSimilar.route) {
                FindSimilarScreen(
                    onBack = { navController.popBackStack() },
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
                    onBack = { navController.popBackStack() },
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
                // FeedScreen placeholder — superseded by Discover
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
}
