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
                matchedUserId = "user-alex",
                matchedUserNickname = "Alex",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("location", "Wangjing"),
                    CommonDetail("mood", "Calm"),
                    CommonDetail("tag", "coffee"),
                ),
                iceBreakMessage = "You go to that coffee shop too? Their osmanthus latte is amazing ☕",
            ),
            MatchCard(
                id = "match-2",
                matchedUserId = "user-mia",
                matchedUserNickname = "Mia",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("location", "Wangjing SOHO"),
                    CommonDetail("tag", "osmanthus"),
                    CommonDetail("tag", "work"),
                ),
                iceBreakMessage = "Did you catch the osmanthus scent near that coffee shop in Wangjing SOHO today?",
            ),
            MatchCard(
                id = "match-3",
                matchedUserId = "user-sam",
                matchedUserNickname = "Sam",
                matchDate = LocalDate.now(),
                commonDetails = listOf(
                    CommonDetail("activity", "running"),
                    CommonDetail("mood", "Calm"),
                ),
                iceBreakMessage = "You went for a run today too? The weather was perfect for it 🏃",
            ),
        )
    }
}
