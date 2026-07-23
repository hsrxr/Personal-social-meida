"""M10 社交文案生成器——三风格 Prompt 变体 + 用户反馈学习。

为同一份每日总结生成三种平台风格的社交文案：
- 朋友圈 (wechat_moments)：口语化、亲切
- 小红书 (xiaohongshu)：种草/分享、emoji、标签
- Instagram：视觉导向、短句、hashtag
"""

from __future__ import annotations

import json
import logging
import time
import uuid
from collections import Counter
from typing import Optional

from common.config import get_settings
from common.llm_client import LLMClient
from common.models import DailySummaryResult, Platform, SocialCopy, SocialCopyResponse

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────
# 平台 Prompt 模板
# ──────────────────────────────────────────────

PLATFORM_PROMPTS = {
    Platform.WECHAT_MOMENTS: """你是朋友圈文案助手。基于用户的每日总结，生成朋友圈风格的分享文案。

要点：
- 口语化、亲切，像和朋友聊天
- 2-3句话即可，不要太长
- 适当使用1-2个emoji，不要堆砌
- 重点放在个人感受和趣事上
- 不要使用话题标签 #

仅输出 JSON：
{
  "text": "朋友圈文案",
  "hashtags": []
}""",

    Platform.XIAOHONGSHU: """你是小红书文案助手。基于用户的每日总结，生成小红书笔记风格文案。

要点：
- 种草/分享语气，活泼积极
- 分点式列出（如适用），结构清晰
- emoji丰富但适度（每行1-2个）
- 包含2-3个话题标签 #
- 重点放在场景氛围和生活美学上

仅输出 JSON：
{
  "text": "小红书文案",
  "hashtags": ["#标签1", "#标签2", "#标签3"]
}""",

    Platform.INSTAGRAM: """你是Instagram文案助手。基于用户的每日总结，生成Instagram风格文案。

要点：
- 视觉导向，短句+氛围感
- 可以用英文短语穿插
- 包含3-5个hashtag
- 重点放在moment和vibe上
- 用简短有力的句子表达，每句不超过一行

仅输出 JSON：
{
  "text": "Instagram文案",
  "hashtags": ["#hashtag1", "#hashtag2", "#hashtag3"]
}""",
}


class CopyGenerator:
    """社交文案生成器。"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()
        self.settings = get_settings()

    async def generate(
        self, summary: DailySummaryResult, platform: Platform
    ) -> SocialCopy:
        """为给定总结生成单个平台的文案。"""
        system_prompt = PLATFORM_PROMPTS.get(platform, PLATFORM_PROMPTS[Platform.WECHAT_MOMENTS])

        user_msg = f"""今日总结：
关键词：{', '.join(summary.keywords)}
情绪：{summary.mood}
摘要：{summary.summary}
高光时刻：{summary.highlight}"""

        try:
            result_text = await self.llm.complete(
                system=system_prompt,
                user=user_msg,
                max_tokens=400,
                temperature=0.8,
                response_format="json",
            )
            data = json.loads(result_text)
        except Exception:
            logger.exception("Copy generation failed for platform=%s", platform)
            data = {"text": summary.summary or "今天也是充实的一天。", "hashtags": []}

        return SocialCopy(
            copy_id=str(uuid.uuid4()),
            platform=platform,
            text=data.get("text", ""),
            hashtags=data.get("hashtags", []),
        )

    async def generate_all(self, summary: DailySummaryResult) -> SocialCopyResponse:
        """为一份总结生成全部三种风格的文案。"""
        copies = []
        for platform in Platform:
            copy = await self.generate(summary, platform)
            copies.append(copy)

        return SocialCopyResponse(
            user_id=summary.user_id,
            date=summary.date,
            copies=copies,
        )


class FeedbackLearner:
    """用户行为反馈学习器——记录用户操作，优化后续生成。

    生产环境应持久化到数据库。
    """

    def __init__(self):
        self.events: list[dict] = []

    def record(self, user_id: str, platform: str, action: str, copy_id: str):
        """记录用户行为。

        Args:
            action: 'copied' | 'edited' | 'regenerated' | 'ignored'
        """
        self.events.append({
            "user_id": user_id,
            "platform": platform,
            "action": action,
            "copy_id": copy_id,
            "timestamp": time.time(),
        })
        logger.debug(
            "Feedback recorded: user=%s platform=%s action=%s",
            user_id, platform, action,
        )

    def get_user_preference(self, user_id: str) -> dict:
        """分析用户偏好 → 可用于调整 Prompt 参数。"""
        user_events = [e for e in self.events if e["user_id"] == user_id]

        platform_counts = Counter(
            e["platform"] for e in user_events if e["action"] == "copied"
        )
        preferred = platform_counts.most_common(1)
        preferred_platform = preferred[0][0] if preferred else Platform.WECHAT_MOMENTS.value

        # 计算各平台复制率
        total_by_platform = Counter(e["platform"] for e in user_events)
        copy_rates = {}
        for plat, count in platform_counts.items():
            total = total_by_platform.get(plat, 1)
            copy_rates[plat] = count / total if total else 0.0

        return {
            "preferred_platform": preferred_platform,
            "copy_rates": copy_rates,
            "total_events": len(user_events),
        }
