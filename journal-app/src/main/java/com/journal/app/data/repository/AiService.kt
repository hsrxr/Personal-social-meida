package com.journal.app.data.repository

import com.journal.app.data.model.SocialCopy
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Summary

interface AiService {
    suspend fun generateSummary(date: String): Result<Summary>
    suspend fun generateSocialCopies(summary: Summary): Result<List<SocialCopy>>
    suspend fun regenerateCopy(platform: SocialPlatform, summary: Summary): Result<SocialCopy>
}
