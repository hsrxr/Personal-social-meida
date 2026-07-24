package com.journal.app.navigation

/**
 * Central route table for Compose Navigation.
 * Each screen is a unique route constant used by [NavGraph].
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Calendar : Screen("calendar")
    data object MediaDetail : Screen("media_detail/{entryId}") {
        fun createRoute(entryId: String) = "media_detail/$entryId"
    }
    data object Summary : Screen("summary")
    data object SocialCopy : Screen("social_copy")
    data object Match : Screen("match")
    data object MatchDetail : Screen("match_detail/{matchId}") {
        fun createRoute(matchId: String) = "match_detail/$matchId"
    }
    data object IceBreak : Screen("ice_break/{matchId}") {
        fun createRoute(matchId: String) = "ice_break/$matchId"
    }
    data object Feed : Screen("feed")
    data object Annotate : Screen("annotate/{entryId}") {
        fun createRoute(entryId: String) = "annotate/$entryId"
    }
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")

    // ── Echoes bottom-nav destinations ──
    data object Discover : Screen("discover")
    data object Messages : Screen("messages")
    data object FullJournal : Screen("full_journal")
    data object FindSimilar : Screen("find_similar")
}
