package com.journal.app.data.repository

import com.journal.app.data.model.MatchCard
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    fun getDailyMatches(): Flow<List<MatchCard>>
    suspend fun acceptMatch(matchId: String)
    suspend fun skipMatch(matchId: String)
    suspend fun sendIceBreakMessage(matchId: String, message: String, round: Int): Boolean
}
