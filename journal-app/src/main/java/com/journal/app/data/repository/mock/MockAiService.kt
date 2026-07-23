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
                keywords = listOf("咖啡", "望京", "桂花", "平静", "工作"),
                narrative = "今天又是平凡但有趣的一天。上午在望京SOHO处理工作，中午路过楼下的咖啡店，闻到了今年第一缕桂花香。下午在公园跑步，晚上和朋友约了顿火锅。虽然很累但很充实。",
                mood = "平静",
                highlight = "在楼下咖啡店闻到了今年第一缕桂花香",
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
                    text = "今天的快乐是桂花味儿的 🍂\n在楼下闻到了今年第一缕桂花香，咖啡店老板说他也闻到了。秋天真的来了。",
                    suggestedPhotoIds = listOf("entry-1"),
                ),
                SocialCopy(
                    id = "copy-xiaohongshu",
                    platform = SocialPlatform.XIAOHONGSHU,
                    text = "🍂 今日份小确幸：第一缕桂花香！\n\n路过楼下咖啡店，突然闻到熟悉的桂花香～\n今年秋天比往年来得早一点？\n桂花拿铁意外好喝，安利给所有人！\n\n#桂花 #秋天 #咖啡店日常 #望京生活",
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
                text = "尝一口秋天 🍂 桂花拿铁配桂花香，秋天的小仪式感～ #日常 #桂花",
            )
        )
    }
}
