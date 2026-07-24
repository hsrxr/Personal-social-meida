package com.journal.app.data.repository

import com.journal.app.data.model.FeedPost
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    /** Echoes feed: other users' daily summaries, ordered by match degree (desc). */
    fun getEchoes(): Flow<List<FeedPost>>

    suspend fun getPost(id: String): FeedPost?

    /** Records a "Say Hi" to the post's author. Returns true once acknowledged. */
    suspend fun sayHi(postId: String): Boolean
}
