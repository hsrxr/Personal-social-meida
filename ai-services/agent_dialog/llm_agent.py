"""LLM 对话核心——System Prompt + 上下文管理 + 状态机。

AgentDialogEngine 是 M-Agent 语音对话的核心引擎，负责：
1. 接收用户语音转文本
2. 管理对话上下文（滑动窗口）
3. 检测对话结束信号
4. 生成结构化摘要
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from common.config import get_settings
from common.llm_client import LLMClient
from common.models import AgentResponse, DialogState, DialogSummary

logger = logging.getLogger(__name__)

# 加载 System Prompt 模板
_PROMPT_DIR = Path(__file__).parent / "prompt_templates"
SYSTEM_PROMPT = (_PROMPT_DIR / "agent_system.txt").read_text(encoding="utf-8")

SUMMARY_PROMPT = """从以下对话中提取结构化信息，输出 JSON。

要求：
- location: 推断的地点名（不知道则留空字符串）
- activity: 活动类型（咖啡/工作/运动/通勤/社交/购物/餐饮/阅读/户外/音乐/旅行/居家/其他）
- sensory: 感官细节（味觉/嗅觉/视觉/听觉等描述）
- mood: 推断情绪（开心/平静/烦躁/兴奋/疲惫/中性）
- people: 提及的人名列表
- highlights: 值得记住的瞬间（一句话，无则不写）
- raw_summary: 50字以内自然语言摘要

仅输出 JSON，不要其他文字。"""

END_PHRASES = [
    "好了", "就这样", "记下来", "回头聊", "没了", "说完了",
    "拜拜", "再见", "下次聊", "先这样", "不说了", "嗯好",
]


class AgentDialogEngine:
    """M-Agent 对话状态机。

    ┌──────┐   speech    ┌───────────┐   LLM call   ┌──────────┐
    │ IDLE │ ──────────→ │ LISTENING │ ────────────→ │ THINKING │
    └──────┘             └───────────┘               └──────────┘
       ↑                                                    │
       │   summary done                                     ↓
       │                                              ┌──────────┐
       │                                              │ SPEAKING │
       └── SUMMARIZING ←── end signal ────────────────└──────────┘
    """

    def __init__(self, user_id: str, llm_client: Optional[LLMClient] = None):
        self.user_id = user_id
        self.settings = get_settings()
        self.llm = llm_client or LLMClient()
        self.state = DialogState.IDLE
        self.context: list[dict] = []
        self.turn_count = 0
        self._created_at = datetime.now(timezone.utc)

    # ──────────────────────────────────────
    # 公开 API
    # ──────────────────────────────────────

    async def on_user_speech(self, text: str) -> AgentResponse:
        """处理用户语音转文本输入，返回 Agent 回复。

        这是引擎的主入口。根据当前状态和输入内容决定下一步：
        - 检测到结束信号 → 生成摘要
        - 超出最大轮次 → 强制摘要
        - 正常对话 → LLM 生成回复
        """
        if not text.strip():
            return AgentResponse(text="", is_final=False)

        self.context.append({"role": "user", "content": text})
        self.state = DialogState.THINKING

        # 检查结束条件
        if self._is_end_signal(text) or self.turn_count >= self.settings.dialog_max_turns:
            return await self._summarize()

        # 正常对话回合
        reply = await self._generate_reply()
        self.context.append({"role": "assistant", "content": reply})
        self.turn_count += 1
        self.state = DialogState.SPEAKING

        return AgentResponse(
            text=reply,
            is_final=False,
        )

    async def end_dialog(self) -> AgentResponse:
        """强制结束对话（例如用户摘掉眼镜）。"""
        if self.state == DialogState.SUMMARIZING:
            return AgentResponse(text="", is_final=True)
        if self.state == DialogState.IDLE and len(self.context) == 0:
            return AgentResponse(text="", is_final=True)
        return await self._summarize()

    def reset(self):
        """重置引擎状态。"""
        self.state = DialogState.IDLE
        self.context.clear()
        self.turn_count = 0

    # ──────────────────────────────────────
    # 内部方法
    # ──────────────────────────────────────

    async def _generate_reply(self) -> str:
        """调用 LLM 生成对话回复。"""
        try:
            reply = await self.llm.complete(
                system=SYSTEM_PROMPT,
                messages=self.context[-6:],  # 滑动窗口：最近 6 轮
                max_tokens=self.settings.dialog_max_reply_tokens,
                temperature=0.7,
            )
            return reply.strip()
        except Exception:
            logger.exception("Failed to generate reply for user=%s", self.user_id)
            return "嗯，我在听，继续说。"

    async def _summarize(self) -> AgentResponse:
        """生成对话结构化摘要，重置状态。"""
        self.state = DialogState.SUMMARIZING

        if len(self.context) == 0:
            # 无对话内容，无需摘要
            self.reset()
            return AgentResponse(text="", is_final=True, summary=None)

        try:
            summary_text = await self.llm.complete(
                system=SUMMARY_PROMPT,
                messages=self.context,
                max_tokens=300,
                temperature=0.3,
                response_format="json",
            )
            summary_data = json.loads(summary_text)
        except (json.JSONDecodeError, Exception):
            logger.exception("Failed to summarize dialog for user=%s", self.user_id)
            summary_data = {
                "location": "",
                "activity": "",
                "sensory": "",
                "mood": "",
                "people": [],
                "highlights": "",
                "raw_summary": "",
            }

        summary = DialogSummary(
            location=summary_data.get("location", ""),
            activity=summary_data.get("activity", ""),
            sensory=summary_data.get("sensory", ""),
            mood=summary_data.get("mood", ""),
            people=summary_data.get("people", []),
            highlights=summary_data.get("highlights", ""),
            raw_summary=summary_data.get("raw_summary", ""),
        )

        self.reset()

        return AgentResponse(
            text="已保存。",
            is_final=True,
            summary=summary,
        )

    @staticmethod
    def _is_end_signal(text: str) -> bool:
        return any(phrase in text for phrase in END_PHRASES)
