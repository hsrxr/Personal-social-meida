package com.journal.app.data.model

/**
 * A user's public-facing profile: basic info + their published journal summaries.
 */
data class UserPublicProfile(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String,
    val publicJournals: List<PublicJournalSummary>,
)

data class PublicJournalSummary(
    val date: String,
    val summary: String,
    val keywords: List<String>,
    val mood: String?,
    val photoUrls: List<String>,
)
