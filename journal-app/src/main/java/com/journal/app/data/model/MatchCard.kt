package com.journal.app.data.model

import java.time.LocalDate

data class MatchCard(
    val id: String,
    val matchedUserId: String,
    val matchedUserNickname: String,
    val matchDate: LocalDate,
    val commonDetails: List<CommonDetail> = emptyList(),
    val iceBreakMessage: String = "",
    val status: MatchStatus = MatchStatus.PENDING,
)

data class CommonDetail(
    val type: String,   // "location", "mood", "tag"
    val value: String,
)

enum class MatchStatus { PENDING, ACCEPTED, SKIPPED }
