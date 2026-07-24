package com.journal.app.data.repository.mock

import com.journal.app.data.model.MyPost
import com.journal.app.data.model.SimilarPost
import com.journal.app.data.model.SimilarResults
import com.journal.app.data.model.SimilarUser
import com.journal.app.data.repository.SearchRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Mock similarity search. It stands in for the backend match engine behind
 * [SearchRepository]; it isn't a fixed lookup table — results are ranked against the
 * seed text by shared-word overlap, so different seeds yield different rankings.
 */
@Singleton
class MockSearchRepository @Inject constructor() : SearchRepository {

    override suspend fun getMyPosts(): List<MyPost> {
        delay(100)
        return MY_POSTS
    }

    override suspend fun findSimilar(postId: String): SimilarResults {
        delay(300)
        val seed = MY_POSTS.firstOrNull { it.id == postId }?.text ?: return SimilarResults(emptyList(), emptyList())
        return rank(seed)
    }

    override suspend fun search(query: String): SimilarResults {
        delay(300)
        if (query.isBlank()) return SimilarResults(emptyList(), emptyList())
        return rank(query)
    }

    /** Rank fixtures by word-overlap similarity with [seed], keeping the closest matches. */
    private fun rank(seed: String): SimilarResults {
        val seedTokens = tokenize(seed)

        val users = CANDIDATE_USERS
            .map { it.copy(similarityPercent = similarity(seedTokens, tokenize(it.previewText))) }
            .filter { it.similarityPercent > 0 }
            .sortedByDescending { it.similarityPercent }

        val posts = CANDIDATE_POSTS
            .map { it.copy(similarityPercent = similarity(seedTokens, tokenize(it.text))) }
            .filter { it.similarityPercent > 0 }
            .sortedByDescending { it.similarityPercent }

        return SimilarResults(users = users, posts = posts)
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(' ', ',', '.', '!', '?', '#', '\n')
            .map { it.trim() }
            .filter { it.length > 2 }
            .toSet()

    /** Jaccard overlap scaled to a 0–100 percentage. */
    private fun similarity(a: Set<String>, b: Set<String>): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return ((intersection.toDouble() / union) * 100).roundToInt()
    }

    companion object {
        private val MY_POSTS = listOf(
            MyPost("my-1", "Morning run by the river. #running felt amazing today."),
            MyPost("my-2", "Coffee and reading at the corner cafe. #coffee #reading"),
            MyPost("my-3", "Wrapped up the big project and went for a weekend hike. #work #weekend"),
        )

        // similarityPercent is recomputed per query; the seed values here are placeholders.
        private val CANDIDATE_USERS = listOf(
            SimilarUser(
                name = "Ethan",
                avatarUrl = "https://i.pravatar.cc/150?img=12",
                similarityPercent = 0,
                previewText = "Long trail run this morning, then coffee to recover. #running #coffee",
            ),
            SimilarUser(
                name = "Mia",
                avatarUrl = "https://i.pravatar.cc/150?img=32",
                similarityPercent = 0,
                previewText = "Quiet afternoon reading at the corner cafe. #reading #coffee",
            ),
            SimilarUser(
                name = "Noah",
                avatarUrl = "https://i.pravatar.cc/150?img=51",
                similarityPercent = 0,
                previewText = "Wrapped a big project today. Weekend hike to celebrate. #work #weekend",
            ),
        )

        private val CANDIDATE_POSTS = listOf(
            SimilarPost(
                id = "sp-1",
                authorName = "Ethan",
                authorAvatarUrl = "https://i.pravatar.cc/150?img=12",
                similarityPercent = 0,
                text = "Riverside running route is unbeatable at sunrise. #running",
            ),
            SimilarPost(
                id = "sp-2",
                authorName = "Mia",
                authorAvatarUrl = "https://i.pravatar.cc/150?img=32",
                similarityPercent = 0,
                text = "New book, same corner cafe, perfect coffee. #reading #coffee",
            ),
            SimilarPost(
                id = "sp-3",
                authorName = "Noah",
                authorAvatarUrl = "https://i.pravatar.cc/150?img=51",
                similarityPercent = 0,
                text = "Big project shipped. Time for a weekend hike. #weekend #work",
            ),
        )
    }
}
