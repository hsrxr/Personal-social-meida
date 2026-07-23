"""AI Worker——消费 Agent-C 消息队列，处理后发布结果。

持续运行的消息队列消费者，覆盖四个 AI 能力：
- ai.summary.request → 每日总结
- ai.social_copy.request → 社交文案
- ai.match.request → 匹配推荐
- ai.vision_tag.request → 视觉标注
"""

from __future__ import annotations

import asyncio
import json
import logging

from common.config import get_settings
from common.models import (
    DailyJournal,
    DailySummaryResult,
    Platform,
    TimelineEntry,
)
from common.redis_client import RedisStreamClient
from journal_summary.summarizer import JournalSummarizer
from social_copy.copy_generator import CopyGenerator
from match_engine.matcher import MatchEngine
from vision_tagger.taggers import ImageAnalyzer, SentimentAnalyzer, ActivityClassifier

logger = logging.getLogger(__name__)


class AiWorker:
    """AI Worker——统一消费消息队列，调度所有 AI 能力。"""

    def __init__(self):
        self.settings = get_settings()
        self.redis = RedisStreamClient(self.settings)

        # 所有子模块
        self.summarizer = JournalSummarizer(redis_client=self.redis)
        self.copy_generator = CopyGenerator()
        self.match_engine = MatchEngine()
        self.image_analyzer = ImageAnalyzer()
        self.sentiment_analyzer = SentimentAnalyzer()
        self.activity_classifier = ActivityClassifier()

    async def start(self):
        """启动 Worker，持续消费消息队列。"""
        await self.redis.connect()
        logger.info("AI Worker started, consuming streams")

        streams = [
            "ai.summary.request",
            "ai.social_copy.request",
            "ai.match.request",
            "ai.vision_tag.request",
        ]

        async for msg in self.redis.consume(
            group="ai-workers",
            consumer="worker-1",
            streams=streams,
        ):
            try:
                await self._dispatch(msg.stream, msg.data)
            except Exception:
                logger.exception("Failed to process message: stream=%s id=%s",
                                 msg.stream, msg.message_id)

    async def _dispatch(self, stream: str, data: dict):
        """根据 stream 名称分发到对应处理器。"""
        if stream == "ai.summary.request":
            await self._handle_summary_request(data)
        elif stream == "ai.social_copy.request":
            await self._handle_copy_request(data)
        elif stream == "ai.match.request":
            await self._handle_match_request(data)
        elif stream == "ai.vision_tag.request":
            await self._handle_vision_tag_request(data)

    # ──────────────────────────────────────
    # 每日总结
    # ──────────────────────────────────────

    async def _handle_summary_request(self, data: dict):
        user_id = data["user_id"]
        date_str = data.get("date", "")

        entries_data = data.get("entries", [])
        if isinstance(entries_data, str):
            entries_data = json.loads(entries_data)

        entries = [
            TimelineEntry(
                entry_id=e.get("entry_id", f"{user_id}_{i}"),
                user_id=user_id,
                timestamp=e.get("timestamp", ""),
                entry_type=e.get("entry_type", "note"),
                content_ref=e.get("content_ref", ""),
                location_name=e.get("location_name"),
            )
            for i, e in enumerate(entries_data)
        ]

        journal = DailyJournal(
            journal_id=f"{user_id}_{date_str}",
            user_id=user_id,
            date=date_str,
            entries=entries,
        )

        result = await self.summarizer.generate_and_publish(journal)
        logger.info("Summary: user=%s mood=%s", user_id, result.mood)

    # ──────────────────────────────────────
    # 社交文案
    # ──────────────────────────────────────

    async def _handle_copy_request(self, data: dict):
        user_id = data["user_id"]
        date_str = data.get("date", "")
        platform_str = data.get("platform", "wechat_moments")

        summary_data = data.get("summary", {})
        if isinstance(summary_data, str):
            summary_data = json.loads(summary_data)

        summary = DailySummaryResult(
            user_id=user_id,
            date=date_str,
            keywords=summary_data.get("keywords", []),
            summary=summary_data.get("summary", ""),
            mood=summary_data.get("mood", ""),
            highlight=summary_data.get("highlight", ""),
        )

        platform = Platform(platform_str)
        copy = await self.copy_generator.generate(summary, platform)

        await self.redis.publish("ai.social_copy.response", {
            "user_id": user_id,
            "date": date_str,
            "copy_id": copy.copy_id,
            "platform": copy.platform.value,
            "text": copy.text,
            "hashtags": copy.hashtags,
        })

        logger.info("Social copy: user=%s platform=%s", user_id, platform_str)

    # ──────────────────────────────────────
    # 匹配推荐
    # ──────────────────────────────────────

    async def _handle_match_request(self, data: dict):
        user_id = data["user_id"]
        date_str = data.get("date", "")

        from datetime import date as date_type
        match_date = date_type.fromisoformat(date_str) if date_str else date_type.today()

        response = await self.match_engine.find_matches(user_id, match_date)

        await self.redis.publish("ai.match.response", {
            "user_id": response.user_id,
            "date": str(response.date),
            "matches": json.dumps(
                [m.model_dump() for m in response.matches],
                ensure_ascii=False,
            ),
        })

        logger.info("Match: user=%s matches=%d", user_id, len(response.matches))

    # ──────────────────────────────────────
    # 视觉标注
    # ──────────────────────────────────────

    async def _handle_vision_tag_request(self, data: dict):
        entry_id = data["entry_id"]
        image_url = data.get("image_url", "")
        transcription = data.get("transcription", "")
        location_name = data.get("location_name", "")

        annotation = await self.image_analyzer.analyze(image_url) if image_url else None

        sentiment = await self.sentiment_analyzer.analyze(transcription)

        activity = await self.activity_classifier.classify(
            image_annotation=annotation,
            transcription=transcription,
            location_name=location_name,
        )

        result = {
            "entry_id": entry_id,
            "scene": annotation.scene if annotation else "",
            "objects": annotation.objects if annotation else [],
            "activity": activity,
            "mood": sentiment["mood"],
            "sentiment_confidence": sentiment["confidence"],
            "quality_score": annotation.quality_score if annotation else 0,
            "is_social_worthy": annotation.is_social_worthy if annotation else False,
        }

        await self.redis.publish("ai.vision_tag.response", result)
        logger.info("Vision tag: entry=%s activity=%s", entry_id, activity)


async def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    worker = AiWorker()
    await worker.start()


if __name__ == "__main__":
    asyncio.run(main())
