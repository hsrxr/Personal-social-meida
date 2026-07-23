"""对话摘要发布——对话结束后，将结构化摘要发布到消息队列。

此模块是 agent-dialog 与 Agent-C 数据管线的桥梁：
对话结束 → 摘要 JSON → Redis Stream "ai.dialog.summary"
→ Agent-C 消费 → 写入 DailyJournal
"""

from __future__ import annotations

import logging
from typing import Optional

from common.models import DialogSummary
from common.redis_client import RedisStreamClient

logger = logging.getLogger(__name__)


class DialogSummarizer:
    """发布对话摘要到消息队列。"""

    def __init__(self, redis_client: Optional[RedisStreamClient] = None):
        self.redis = redis_client or RedisStreamClient()

    async def publish(self, user_id: str, summary: DialogSummary) -> str:
        """发布对话摘要到 Redis Stream。

        Returns:
            消息 ID
        """
        msg_id = await self.redis.publish("ai.dialog.summary", {
            "user_id": user_id,
            "location": summary.location,
            "activity": summary.activity,
            "sensory": summary.sensory,
            "mood": summary.mood,
            "people": summary.people,
            "highlights": summary.highlights,
            "raw_summary": summary.raw_summary,
        })

        logger.info(
            "Dialog summary published: user=%s msg_id=%s location=%s mood=%s",
            user_id, msg_id, summary.location, summary.mood,
        )
        return msg_id
