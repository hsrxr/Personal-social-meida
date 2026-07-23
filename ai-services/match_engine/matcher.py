"""M11 匹配引擎——向量化 + FAISS 搜索 + 破冰语 + 冷启动。

核心流程：
1. 每日 00:00 → compute_daily_embeddings() 计算所有用户日志向量
2. 用户请求匹配 → find_matches() 在当日索引中搜索 TopK
3. 生成破冰语 → 基于共同细节的简短问候
4. 用户不足 → 冷启动降级策略
"""

from __future__ import annotations

import json
import logging
from datetime import date, datetime
from typing import Optional

import numpy as np

from common.config import get_settings
from common.llm_client import LLMClient
from common.models import (
    DailyJournal,
    MatchDetail,
    MatchResponse,
    MatchResult,
    TimelineEntry,
)

logger = logging.getLogger(__name__)


class Embedder:
    """Sentence-Transformer 向量化封装。

    懒加载模型，首次使用时初始化。
    """

    def __init__(self, model_name: Optional[str] = None):
        self.settings = get_settings()
        self.model_name = model_name or self.settings.embedding_model
        self._model = None

    @property
    def model(self):
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            logger.info("Loading embedding model: %s", self.model_name)
            self._model = SentenceTransformer(self.model_name)
        return self._model

    def encode(self, texts: list[str], normalize: bool = True) -> np.ndarray:
        """批量编码文本为向量。"""
        embeddings = self.model.encode(
            texts,
            normalize_embeddings=normalize,
            show_progress_bar=False,
        )
        return np.array(embeddings, dtype=np.float32)


class MatchEngine:
    """匹配引擎主类。"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.settings = get_settings()
        self.llm = llm_client or LLMClient()
        self.embedder = Embedder()
        self._index_cache: dict[str, "faiss.IndexFlatIP"] = {}
        self._user_index_map: dict[str, list[str]] = {}
        self._journal_cache: dict[str, DailyJournal] = {}

    # ──────────────────────────────────────
    # 每日批量向量化
    # ──────────────────────────────────────

    async def compute_daily_embeddings(self, journals: list[DailyJournal]):
        """对所有用户的当日日志向量化并构建 FAISS 索引。

        Args:
            journals: 当日所有用户的 DailyJournal 列表（从 Agent-C 拉取）
        """
        if not journals:
            logger.warning("No journals to compute embeddings")
            return

        date_key = str(journals[0].date)

        texts = []
        user_ids = []
        for j in journals:
            text = self._journal_to_text(j)
            texts.append(text)
            user_ids.append(j.user_id)
            self._journal_cache[j.user_id] = j

        embeddings = self.embedder.encode(texts, normalize=True)

        # 构建 FAISS 索引（内积相似度 = 余弦相似度因为已归一化）
        import faiss
        dim = embeddings.shape[1]
        index = faiss.IndexFlatIP(dim)
        index.add(embeddings)

        self._index_cache[date_key] = index
        self._user_index_map[date_key] = user_ids

        logger.info(
            "Computed daily embeddings: date=%s users=%d dim=%d",
            date_key, len(user_ids), dim,
        )

    # ──────────────────────────────────────
    # 匹配搜索
    # ──────────────────────────────────────

    async def find_matches(
        self, user_id: str, target_date: date, top_k: Optional[int] = None
    ) -> MatchResponse:
        """为用户找到最佳匹配。

        Args:
            user_id: 查询用户 ID
            target_date: 匹配日期
            top_k: 返回匹配数（默认使用配置值）
        """
        top_k = top_k or self.settings.match_top_k
        date_key = str(target_date)

        # 冷启动检查
        active_count = len(self._user_index_map.get(date_key, []))
        if active_count < self.settings.match_cold_start_threshold:
            return await self._cold_start_fallback(user_id, target_date, top_k, active_count)

        if date_key not in self._index_cache:
            logger.warning("No embeddings for date=%s, triggering cold start", date_key)
            return await self._cold_start_fallback(user_id, target_date, top_k, 0)

        index = self._index_cache[date_key]
        user_ids = self._user_index_map[date_key]

        # 查找用户自身的向量
        try:
            user_position = user_ids.index(user_id)
        except ValueError:
            logger.warning("User %s not found in daily index for %s", user_id, date_key)
            return MatchResponse(user_id=user_id, date=target_date, matches=[])

        user_vec = index.reconstruct(user_position).reshape(1, -1)

        import faiss
        distances, indices = index.search(user_vec, top_k + 1)  # +1 排除自己

        matches = []
        ice_break_gen = IceBreakGenerator(self.llm)

        for dist, idx in zip(distances[0], indices[0]):
            matched_user = user_ids[idx]
            if matched_user == user_id:
                continue

            common_details = await self._extract_common_details(user_id, matched_user, target_date)
            ice_break = await ice_break_gen.generate(common_details)

            matches.append(MatchResult(
                matched_user_id=matched_user,
                similarity=float(dist),
                common_details=common_details,
                ice_break=ice_break,
            ))

            if len(matches) >= top_k:
                break

        return MatchResponse(user_id=user_id, date=target_date, matches=matches)

    # ──────────────────────────────────────
    # 共同细节提取
    # ──────────────────────────────────────

    async def _extract_common_details(
        self, user_a: str, user_b: str, target_date: date
    ) -> list[MatchDetail]:
        """提取两个用户的共同细节（用于匹配卡片展示）。"""
        journal_a = self._journal_cache.get(user_a)
        journal_b = self._journal_cache.get(user_b)

        if not journal_a or not journal_b:
            return []

        common = []

        # 共同地点
        a_locations = set(journal_a.location_names)
        b_locations = set(journal_b.location_names)
        for loc in a_locations & b_locations:
            if loc:
                common.append(MatchDetail(type="location", value=loc))

        # 共同情绪
        if journal_a.mood and journal_b.mood and journal_a.mood == journal_b.mood:
            common.append(MatchDetail(type="mood", value=journal_a.mood.value))

        # 共同标签
        shared_tags = set(journal_a.tags) & set(journal_b.tags)
        for tag in shared_tags:
            if tag:
                common.append(MatchDetail(type="tag", value=tag))

        # 共同活动
        shared_activities = set(journal_a.activities) & set(journal_b.activities)
        for act in shared_activities:
            if act:
                common.append(MatchDetail(type="activity", value=act))

        return common

    # ──────────────────────────────────────
    # 冷启动策略
    # ──────────────────────────────────────

    async def _cold_start_fallback(
        self, user_id: str, target_date: date, top_k: int, active_count: int
    ) -> MatchResponse:
        """冷启动降级策略。"""
        if active_count < 10:
            # 极端冷启动：返回"城市共感"而非具体匹配
            journal = self._journal_cache.get(user_id)
            city_vibe = ""
            if journal and journal.location_names:
                city_vibe = f"今天有 {active_count} 个人也在{journal.location_names[0]}附近"
                if journal.tags:
                    city_vibe += f"，他们都提到了{', '.join(journal.tags[:3])}"

            return MatchResponse(
                user_id=user_id,
                date=target_date,
                matches=[MatchResult(
                    matched_user_id=None,
                    type="city_vibe",
                    similarity=0.0,
                    ice_break=city_vibe or "城市里还有其他人也在记录今天。",
                )],
            )

        # 用户数在 10-100 之间：扩大时间窗口
        logger.info("Cold start: expanding window for user=%s (active=%d)", user_id, active_count)
        # 简化实现：返回随机匹配 + 标记
        return MatchResponse(
            user_id=user_id,
            date=target_date,
            matches=[MatchResult(
                matched_user_id=None,
                type="city_vibe",
                similarity=0.0,
                ice_break="正在为你寻找同频的人...",
            )],
        )

    # ──────────────────────────────────────
    # 工具方法
    # ──────────────────────────────────────

    @staticmethod
    def _journal_to_text(journal: DailyJournal) -> str:
        """将日志转为可用于向量化的文本。"""
        parts = [
            f"地点:{' '.join(journal.location_names)}" if journal.location_names else "",
            f"活动:{' '.join(journal.activities)}" if journal.activities else "",
            f"情绪:{journal.mood.value}" if journal.mood else "",
            f"标签:{' '.join(journal.tags)}" if journal.tags else "",
            f"总结:{journal.summary}" if journal.summary else "",
        ]
        return " ".join(filter(None, parts))

    def clear_cache(self):
        """清理索引缓存（内存管理）。"""
        self._index_cache.clear()
        self._user_index_map.clear()
        self._journal_cache.clear()


class IceBreakGenerator:
    """破冰语生成器。"""

    PROMPT = """基于两个用户的共同点，生成一条友好的破冰问候。
要求：15字以内，自然不刻意，像一个朋友在聊天。
不要使用"你好"、"哈喽"等过于正式的问候。"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()

    async def generate(self, common_details: list[MatchDetail]) -> str:
        """基于共同细节生成破冰语。"""
        if not common_details:
            return "今天好像有一些共同的经历。"

        details_text = json.dumps(
            [{"type": d.type, "value": d.value} for d in common_details],
            ensure_ascii=False,
        )

        try:
            result = await self.llm.complete(
                system=self.PROMPT,
                user=f"共同点：{details_text}",
                max_tokens=30,
                temperature=0.8,
            )
            return result.strip()
        except Exception:
            logger.exception("Ice break generation failed")
            # 硬编码降级方案
            first = common_details[0]
            if first.type == "location":
                return f"你也去了{first.value}？"
            elif first.type == "mood":
                return f"今天心情也是{first.value}～"
            elif first.type == "tag":
                return f"你也喜欢{first.value}？"
            elif first.type == "activity":
                return f"今天也{first.value}了吗？"
            return "今天好像有一些共同的经历。"
