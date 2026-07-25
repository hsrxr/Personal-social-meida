package com.journal.app.data.repository

import com.journal.app.data.model.SocialCopy
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Summary
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.model.Visibility

interface AiService {
    suspend fun generateSummary(date: String, materials: List<TimelineEntry>): Result<Summary>
    suspend fun generateSocialCopies(summary: Summary): Result<List<SocialCopy>>
    suspend fun regenerateCopy(platform: SocialPlatform, summary: Summary): Result<SocialCopy>
    suspend fun publishSummary(date: String, summary: Summary, visibility: Visibility): Result<Boolean>
}
