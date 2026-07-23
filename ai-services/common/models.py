"""共享数据模型（Pydantic）——对齐 Agent-C 的 JSON 格式。

所有跨服务传递的数据结构统一在此定义，确保序列化/反序列化一致。
"""

from __future__ import annotations

from datetime import date, datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


# ──────────────────────────────────────────────
# 枚举
# ──────────────────────────────────────────────

class Mood(str, Enum):
    HAPPY = "开心"
    CALM = "平静"
    IRRITATED = "烦躁"
    EXCITED = "兴奋"
    TIRED = "疲惫"
    NEUTRAL = "中性"


class Activity(str, Enum):
    COFFEE = "咖啡"
    WORK = "工作"
    SPORT = "运动"
    COMMUTE = "通勤"
    SOCIAL = "社交"
    SHOPPING = "购物"
    DINING = "餐饮"
    READING = "阅读"
    OUTDOOR = "户外"
    MUSIC = "音乐"
    TRAVEL = "旅行"
    HOME = "居家"
    OTHER = "其他"


class Platform(str, Enum):
    WECHAT_MOMENTS = "wechat_moments"
    XIAOHONGSHU = "xiaohongshu"
    INSTAGRAM = "instagram"


class DialogState(str, Enum):
    IDLE = "idle"
    LISTENING = "listening"
    THINKING = "thinking"
    SPEAKING = "speaking"
    SUMMARIZING = "summarizing"


# ──────────────────────────────────────────────
# 素材/日志
# ──────────────────────────────────────────────

class TimelineEntry(BaseModel):
    """时间线条目——对应 Agent-C 的 TimelineEntryEntity。"""
    entry_id: str
    user_id: str
    timestamp: datetime
    entry_type: str  # "photo" | "audio" | "location" | "dialog_summary" | "note"
    content_ref: str  # OSS object key 或文本内容
    thumbnail_url: Optional[str] = None
    location_name: Optional[str] = None
    location_lat: Optional[float] = None
    location_lng: Optional[float] = None


class ImageAnnotation(BaseModel):
    """图片分析结果。"""
    scene: str = ""
    objects: list[str] = Field(default_factory=list)
    activity: str = ""
    indoor: bool = True
    quality_score: int = 5          # 1-10
    is_social_worthy: bool = False


class EntryAnnotation(BaseModel):
    """条目标注信息。"""
    entry_id: str
    tags: list[str] = Field(default_factory=list)
    mood: Optional[Mood] = None
    activity: Optional[Activity] = None
    image_annotation: Optional[ImageAnnotation] = None
    sentiment_confidence: float = 0.0


# ──────────────────────────────────────────────
# 每日日志
# ──────────────────────────────────────────────

class DailyJournal(BaseModel):
    """每日日志——对应 Agent-C 的 DailyJournalEntity。"""
    journal_id: str
    user_id: str
    date: date
    entries: list[TimelineEntry] = Field(default_factory=list)
    annotations: list[EntryAnnotation] = Field(default_factory=list)
    location_names: list[str] = Field(default_factory=list)
    activities: list[str] = Field(default_factory=list)
    mood: Optional[Mood] = None
    tags: list[str] = Field(default_factory=list)
    summary: Optional[str] = None
    keywords: list[str] = Field(default_factory=list)
    highlight: Optional[str] = None


# ──────────────────────────────────────────────
# Agent 对话
# ──────────────────────────────────────────────

class DialogSummary(BaseModel):
    """对话结束后的结构化摘要。"""
    location: str = ""
    activity: str = ""
    sensory: str = ""
    mood: str = ""
    people: list[str] = Field(default_factory=list)
    highlights: str = ""
    raw_summary: str = ""


class AgentResponse(BaseModel):
    """Agent 对话回复。"""
    text: str
    audio_url: Optional[str] = None
    is_final: bool = False
    summary: Optional[DialogSummary] = None


# ──────────────────────────────────────────────
# 每日总结
# ──────────────────────────────────────────────

class DailySummaryResult(BaseModel):
    """每日总结生成结果。"""
    user_id: str
    date: date
    keywords: list[str] = Field(default_factory=list)
    summary: str = ""
    mood: str = ""
    highlight: str = ""


# ──────────────────────────────────────────────
# 社交文案
# ──────────────────────────────────────────────

class SocialCopyRequest(BaseModel):
    """社交文案生成请求。"""
    user_id: str
    date: date
    platform: Platform
    summary: DailySummaryResult
    image_urls: list[str] = Field(default_factory=list)


class SocialCopy(BaseModel):
    """生成的社交文案。"""
    copy_id: str
    platform: Platform
    text: str
    hashtags: list[str] = Field(default_factory=list)
    recommended_image_index: int = 0  # 推荐配图在 image_urls 中的索引


class SocialCopyResponse(BaseModel):
    """社交文案生成响应。"""
    user_id: str
    date: date
    copies: list[SocialCopy] = Field(default_factory=list)


# ──────────────────────────────────────────────
# 匹配
# ──────────────────────────────────────────────

class MatchDetail(BaseModel):
    """共同细节——用于匹配卡片展示。"""
    type: str   # "location" | "mood" | "tag" | "activity"
    value: str


class MatchResult(BaseModel):
    """单个匹配结果。"""
    matched_user_id: Optional[str] = None
    similarity: float = 0.0
    common_details: list[MatchDetail] = Field(default_factory=list)
    ice_break: str = ""
    # 冷启动模式下无具体用户
    type: str = "match"  # "match" | "city_vibe"


class MatchResponse(BaseModel):
    """匹配推荐响应。"""
    user_id: str
    date: date
    matches: list[MatchResult] = Field(default_factory=list)


# ──────────────────────────────────────────────
# 消息队列消息
# ──────────────────────────────────────────────

class StreamMessage(BaseModel):
    """Redis Stream 消息通用格式。"""
    stream: str
    message_id: str
    data: dict
