package com.journal.app.data.repository

import com.journal.app.data.local.dao.MatchDao
import com.journal.app.data.local.entity.toDomain
import com.journal.app.data.local.entity.toEntity
import com.journal.app.data.model.MatchCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRepositoryImpl @Inject constructor(
    private val matchDao: MatchDao,
) : MatchRepository {

    override fun getDailyMatches(): Flow<List<MatchCard>> {
        val today = LocalDate.now().toString()
        return matchDao.getMatches(today).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun acceptMatch(matchId: String) {
        matchDao.updateStatus(matchId, "ACCEPTED")
    }

    override suspend fun skipMatch(matchId: String) {
        matchDao.updateStatus(matchId, "SKIPPED")
    }

    override suspend fun sendIceBreakMessage(matchId: String, message: String, round: Int): Boolean {
        // Round validation: max 3 rounds
        if (round > 3) return false
        // In the local implementation, messages are stored on the server.
        // Client just records the round progress locally for now.
        return true
    }

    suspend fun cacheMatches(matches: List<MatchCard>) {
        matchDao.insertAll(matches.map { it.toEntity() })
    }
}
