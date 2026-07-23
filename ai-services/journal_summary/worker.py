"""每日总结 Worker——持续消费消息队列，生成总结后发布结果。"""

from __future__ import annotations

import asyncio
import json
import logging

from common.config import get_settings
from common.models import DailyJournal, TimelineEntry
from common.redis_client import RedisStreamClient
from journal_summary.summarizer import JournalSummarizer

logger = logging.getLogger(__name__)


async def main():
    settings = get_settings()
    redis = RedisStreamClient(settings)
    await redis.connect()

    summarizer = JournalSummarizer(redis_client=redis)

    logger.info("Journal Summary Worker started, consuming ai.summary.request")

    async for msg in redis.consume(
        group="ai-summary-workers",
        consumer="worker-1",
        streams=["ai.summary.request"],
    ):
        try:
            data = msg.data
            user_id = data["user_id"]
            date_str = data.get("date", "")

            # 从消息中重建 DailyJournal（生产环境应从 Agent-C API 拉取完整数据）
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

            result = await summarizer.generate_and_publish(journal)
            logger.info("Summary done: user=%s date=%s", user_id, date_str)

        except Exception:
            logger.exception("Failed to process summary request: %s", msg.message_id)


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    asyncio.run(main())
