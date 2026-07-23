"""Redis Streams 消费/发布封装。

使用方式：
    from common.redis_client import RedisStreamClient
    client = RedisStreamClient()
    await client.publish("ai.summary.response", {"user_id": "u1", "date": "2026-07-23"})
    async for msg in client.consume("ai-worker", "worker-1", ["ai.summary.request"]):
        process(msg)
"""

from __future__ import annotations

import json
import logging
from typing import AsyncIterator, Optional

import redis.asyncio as aioredis

from common.config import get_settings
from common.models import StreamMessage

logger = logging.getLogger(__name__)


class RedisStreamClient:
    """Redis Streams 异步客户端封装。

    特性：
    - 消费者组模式：支持多 worker 并行消费
    - 自动 ACK
    - JSON 序列化消息体
    - 连接池复用
    """

    def __init__(self, settings=None):
        self.settings = settings or get_settings()
        self._redis: Optional[aioredis.Redis] = None

    async def connect(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(
                self.settings.redis_url,
                decode_responses=True,
                max_connections=20,
            )
            await self._redis.ping()
            logger.info("Redis connected: %s", self.settings.redis_url)
        return self._redis

    async def close(self):
        if self._redis:
            await self._redis.close()
            self._redis = None

    @property
    def redis(self) -> aioredis.Redis:
        if self._redis is None:
            raise RuntimeError("Redis not connected. Call await connect() first.")
        return self._redis

    # ──────────────────────────────────────
    # 发布
    # ──────────────────────────────────────

    async def publish(self, stream: str, data: dict, maxlen: Optional[int] = None) -> str:
        """向 Stream 发布一条消息。返回消息 ID。

        Args:
            stream: stream key，如 "ai.summary.response"
            data: 消息体字典（自动 JSON 序列化）
            maxlen: 最大长度（默认使用配置值）
        """
        if maxlen is None:
            maxlen = self.settings.redis_stream_maxlen

        # 将复杂类型转为 JSON 字符串
        serialized = {}
        for k, v in data.items():
            if isinstance(v, (dict, list)):
                serialized[k] = json.dumps(v, ensure_ascii=False)
            else:
                serialized[k] = str(v) if not isinstance(v, str) else v

        msg_id = await self.redis.xadd(stream, serialized, maxlen=maxlen)
        logger.debug("Published to %s: %s", stream, msg_id)
        return msg_id

    # ──────────────────────────────────────
    # 消费
    # ──────────────────────────────────────

    async def ensure_consumer_group(
        self, stream: str, group: str, start_id: str = "0"
    ):
        """确保消费者组存在，不存在则创建。"""
        try:
            await self.redis.xgroup_create(stream, group, id=start_id, mkstream=True)
            logger.info("Created consumer group %s for stream %s", group, stream)
        except aioredis.ResponseError as e:
            if "BUSYGROUP" not in str(e):
                raise

    async def consume(
        self,
        group: str,
        consumer: str,
        streams: list[str],
        block_ms: int = 5000,
        count: int = 1,
    ) -> AsyncIterator[StreamMessage]:
        """持续消费消息（生成器）。

        Args:
            group: 消费者组名
            consumer: 消费者名（worker 标识）
            streams: 要消费的 stream key 列表
            block_ms: 阻塞等待毫秒数
            count: 每次读取的最大消息数

        Yields:
            StreamMessage: 结构化消息
        """
        for stream in streams:
            await self.ensure_consumer_group(stream, group)

        stream_keys = {s: ">" for s in streams}

        while True:
            try:
                results = await self.redis.xreadgroup(
                    groupname=group,
                    consumername=consumer,
                    streams=stream_keys,
                    block=block_ms,
                    count=count,
                )
            except aioredis.ConnectionError:
                logger.warning("Redis connection lost, reconnecting...")
                await self.connect()
                continue

            if results is None:
                continue

            for stream_name, messages in results:
                for msg_id, data in messages:
                    # 反序列化 JSON 字段
                    parsed = {}
                    for k, v in data.items():
                        try:
                            parsed[k] = json.loads(v)
                        except (json.JSONDecodeError, TypeError):
                            parsed[k] = v

                    yield StreamMessage(
                        stream=stream_name,
                        message_id=msg_id,
                        data=parsed,
                    )

                    await self.redis.xack(stream_name, group, msg_id)

    async def consume_one(
        self,
        group: str,
        consumer: str,
        stream: str,
        block_ms: int = 5000,
    ) -> Optional[StreamMessage]:
        """消费单条消息（阻塞）。"""
        await self.ensure_consumer_group(stream, group)

        try:
            results = await self.redis.xreadgroup(
                groupname=group,
                consumername=consumer,
                streams={stream: ">"},
                block=block_ms,
                count=1,
            )
        except aioredis.ConnectionError:
            logger.warning("Redis connection lost")
            return None

        if not results:
            return None

        for stream_name, messages in results:
            for msg_id, data in messages:
                parsed = {}
                for k, v in data.items():
                    try:
                        parsed[k] = json.loads(v)
                    except (json.JSONDecodeError, TypeError):
                        parsed[k] = v

                return StreamMessage(
                    stream=stream_name,
                    message_id=msg_id,
                    data=parsed,
                )

        return None
