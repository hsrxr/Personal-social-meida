"""流式语音识别客户端。

支持多种 ASR 后端：
- OpenAI Whisper API（默认）
- 本地 Whisper 模型
- SenseVoice（阿里达摩院，中文优化）
"""

from __future__ import annotations

import io
import logging
import tempfile
from pathlib import Path
from typing import Optional

from common.config import get_settings

logger = logging.getLogger(__name__)


class ASRSession:
    """单个识别会话——累积音频 chunk，按需触发识别。"""

    def __init__(self, sample_rate: int = 16000):
        self.sample_rate = sample_rate
        self._chunks: list[bytes] = []
        self._closed = False

    def feed(self, audio_chunk: bytes) -> Optional[str]:
        """喂入音频数据块。如果有完整语句则返回识别文本，否则返回 None。"""
        if self._closed:
            raise RuntimeError("ASRSession is closed")
        self._chunks.append(audio_chunk)
        return None

    def get_full_audio(self) -> bytes:
        """获取累积的全部音频数据。"""
        return b"".join(self._chunks)

    def close(self):
        self._closed = True
        self._chunks.clear()


class ASRClient:
    """ASR 客户端——管理多路识别会话。

    生产环境建议使用 OpenAI Whisper API；本地部署可使用 faster-whisper。
    """

    def __init__(self, settings=None):
        self.settings = settings or get_settings()
        self._sessions: dict[str, ASRSession] = {}

    def create_session(self, session_id: str, sample_rate: int = 16000) -> ASRSession:
        session = ASRSession(sample_rate=sample_rate)
        self._sessions[session_id] = session
        return session

    def close_session(self, session_id: str):
        session = self._sessions.pop(session_id, None)
        if session:
            session.close()

    async def transcribe(self, session_id: str) -> str:
        """对完整音频做转写。"""
        session = self._sessions.get(session_id)
        if not session:
            return ""

        audio_data = session.get_full_audio()
        if len(audio_data) < 1600:  # 少于 0.1 秒（16kHz → 1600 samples）
            return ""

        provider = self.settings.asr_provider

        if provider == "openai":
            return await self._transcribe_openai(audio_data)
        elif provider == "local_whisper":
            return await self._transcribe_local_whisper(audio_data)
        else:
            logger.warning("Unknown ASR provider: %s, falling back to openai", provider)
            return await self._transcribe_openai(audio_data)

    async def _transcribe_openai(self, audio_data: bytes) -> str:
        """通过 OpenAI Whisper API 转写。"""
        from openai import AsyncOpenAI

        client = AsyncOpenAI(
            api_key=self.settings.openai_api_key,
            base_url=self.settings.openai_base_url,
            timeout=30.0,
        )

        # 写入临时 WAV 文件（OpenAI 需要文件）
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_data)
            tmp_path = f.name

        try:
            with open(tmp_path, "rb") as audio_file:
                transcript = await client.audio.transcriptions.create(
                    model="whisper-1",
                    file=audio_file,
                    language="zh",
                    response_format="text",
                )
            return transcript.strip()
        finally:
            Path(tmp_path).unlink(missing_ok=True)

    async def _transcribe_local_whisper(self, audio_data: bytes) -> str:
        """通过本地 Whisper 模型转写（需安装 openai-whisper 或 faster-whisper）。"""
        try:
            import whisper
        except ImportError:
            logger.error("whisper not installed, run: pip install openai-whisper")
            return ""

        model = whisper.load_model(self.settings.whisper_model)

        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_data)
            tmp_path = f.name

        try:
            result = model.transcribe(tmp_path, language="zh")
            return result["text"].strip()
        finally:
            Path(tmp_path).unlink(missing_ok=True)
