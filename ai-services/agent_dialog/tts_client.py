"""语音合成客户端。

支持：
- Edge TTS（免费，默认）
- 火山引擎 TTS（商业，需配置 API key）
"""

from __future__ import annotations

import io
import logging
from typing import Optional

from common.config import get_settings

logger = logging.getLogger(__name__)


class TTSClient:
    """TTS 客户端——文本转语音，返回音频数据。"""

    def __init__(self, settings=None):
        self.settings = settings or get_settings()

    async def synthesize(self, text: str) -> bytes:
        """将文本转为语音，返回 PCM/WAV 音频数据。"""
        if not text.strip():
            return b""

        provider = self.settings.tts_provider

        if provider == "edge":
            return await self._synthesize_edge(text)
        elif provider == "volcano":
            return await self._synthesize_volcano(text)
        else:
            logger.warning("Unknown TTS provider: %s", provider)
            return b""

    async def _synthesize_edge(self, text: str) -> bytes:
        """通过 Microsoft Edge TTS（免费、无需 API key）合成语音。"""
        try:
            import edge_tts

            communicate = edge_tts.Communicate(
                text=text,
                voice=self.settings.tts_voice,
            )

            audio_chunks = []
            async for chunk in communicate.stream():
                if chunk["type"] == "audio":
                    audio_chunks.append(chunk["data"])

            return b"".join(audio_chunks)
        except ImportError:
            logger.error("edge-tts not installed, run: pip install edge-tts")
            return b""
        except Exception:
            logger.exception("Edge TTS synthesis failed")
            return b""

    async def _synthesize_volcano(self, text: str) -> bytes:
        """通过火山引擎 TTS 合成语音。"""
        # 火山引擎 TTS 需要 appid + token，生产环境实现
        logger.warning("Volcano TTS not implemented yet, falling back to Edge TTS")
        return await self._synthesize_edge(text)
