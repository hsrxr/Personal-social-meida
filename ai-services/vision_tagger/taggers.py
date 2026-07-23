"""M7 视觉/情绪自动标注。

三个核心组件：
1. ImageAnalyzer — GPT-4V/Claude Vision 场景描述 + 社交媒体价值评分
2. SentimentAnalyzer — NLP 情感分析
3. ActivityClassifier — 多模态活动类型分类
"""

from __future__ import annotations

import json
import logging
from typing import Optional

from common.config import get_settings
from common.llm_client import LLMClient
from common.models import Activity, ImageAnnotation, Mood

logger = logging.getLogger(__name__)


class ImageAnalyzer:
    """使用视觉 LLM 分析图片内容。"""

    VISION_PROMPT = """分析这张图片，输出 JSON（仅 JSON，不要 Markdown）：
{
  "scene": "场景描述（如'咖啡店内景'，15字以内）",
  "objects": ["识别到的物体列表，前5个"],
  "activity": "活动类型推断：咖啡/工作/运动/社交/购物/餐饮/阅读/户外/音乐/旅行/居家/其他",
  "indoor": true/false,
  "quality_score": 1-10 (构图质量评分，5分及格),
  "is_social_worthy": true/false (构图和内容是否适合发社交媒体)
}"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()

    async def analyze(self, image_url: str) -> ImageAnnotation:
        """分析单张图片，返回结构化标注。"""
        try:
            result_json = await self.llm.vision(
                image_url=image_url,
                prompt=self.VISION_PROMPT,
                max_tokens=400,
                response_format="json",
            )
            data = json.loads(result_json)
        except Exception:
            logger.exception("Image analysis failed for %s", image_url[:80])
            return ImageAnnotation()

        return ImageAnnotation(
            scene=data.get("scene", ""),
            objects=data.get("objects", []),
            activity=data.get("activity", ""),
            indoor=data.get("indoor", True),
            quality_score=data.get("quality_score", 5),
            is_social_worthy=data.get("is_social_worthy", False),
        )


class SentimentAnalyzer:
    """NLP 情感分析——从文本（如音频转写）推断情绪。"""

    SENTIMENT_PROMPT = """分析以下文本的情绪倾向，输出 JSON（仅 JSON）：
{"mood": "开心/平静/烦躁/兴奋/疲惫/中性", "confidence": 0.0-1.0}"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()

    async def analyze(self, text: str) -> dict:
        """分析文本情绪。"""
        if not text.strip():
            return {"mood": "中性", "confidence": 0.5}

        try:
            result = await self.llm.complete(
                system=self.SENTIMENT_PROMPT,
                user=text,
                max_tokens=100,
                temperature=0.1,
                response_format="json",
            )
            data = json.loads(result)
            return {
                "mood": data.get("mood", "中性"),
                "confidence": float(data.get("confidence", 0.5)),
            }
        except Exception:
            logger.exception("Sentiment analysis failed")
            return {"mood": "中性", "confidence": 0.0}

    @staticmethod
    def to_mood_enum(mood_str: str) -> Mood:
        """将情绪字符串转为 Mood 枚举。"""
        mood_map = {
            "开心": Mood.HAPPY,
            "平静": Mood.CALM,
            "烦躁": Mood.IRRITATED,
            "兴奋": Mood.EXCITED,
            "疲惫": Mood.TIRED,
            "中性": Mood.NEUTRAL,
        }
        return mood_map.get(mood_str, Mood.NEUTRAL)


class ActivityClassifier:
    """多模态活动类型分类。

    综合图片分析、转写文本、位置信息判断用户活动类型。
    """

    VALID_ACTIVITIES = {
        "咖啡", "工作", "运动", "通勤", "社交", "购物",
        "餐饮", "阅读", "户外", "音乐", "旅行", "居家", "其他",
    }

    CLASSIFY_PROMPT = """根据以下信息推断用户正在进行的活动类型，仅返回 JSON：
{"activity": "活动类型", "confidence": 0.0-1.0}

活动类型选项：咖啡/工作/运动/通勤/社交/购物/餐饮/阅读/户外/音乐/旅行/居家/其他"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()

    async def classify(
        self,
        image_annotation: Optional[ImageAnnotation] = None,
        transcription: str = "",
        location_name: str = "",
    ) -> str:
        """综合判断活动类型，返回活动名称。"""
        # 先尝试规则匹配
        rule_result = self._rule_classify(image_annotation, transcription, location_name)
        if rule_result:
            return rule_result

        # 规则不满足时使用 LLM
        context_parts = []
        if image_annotation:
            context_parts.append(f"图片场景：{image_annotation.scene}")
            context_parts.append(f"识别物体：{', '.join(image_annotation.objects)}")
            if image_annotation.activity:
                context_parts.append(f"图片推断活动：{image_annotation.activity}")
        if transcription:
            context_parts.append(f"用户语音转写：{transcription}")
        if location_name:
            context_parts.append(f"地点：{location_name}")

        context = "\n".join(context_parts)

        try:
            result = await self.llm.complete(
                system=self.CLASSIFY_PROMPT,
                user=context,
                max_tokens=100,
                temperature=0.2,
                response_format="json",
            )
            data = json.loads(result)
            activity = data.get("activity", "其他")
            if activity in self.VALID_ACTIVITIES:
                return activity
        except Exception:
            logger.exception("Activity classification failed")

        return "其他"

    def _rule_classify(
        self,
        image_annotation: Optional[ImageAnnotation],
        transcription: str,
        location_name: str,
    ) -> Optional[str]:
        """简单规则匹配——覆盖高频场景，减少 LLM 调用。"""
        # 地点关键词
        location_keywords = {
            "咖啡": "咖啡", "咖啡馆": "咖啡", "咖啡店": "咖啡", "starbucks": "咖啡",
            "餐厅": "餐饮", "饭店": "餐饮", "食堂": "餐饮",
            "健身房": "运动", "gym": "运动", "公园": "户外",
            "公司": "工作", "办公室": "工作",
            "商场": "购物", "mall": "购物",
            "地铁": "通勤", "公交": "通勤", "站": "通勤",
        }

        for keyword, activity in location_keywords.items():
            if keyword in location_name.lower():
                return activity

        # 图片推断的活动
        if image_annotation and image_annotation.activity in self.VALID_ACTIVITIES:
            return image_annotation.activity

        return None
