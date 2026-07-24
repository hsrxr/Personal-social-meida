package com.journal.app.data.repository

import com.journal.app.data.model.MyPost
import com.journal.app.data.model.SimilarResults

interface SearchRepository {
    /** The current user's posts, used to seed a "find similar" search. */
    suspend fun getMyPosts(): List<MyPost>

    /** Similar users and posts for a chosen seed post. */
    suspend fun findSimilar(postId: String): SimilarResults

    /** Free-text similarity search. */
    suspend fun search(query: String): SimilarResults
}
