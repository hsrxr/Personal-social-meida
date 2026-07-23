"""M9 每日总结引擎——Prompt 工程 + LLM 调用。

将用户一天的素材聚合为结构化每日总结：
- 3-5 个关键词
- 100-200 字叙述
- 情绪 + 高光时刻
"""

from __future__ import annotations

import json
import logging
from datetime import date
from typing import Optional

from common.config import get_settings
from common.llm_client import LLMClient
from common.models import DailyJournal, DailySummaryResult
from common.redis_client import RedisStreamClient

logger = logging.getLogger(__name__)

SUMMARY_SYSTEM_PROMPT = """你是个人日志助手。根据用户一天的第一人称素材，生成简洁、温暖的每日总结。

要求：
- 关键词：3-5个，以名词/动词/情绪词为主
- 叙述文字：100-200字，第二人称
- 语气：温暖、平实，不煽情
- 如果有特殊事件（偶遇/新发现/开心时刻），优先提及

Input:
{entries_json}
Output (JSON only):
{{
  "keywords": ["关键词1", "关键词2"],
  "summary": "叙述文字",
  "mood": "情绪",
  "highlight": "高光时刻"
}}"""


class JournalSummarizer:
    """每日总结生成器。

    功能：
    - 单份总结生成
    - 批量处理（每日定时任务）
    - 结果发布到 Redis Stream
    """

    def __init__(self, llm_client: Optional[LLMClient] = None, redis_client: Optional[RedisStreamClient] = None):
        self.llm = llm_client or LLMClient()
        self.redis = redis_client or RedisStreamClient()
        self.settings = get_settings()

    async def generate(self, journal: DailyJournal) -> DailySummaryResult:
        """为一天的日志生成总结。"""
        if len(journal.entries) < self.settings.summary_min_entries:
            logger.info(
                "Skipping summary for user=%s date=%s: only %d entries",
                journal.user_id, journal.date, len(journal.entries),
            )
            return DailySummaryResult(
                user_id=journal.user_id,
                date=journal.date,
                keywords=[],
                summary="今天素材太少，无法生成总结。",
                mood="",
                highlight="",
            )

        entries_json = self._format_entries(journal)
        prompt = SUMMARY_SYSTEM_PROMPT.replace("{entries_json}", entries_json)

        try:
            result_text = await self.llm.complete(
                system=prompt,
                user="请生成今日总结。",
                max_tokens=500,
                temperature=0.7,
                response_format="json",
            )
            data = json.loads(result_text)
        except (json.JSONDecodeError, Exception) as e:
            logger.exception("Summary generation failed for user=%s", journal.user_id)
            return DailySummaryResult(
                user_id=journal.user_id,
                date=journal.date,
                keywords=[],
                summary="",
                mood="",
                highlight="",
            )

        result = DailySummaryResult(
            user_id=journal.user_id,
            date=journal.date,
            keywords=data.get("keywords", []),
            summary=data.get("summary", ""),
            mood=data.get("mood", ""),
            highlight=data.get("highlight", ""),
        )

        logger.info(
            "Generated summary: user=%s date=%s mood=%s keywords=%s",
            journal.user_id, journal.date, result.mood, result.keywords,
        )
        return result

    async def generate_and_publish(self, journal: DailyJournal) -> DailySummaryResult:
        """生成总结并发布到消息队列。"""
        result = await self.generate(journal)

        await self.redis.publish("ai.summary.response", {
            "user_id": result.user_id,
            "date": str(result.date),
            "keywords": result.keywords,
            "summary": result.summary,
            "mood": result.mood,
            "highlight": result.highlight,
        })

        return result

    # ──────────────────────────────────────
    # 内部方法
    # ──────────────────────────────────────

    @staticmethod
    def _format_entries(journal: DailyJournal) -> str:
        """将日志条目格式化为 Prompt 可用的文本。"""
        parts = []
        for entry in journal.entries:
            ts = entry.timestamp.strftime("%H:%M") if entry.timestamp else "??:??"
            line = f"[{ts}] "
            if entry.entry_type == "location" and entry.location_name:
                line += f"在{entry.location_name}"
            elif entry.entry_type == "photo":
                line += "拍摄了照片"
                if entry.thumbnail_url:
                    line += "（已上传）"
            elif entry.entry_type == "audio":
                line += "录制了语音"
            elif entry.entry_type == "dialog_summary":
                line += f"与AI助手对话：{entry.content_ref}"
            else:
                line += entry.content_ref or "(无内容)"
            parts.append(line)

        # 添加标注信息
        if journal.annotations:
            for ann in journal.annotations:
                extras = []
                if ann.mood:
                    extras.append(f"情绪={ann.mood.value}")
                if ann.activity:
                    extras.append(f"活动={ann.activity.value}")
                if ann.tags:
                    extras.append(f"标签={','.join(ann.tags)}")
                if extras:
                    parts.append(f"[标注] {'，'.join(extras)}")

        return "\n".join(parts)
