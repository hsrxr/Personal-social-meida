package com.journal.app.data.repository.mock

import com.journal.app.data.model.FeedPost
import com.journal.app.data.model.PublicJournalSummary
import com.journal.app.data.model.UserPublicProfile
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

    override suspend fun sayHiAndShare(postId: String, myMatchingPostText: String): Boolean {
        delay(250)
        return posts.value.any { it.id == postId }
    }

    override suspend fun getUserPublicProfile(userId: String): UserPublicProfile? {
        delay(300)
        return MOCK_PROFILES[userId]
    }

    companion object {
        private const val ONE_HOUR = 3_600_000L

        private val MOCK_PROFILES = mapOf(
            "user-ethan" to UserPublicProfile(
                userId = "user-ethan",
                name = "Ethan",
                avatarUrl = "https://i.pravatar.cc/150?img=12",
                bio = "Runner, coffee enthusiast, morning person.",
                publicJournals = listOf(
                    PublicJournalSummary(
                        date = "2026-07-25",
                        summary = "Long trail run in the morning, then coffee to recover. Perfect weekend start.",
                        keywords = listOf("running", "coffee", "weekend"),
                        mood = "Energetic",
                        photoUrls = listOf("https://picsum.photos/seed/ethan-run-1/400/400", "https://picsum.photos/seed/ethan-coffee-1/400/400"),
                    ),
                    PublicJournalSummary(
                        date = "2026-07-24",
                        summary = "Early morning 10K, saw the sunrise at the 5K mark. Feeling alive.",
                        keywords = listOf("running", "sunrise", "morning"),
                        mood = "Great",
                        photoUrls = listOf("https://picsum.photos/seed/ethan-sunrise/400/400"),
                    ),
                    PublicJournalSummary(
                        date = "2026-07-23",
                        summary = "New running shoes arrived! Tested them on the usual route. So light.",
                        keywords = listOf("running", "shopping", "gear"),
                        mood = "Excited",
                        photoUrls = listOf("https://picsum.photos/seed/ethan-shoes/400/400", "https://picsum.photos/seed/ethan-route/400/400"),
                    ),
                ),
            ),
            "user-mia" to UserPublicProfile(
                userId = "user-mia",
                name = "Mia",
                avatarUrl = "https://i.pravatar.cc/150?img=32",
                bio = "Book lover & cafe explorer. Currently reading Murakami.",
                publicJournals = listOf(
                    PublicJournalSummary(
                        date = "2026-07-25",
                        summary = "Quiet afternoon reading at the corner cafe. Exactly what I needed.",
                        keywords = listOf("reading", "coffee", "quiet"),
                        mood = "Calm",
                        photoUrls = listOf("https://picsum.photos/seed/mia-cafe-1/400/400"),
                    ),
                    PublicJournalSummary(
                        date = "2026-07-24",
                        summary = "Finished 'Kafka on the Shore'. What an incredible book. Tried a new matcha latte.",
                        keywords = listOf("reading", "matcha", "murakami"),
                        mood = "Thoughtful",
                        photoUrls = listOf("https://picsum.photos/seed/mia-book/400/400", "https://picsum.photos/seed/mia-matcha/400/400"),
                    ),
                ),
            ),
            "user-noah" to UserPublicProfile(
                userId = "user-noah",
                name = "Noah",
                avatarUrl = "https://i.pravatar.cc/150?img=51",
                bio = "Product designer. Work hard, hike harder.",
                publicJournals = listOf(
                    PublicJournalSummary(
                        date = "2026-07-25",
                        summary = "Wrapped a big project today. Feeling accomplished and a little tired.",
                        keywords = listOf("work", "project", "accomplishment"),
                        mood = "Tired",
                        photoUrls = emptyList(),
                    ),
                    PublicJournalSummary(
                        date = "2026-07-23",
                        summary = "Weekend hike at the national park. The view from the summit was breathtaking.",
                        keywords = listOf("hiking", "weekend", "nature"),
                        mood = "Great",
                        photoUrls = listOf("https://picsum.photos/seed/noah-hike/400/400", "https://picsum.photos/seed/noah-summit/400/400"),
                    ),
                ),
            ),
        )

        fun generateMockPosts(): List<FeedPost> {
            val now = System.currentTimeMillis()
            return listOf(
                FeedPost(
                    id = "post-1",
                    authorName = "Ethan",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=12",
                    authorId = "user-ethan",
                    text = "Long trail run this morning, then coffee to recover. #running #coffee",
                    imageUrls = listOf("https://picsum.photos/seed/echoes-feed-run/600/400"),
                    audioUrl = null,
                    audioDurationMs = null,
                    matchPercent = 85,
                    matchReason = "You both mentioned #running",
                    matchedMyPostText = "Morning run by the river. #running felt amazing today.",
                    timestamp = now - 2 * ONE_HOUR,
                ),
                FeedPost(
                    id = "post-2",
                    authorName = "Mia",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=32",
                    authorId = "user-mia",
                    text = "Quiet afternoon reading at the corner cafe. Exactly what I needed. #reading #coffee",
                    imageUrls = listOf(
                        "https://picsum.photos/seed/echoes-feed-cafe1/600/400",
                        "https://picsum.photos/seed/echoes-feed-cafe2/600/400",
                    ),
                    audioUrl = null,
                    audioDurationMs = 32_000,
                    matchPercent = 78,
                    matchReason = "You both mentioned #coffee",
                    matchedMyPostText = "Coffee and reading at the corner cafe. #coffee #reading",
                    timestamp = now - 5 * ONE_HOUR,
                ),
                FeedPost(
                    id = "post-3",
                    authorName = "Noah",
                    authorAvatarUrl = "https://i.pravatar.cc/150?img=51",
                    authorId = "user-noah",
                    text = "Wrapped a big project today. Feeling accomplished and a little tired. #work",
                    imageUrls = emptyList(),
                    audioUrl = null,
                    audioDurationMs = 48_000,
                    matchPercent = 64,
                    matchReason = "You both had a calm mood today",
                    matchedMyPostText = "Wrapped up the big project and celebrated with a weekend hike.",
                    timestamp = now - 26 * ONE_HOUR,
                ),
            )
        }
    }
}
