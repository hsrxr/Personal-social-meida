package com.journal.app.data.model

/** One of the current user's own posts, offered as the seed for a similarity search. */
data class MyPost(
    val id: String,
    val text: String,
)

data class SimilarUser(
    val name: String,
    val avatarUrl: String?,
    val similarityPercent: Int,
    val previewText: String,
)

data class SimilarPost(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val similarityPercent: Int,
    val text: String,
)

data class SimilarResults(
    val users: List<SimilarUser>,
    val posts: List<SimilarPost>,
)
