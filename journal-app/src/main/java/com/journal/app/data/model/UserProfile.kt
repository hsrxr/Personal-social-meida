package com.journal.app.data.model

/**
 * The signed-in user's social profile shown on Home.
 * Counts are social-facing aggregates (not the raw local entry count).
 */
data class UserProfile(
    val handle: String,
    val tagline: String,
    val avatarUrl: String?,
    val entriesCount: Int,
    val notesCount: Int,
    val matchedFriendsCount: Int,
)
