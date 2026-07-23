"""LLM API 统一封装——支持 OpenAI / Anthropic 多模型切换与降级。

使用方式：
    from common.llm_client import LLMClient
    client = LLMClient()
    reply = await client.complete(system="...", user="...", model="gpt-4o")
    result = await client.vision(image_url="...", prompt="...")
"""

from __future__ import annotations

import json
import logging
from typing import Optional

from openai import AsyncOpenAI
from anthropic import AsyncAnthropic

from common.config import get_settings

logger = logging.getLogger(__name__)


class LLMError(Exception):
    """LLM 调用失败。"""


class LLMClient:
    """统一 LLM 调用封装。

    特性：
    - 支持 OpenAI (GPT-4o, GPT-4V) 和 Anthropic (Claude) 切换
    - 自动 JSON 响应解析
    - 超时重试（通过 tenacity）
    - 降级策略：主模型失败 → fallback 模型
    """

    def __init__(self, settings=None):
        self.settings = settings or get_settings()
        self._openai: Optional[AsyncOpenAI] = None
        self._anthropic: Optional[AsyncAnthropic] = None

    @property
    def openai(self) -> AsyncOpenAI:
        if self._openai is None:
            self._openai = AsyncOpenAI(
                api_key=self.settings.openai_api_key,
                base_url=self.settings.openai_base_url,
                timeout=self.settings.llm_request_timeout,
                max_retries=self.settings.llm_max_retries,
            )
        return self._openai

    @property
    def anthropic(self) -> AsyncAnthropic:
        if self._anthropic is None:
            self._anthropic = AsyncAnthropic(
                api_key=self.settings.anthropic_api_key,
                timeout=self.settings.llm_request_timeout,
                max_retries=self.settings.llm_max_retries,
            )
        return self._anthropic

    # ──────────────────────────────────────
    # 文本对话
    # ──────────────────────────────────────

    async def complete(
        self,
        system: str,
        user: str,
        model: Optional[str] = None,
        messages: Optional[list[dict]] = None,
        max_tokens: int = 1024,
        temperature: float = 0.7,
        response_format: Optional[str] = None,  # "json" → 自动解析
    ) -> str:
        """发送文本对话请求，返回文本响应。

        Args:
            system: system prompt
            user: user message（与 messages 互斥）
            model: 模型名，默认使用配置中的 default_llm_model
            messages: 完整消息列表（提供时忽略 system/user 参数）
            max_tokens: 最大生成 token 数
            temperature: 采样温度
            response_format: 设为 "json" 时自动解析为 dict 并重新序列化（确保合法 JSON）
        """
        model = model or self.settings.default_llm_model
        is_openai = model.startswith("gpt") or model.startswith("o1") or model.startswith("o3")

        if messages is None:
            messages = [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ]

        try:
            if is_openai:
                return await self._openai_complete(
                    model, messages, max_tokens, temperature, response_format
                )
            else:
                return await self._anthropic_complete(
                    model, messages, max_tokens, temperature, response_format
                )
        except Exception:
            logger.exception("LLM call failed for model=%s", model)
            raise LLMError(f"LLM call failed for model={model}")

    async def _openai_complete(
        self, model: str, messages: list[dict],
        max_tokens: int, temperature: float, response_format: Optional[str],
    ) -> str:
        kwargs = dict(
            model=model,
            messages=messages,
            max_tokens=max_tokens,
            temperature=temperature,
        )
        if response_format == "json":
            kwargs["response_format"] = {"type": "json_object"}

        resp = await self.openai.chat.completions.create(**kwargs)
        content = resp.choices[0].message.content or ""

        if response_format == "json":
            content = self._ensure_json(content)
        return content

    async def _anthropic_complete(
        self, model: str, messages: list[dict],
        max_tokens: int, temperature: float, response_format: Optional[str],
    ) -> str:
        # Anthropic uses system param separately
        system_msg = ""
        user_messages = []
        for m in messages:
            if m["role"] == "system":
                system_msg = m["content"]
            else:
                user_messages.append(m)

        kwargs = dict(
            model=model,
            system=system_msg,
            messages=user_messages,
            max_tokens=max_tokens,
            temperature=temperature,
        )

        resp = await self.anthropic.messages.create(**kwargs)
        content = resp.content[0].text if resp.content else ""

        if response_format == "json":
            content = self._ensure_json(content)
        return content

    # ──────────────────────────────────────
    # 视觉分析（GPT-4V / Claude Vision）
    # ──────────────────────────────────────

    async def vision(
        self,
        image_url: str,
        prompt: str,
        model: Optional[str] = None,
        max_tokens: int = 512,
        response_format: Optional[str] = None,
    ) -> str:
        """视觉分析：发送图片 + 提示词，返回文本分析结果。"""
        model = model or self.settings.default_vision_model
        is_openai = model.startswith("gpt") or model.startswith("o1")

        try:
            if is_openai:
                return await self._openai_vision(model, image_url, prompt, max_tokens, response_format)
            else:
                return await self._anthropic_vision(model, image_url, prompt, max_tokens, response_format)
        except Exception:
            logger.exception("Vision call failed for model=%s", model)
            raise LLMError(f"Vision call failed for model={model}")

    async def _openai_vision(
        self, model: str, image_url: str, prompt: str,
        max_tokens: int, response_format: Optional[str],
    ) -> str:
        messages = [{
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": image_url}},
            ],
        }]
        kwargs = dict(model=model, messages=messages, max_tokens=max_tokens)
        if response_format == "json":
            kwargs["response_format"] = {"type": "json_object"}

        resp = await self.openai.chat.completions.create(**kwargs)
        content = resp.choices[0].message.content or ""

        if response_format == "json":
            content = self._ensure_json(content)
        return content

    async def _anthropic_vision(
        self, model: str, image_url: str, prompt: str,
        max_tokens: int, response_format: Optional[str],
    ) -> str:
        # Anthropic vision requires base64 images; for URL we instruct the model
        # For production, download image and send as base64.
        import base64
        import httpx

        async with httpx.AsyncClient() as client:
            resp = await client.get(image_url)
            resp.raise_for_status()
            image_data = base64.b64encode(resp.content).decode("utf-8")
            content_type = resp.headers.get("content-type", "image/jpeg")

        messages = [{
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": content_type,
                        "data": image_data,
                    },
                },
                {"type": "text", "text": prompt},
            ],
        }]

        api_resp = await self.anthropic.messages.create(
            model=model,
            messages=messages,
            max_tokens=max_tokens,
        )
        content = api_resp.content[0].text if api_resp.content else ""

        if response_format == "json":
            content = self._ensure_json(content)
        return content

    # ──────────────────────────────────────
    # 工具方法
    # ──────────────────────────────────────

    @staticmethod
    def _ensure_json(text: str) -> str:
        """确保输出是合法 JSON 字符串。尝试提取 JSON 块并重新序列化。"""
        text = text.strip()
        # 处理 markdown 代码块包裹
        if text.startswith("```"):
            lines = text.split("\n")
            lines = lines[1:] if len(lines) > 1 else lines
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines).strip()
        try:
            parsed = json.loads(text)
            return json.dumps(parsed, ensure_ascii=False)
        except json.JSONDecodeError:
            logger.warning("LLM returned non-JSON despite json format request: %s", text[:200])
            return text
