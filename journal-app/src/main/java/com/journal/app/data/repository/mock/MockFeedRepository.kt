package com.journal.app.data.repository.mock

import com.journal.app.data.model.FeedPost
import com.journal.app.data.repository.FeedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockFeedRepository @Inject constructor() : FeedRepository {

    private val posts = MutableStateFlow(generateMockPosts())

    override fun getEchoes(): Flow<List<FeedPost>> =
        posts.map { list -> list.sortedByDescending { it.matchPercent } }

    override suspend fun getPost(id: String): FeedPost? {
        delay(100)
        return posts.value.firstOrNull { it.id == id }
    }

    override suspend fun sayHi(postId: String): Boolean {
        delay(250)
        return posts.value.any { it.id == postId }
    }

    companion object {
        private const val ONE_HOUR = 3_600_000L

        fun generateMockPosts(): List<FeedPost> {
            val now = System.currentTimeMillis()
            return listOf(
                FeedPost(
                    id = "post-1",
                    authorName = "Ethan",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=12",
                    text = "Long trail run this morning, then coffee to recover. #running #coffee",
                    imageUrls = listOf("https://picsum.photos/seed/echoes-feed-run/600/400"),
                    audioUrl = null,
                    audioDurationMs = null,
                    matchPercent = 85,
                    matchReason = "You both mentioned #running",
                    timestamp = now - 2 * ONE_HOUR,
                ),
                FeedPost(
                    id = "post-2",
                    authorName = "Mia",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=32",
                    text = "Quiet afternoon reading at the corner cafe. Exactly what I needed. #reading #coffee",
                    imageUrls = listOf(
                        "https://picsum.photos/seed/echoes-feed-cafe1/600/400",
                        "https://picsum.photos/seed/echoes-feed-cafe2/600/400",
                    ),
                    audioUrl = null,
                    audioDurationMs = 32_000,
                    matchPercent = 78,
                    matchReason = "You both mentioned #coffee",
                    timestamp = now - 5 * ONE_HOUR,
                ),
                FeedPost(
                    id = "post-3",
                    authorName = "Noah",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=51",
                    text = "Wrapped a big project today. Feeling accomplished and a little tired. #work",
                    imageUrls = emptyList(),
                    audioUrl = null,
                    audioDurationMs = 48_000,
                    matchPercent = 64,
                    matchReason = "You both had a calm mood today",
                    timestamp = now - 26 * ONE_HOUR,
                ),
            )
        }
    }
}
