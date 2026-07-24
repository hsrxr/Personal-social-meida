package com.journal.app.data.repository.mock

import com.journal.app.data.model.SocialCopy
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Summary
import com.journal.app.data.repository.AiService
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAiService @Inject constructor() : AiService {

    override suspend fun generateSummary(date: String): Result<Summary> {
        delay(1500) // simulate AI latency
        return Result.success(
            Summary(
                keywords = listOf("coffee", "wangjing", "osmanthus", "calm", "work"),
                narrative = "Another ordinary but interesting day. Spent the morning at Wangjing SOHO handling work, then passed the coffee shop downstairs at lunch and caught the first whiff of osmanthus this year. Went for a run in the park in the afternoon, then met friends for hot pot in the evening. Exhausting but fulfilling.",
                mood = "Calm",
                highlight = "Caught the first scent of osmanthus this year at the coffee shop downstairs",
            )
        )
    }

    override suspend fun generateSocialCopies(summary: Summary): Result<List<SocialCopy>> {
        delay(2000)
        return Result.success(
            listOf(
                SocialCopy(
                    id = "copy-wechat",
                    platform = SocialPlatform.WECHAT_MOMENTS,
                    text = "Today's happiness smells like osmanthus 🍂\nCaught the first whiff of osmanthus this year at the coffee shop downstairs — the owner said he smelled it too. Autumn is really here.",
                    suggestedPhotoIds = listOf("entry-1"),
                ),
                SocialCopy(
                    id = "copy-xiaohongshu",
                    platform = SocialPlatform.XIAOHONGSHU,
                    text = "🍂 Today's little joy: first osmanthus of the year!\n\nWalked past the coffee shop downstairs and suddenly caught that familiar osmanthus scent~\nDid autumn arrive earlier than usual this year?\nThe osmanthus latte was surprisingly good — highly recommend!\n\n#osmanthus #autumn #coffeeshop #wangjinglife",
                    suggestedPhotoIds = listOf("entry-1", "entry-3"),
                ),
                SocialCopy(
                    id = "copy-instagram",
                    platform = SocialPlatform.INSTAGRAM,
                    text = "first osmanthus of the year 🍂\nautumn has arrived on this street corner\n#osmanthus #coffeeshopvibes #autumnmood #wangjinglife",
                    suggestedPhotoIds = listOf("entry-1"),
                ),
            )
        )
    }

    override suspend fun regenerateCopy(platform: SocialPlatform, summary: Summary): Result<SocialCopy> {
        delay(2000)
        return Result.success(
            SocialCopy(
                id = "copy-${platform.name}-v2",
                platform = platform,
                text = "A taste of autumn 🍂 Osmanthus latte + osmanthus breeze — autumn's little ritual~ #daily #osmanthus",
            )
        )
    }
}
