package com.journal.app.data.repository.mock

import com.journal.app.data.model.CommonDetail
import com.journal.app.data.model.MatchCard
import com.journal.app.data.model.MatchStatus
import com.journal.app.data.repository.MatchRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockMatchRepository @Inject constructor() : MatchRepository {

    private val matches = MutableStateFlow(generateMockMatches())

    override fun getDailyMatches(): Flow<List<MatchCard>> = matches.asStateFlow()

    override suspend fun acceptMatch(matchId: String) {
        delay(200)
        matches.value = matches.value.map {
            if (it.id == matchId) it.copy(status = MatchStatus.ACCEPTED) else it
        }
    }

    override suspend fun skipMatch(matchId: String) {
        delay(100)
        matches.value = matches.value.map {
            if (it.id == matchId) it.copy(status = MatchStatus.SKIPPED) else it
        }
    }

    override suspend fun sendIceBreakMessage(matchId: String, message: String, round: Int): Boolean {
        delay(300)
        return true
    }

    companion object {
        fun generateMockMatches(): List<MatchCard> = listOf(
            MatchCard(
                id = "match-1",
                matchedUserId = "user-xiaoming",
                matchedUserNickname = "小明",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("location", "望京"),
                    CommonDetail("mood", "平静"),
                    CommonDetail("tag", "咖啡"),
                ),
                iceBreakMessage = "你也经常去那家咖啡店？他家的桂花拿铁很好喝 ☕",
            ),
            MatchCard(
                id = "match-2",
                matchedUserId = "user-xiaohong",
                matchedUserNickname = "小红",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("location", "望京SOHO"),
                    CommonDetail("tag", "桂花"),
                    CommonDetail("tag", "工作"),
                ),
                iceBreakMessage = "望京SOHO附近那家咖啡店的桂花香，你今天也闻到了吗？",
            ),
            MatchCard(
                id = "match-3",
                matchedUserId = "user-daniu",
                matchedUserNickname = "大牛",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("activity", "跑步"),
                    CommonDetail("mood", "平静"),
                ),
                iceBreakMessage = "你也今天跑了步？感觉这个天气跑步特别舒服 🏃",
            ),
        )
    }
}
