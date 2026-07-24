package com.journal.app.data.model

/**
 * A discoverable daily summary from another user, ranked by how well it matches yours.
 *
 * [matchPercent] mirrors the match engine's cosine similarity (0–100), and [matchReason]
 * is derived from the users' common details (location / mood / tag / activity), matching
 * `ai-services/match_engine/matcher.py`.
 */
data class FeedPost(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val imageUrls: List<String>,
    val audioUrl: String?,
    val audioDurationMs: Long?,
    val matchPercent: Int,
    val matchReason: String,
    val timestamp: Long,
)
