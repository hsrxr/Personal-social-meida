"""AgentDialogEngine 单元测试。

测试覆盖：
- 状态机流转
- 结束信号检测
- 对话摘要生成
- 上下文管理
- 边界条件
"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from agent_dialog.llm_agent import (
    END_PHRASES,
    SUMMARY_PROMPT,
    SYSTEM_PROMPT,
    AgentDialogEngine,
)
from common.models import AgentResponse, DialogState


class FakeLLMClient:
    """模拟 LLM 客户端——返回固定响应。"""

    def __init__(self, reply: str = "听起来不错，后来呢？", summary: str = None):
        self.reply = reply
        self.summary = summary or (
            '{"location":"望京","activity":"咖啡","sensory":"桂花香","mood":"平静",'
            '"people":[],"highlights":"在咖啡店闻到桂花香","raw_summary":"今天去了望京的咖啡店"}'
        )
        self.complete_calls = []

    async def complete(self, system, user=None, messages=None,
                       max_tokens=1024, temperature=0.7, response_format=None):
        self.complete_calls.append({
            "system": system,
            "messages": messages,
            "response_format": response_format,
        })
        if response_format == "json":
            return self.summary
        return self.reply


# ──────────────────────────────────────
# 状态机测试
# ──────────────────────────────────────

class TestAgentDialogEngine:
    """AgentDialogEngine 核心行为测试。"""

    def test_initial_state(self):
        engine = AgentDialogEngine("test_user")
        assert engine.state == DialogState.IDLE
        assert engine.turn_count == 0
        assert len(engine.context) == 0

    def test_first_speech_transitions_to_thinking_then_speaking(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)

        async def _test():
            response = await engine.on_user_speech("今天去了望京")
            assert response.is_final is False
            assert response.text == "听起来不错，后来呢？"
            assert engine.turn_count == 1
            assert len(engine.context) == 2  # user + assistant

        import asyncio
        asyncio.run(_test())

    def test_end_signal_triggers_summarizing(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)

        async def _test():
            response = await engine.on_user_speech("好了")
            assert response.is_final is True
            assert response.summary is not None
            assert response.summary.location == "望京"
            assert response.summary.mood == "平静"
            assert engine.state == DialogState.IDLE  # 重置
            assert engine.turn_count == 0

        import asyncio
        asyncio.run(_test())

    def test_max_turns_triggers_summarizing(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)
        engine.turn_count = 10  # 已达上限

        async def _test():
            response = await engine.on_user_speech("还有一件事")
            assert response.is_final is True
            assert response.summary is not None

        import asyncio
        asyncio.run(_test())

    def test_empty_input_returns_empty_response(self):
        engine = AgentDialogEngine("test_user")

        async def _test():
            response = await engine.on_user_speech("")
            assert response.text == ""
            assert response.is_final is False

        import asyncio
        asyncio.run(_test())

    def test_short_dialog_skipped_without_summary(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)
        engine.context = []  # 太短

        async def _test():
            engine.context = []  # 空上下文
            response = await engine._summarize()
            assert response.is_final is True
            assert response.summary is None

        import asyncio
        asyncio.run(_test())


class TestEndSignalDetection:
    """结束信号检测测试。"""

    @pytest.mark.parametrize("text,expected", [
        ("好了", True),
        ("就这样吧", True),
        ("记下来", True),
        ("回头聊", True),
        ("没了", True),
        ("说完了", True),
        ("拜拜", True),
        ("先这样", True),
        ("嗯好", True),
        ("今天去了望京", False),
        ("咖啡很好喝", False),
        ("然后在路上看到一只猫", False),
    ])
    def test_end_phrases(self, text, expected):
        assert AgentDialogEngine._is_end_signal(text) == expected


class TestContextManagement:
    """上下文管理测试。"""

    def test_context_uses_sliding_window(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)

        async def _test():
            # 模拟多轮对话
            for i in range(8):
                engine.context.append({"role": "user", "content": f"消息{i}"})
                engine.context.append({"role": "assistant", "content": f"回复{i}"})

            # context 现在有 16 条消息，最近 6 条会被传入 LLM
            response = await engine.on_user_speech("新消息")
            # 验证传递给 LLM 的消息数量（_generate_reply 取 context[-6:]）
            call = llm.complete_calls[-1]
            passed_messages = call["messages"]
            assert len(passed_messages) <= 6

        import asyncio
        asyncio.run(_test())


class TestReset:
    """重置引擎状态测试。"""

    def test_reset_clears_all_state(self):
        engine = AgentDialogEngine("test_user")
        engine.context = [{"role": "user", "content": "hello"}]
        engine.turn_count = 5
        engine.state = DialogState.THINKING

        engine.reset()

        assert engine.state == DialogState.IDLE
        assert len(engine.context) == 0
        assert engine.turn_count == 0


class TestEndDialog:
    """强制结束对话测试。"""

    def test_end_dialog_on_idle_returns_empty(self):
        engine = AgentDialogEngine("test_user")

        async def _test():
            response = await engine.end_dialog()
            assert response.text == ""
            assert response.is_final is True

        import asyncio
        asyncio.run(_test())

    def test_end_dialog_generates_summary(self):
        llm = FakeLLMClient()
        engine = AgentDialogEngine("test_user", llm_client=llm)
        engine.context = [
            {"role": "user", "content": "今天很累"},
            {"role": "assistant", "content": "辛苦了，发生了什么？"},
        ]

        async def _test():
            response = await engine.end_dialog()
            assert response.is_final is True
            assert response.summary is not None
            assert engine.state == DialogState.IDLE

        import asyncio
        asyncio.run(_test())


# ──────────────────────────────────────
# System Prompt 完整性
# ──────────────────────────────────────

def test_system_prompt_exists():
    assert len(SYSTEM_PROMPT) > 100
    assert "私人日志助手" in SYSTEM_PROMPT
    assert "Rokid" in SYSTEM_PROMPT


def test_summary_prompt_exists():
    assert len(SUMMARY_PROMPT) > 50
    assert "结构化信息" in SUMMARY_PROMPT


# ──────────────────────────────────────
# END_PHRASES 完整性
# ──────────────────────────────────────

def test_all_end_phrases_in_list():
    assert "好了" in END_PHRASES
    assert "就这样" in END_PHRASES
    assert "拜拜" in END_PHRASES
    assert len(END_PHRASES) >= 10
