"""Agent 对话引擎——FastAPI + WebSocket 服务。

端点：
- GET  /health                              — 健康检查
- POST /api/dialog/text/{user_id}           — 文本对话（适合手机端发送语音转文本结果）
- WS   /ws/agent-chat/{user_id}             — WebSocket 全双工对话（眼镜端实时音频流）
"""

from __future__ import annotations

import asyncio
import json
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from agent_dialog.asr_client import ASRClient
from agent_dialog.dialog_summarizer import DialogSummarizer
from agent_dialog.llm_agent import AgentDialogEngine
from agent_dialog.tts_client import TTSClient
from common.config import get_settings
from common.models import AgentResponse, DialogSummary
from common.redis_client import RedisStreamClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# ── 全局资源 ──
_engines: dict[str, AgentDialogEngine] = {}  # user_id → engine
_asr_client: Optional[ASRClient] = None
_tts_client: Optional[TTSClient] = None
_redis_client: Optional[RedisStreamClient] = None
_summarizer: Optional[DialogSummarizer] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _asr_client, _tts_client, _redis_client, _summarizer
    settings = get_settings()

    _asr_client = ASRClient(settings)
    _tts_client = TTSClient(settings)
    _redis_client = RedisStreamClient(settings)
    try:
        await _redis_client.connect()
        _summarizer = DialogSummarizer(_redis_client)
    except Exception:
        logger.warning("Redis unavailable, running without message queue")
        _summarizer = None

    logger.info("Agent Dialog service started on port %s", settings.app_port)
    yield

    if _redis_client:
        try:
            await _redis_client.close()
        except Exception:
            pass
    logger.info("Agent Dialog service shut down")


app = FastAPI(
    title="Agent Dialog Engine",
    description="M-Agent 语音对话引擎 —— Rokid 智能眼镜日志助手",
    version="0.1.0",
    lifespan=lifespan,
)


def _get_or_create_engine(user_id: str) -> AgentDialogEngine:
    """获取或创建用户的对话引擎实例。"""
    if user_id not in _engines:
        _engines[user_id] = AgentDialogEngine(user_id)
    return _engines[user_id]


def _remove_engine(user_id: str):
    _engines.pop(user_id, None)


# ──────────────────────────────────────
# REST 端点
# ──────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "active_engines": len(_engines)}


@app.post("/api/dialog/text/{user_id}", response_model=AgentResponse)
async def text_dialog(user_id: str, payload: dict):
    """文本对话——手机端发送用户语音转文本，返回 Agent 回复。

    请求体：
        {"text": "今天去了望京的咖啡店，闻到了桂花香"}

    响应：
        AgentResponse { text, audio_url, is_final, summary }
    """
    text = payload.get("text", "")
    engine = _get_or_create_engine(user_id)
    response = await engine.on_user_speech(text)

    # 如果对话结束，发布摘要到消息队列
    if response.is_final and response.summary:
        await _publish_summary(user_id, response.summary)
        # 异步生成 TTS 音频（"已保存"）
        audio = await _tts_client.synthesize(response.text)
        # 生产环境应上传到 OSS，这里直接返回 base64 示意
        import base64
        response.audio_url = f"data:audio/wav;base64,{base64.b64encode(audio).decode()}"
    elif not response.is_final:
        # 回复也需要 TTS
        audio = await _tts_client.synthesize(response.text)
        import base64
        response.audio_url = f"data:audio/wav;base64,{base64.b64encode(audio).decode()}"

    return response


@app.post("/api/dialog/end/{user_id}", response_model=AgentResponse)
async def end_dialog(user_id: str):
    """强制结束对话——用户摘掉眼镜或主动退出。"""
    engine = _get_or_create_engine(user_id)
    response = await engine.end_dialog()

    if response.is_final and response.summary and _summarizer:
        await _summarizer.publish(user_id, response.summary)
        import base64
        audio = await _tts_client.synthesize(response.text)
        response.audio_url = f"data:audio/wav;base64,{base64.b64encode(audio).decode()}"

    _remove_engine(user_id)
    return response


async def _publish_summary(user_id: str, summary):
    """安全发布摘要（Redis 不可用时跳过）。"""
    if _summarizer and summary:
        try:
            await _summarizer.publish(user_id, summary)
        except Exception:
            logger.warning("Failed to publish summary (Redis unavailable)")


# ──────────────────────────────────────
# WebSocket 端点
# ──────────────────────────────────────

@app.websocket("/ws/agent-chat/{user_id}")
async def agent_chat_ws(ws: WebSocket, user_id: str):
    """WebSocket 全双工对话通道。

    协议：
    - 客户端 → 服务端：JSON {"type": "text", "data": "转写文本"}
                      或 JSON {"type": "audio", "data": "<base64>"}
                      或 JSON {"type": "end"}
    - 服务端 → 客户端：JSON AgentResponse
    """
    await ws.accept()
    engine = _get_or_create_engine(user_id)
    asr_session = _asr_client.create_session(user_id)

    logger.info("WebSocket connected: user=%s", user_id)

    try:
        while True:
            raw = await ws.receive_text()
            msg = json.loads(raw)
            msg_type = msg.get("type", "text")

            if msg_type == "end":
                response = await engine.end_dialog()
                await _publish_summary(user_id, response.summary)
                await ws.send_json(response.model_dump())
                break

            elif msg_type == "audio":
                # 音频 chunk → 累积到 ASR session
                import base64
                audio_chunk = base64.b64decode(msg["data"])
                transcript = asr_session.feed(audio_chunk)
                if transcript:
                    # ASR 有了完整的语句 → 送入对话引擎
                    response = await engine.on_user_speech(transcript)
                    if not response.is_final:
                        # 生成 TTS 音频
                        tts_audio = await _tts_client.synthesize(response.text)
                        response.audio_url = (
                            f"data:audio/wav;base64,"
                            f"{base64.b64encode(tts_audio).decode()}"
                        )
                    else:
                        await _publish_summary(user_id, response.summary)
                    await ws.send_json(response.model_dump())

                    if response.is_final:
                        break

            elif msg_type == "text":
                # 直接文本输入（兼容手机端）
                response = await engine.on_user_speech(msg["data"])
                if not response.is_final:
                    tts_audio = await _tts_client.synthesize(response.text)
                    import base64
                    response.audio_url = (
                        f"data:audio/wav;base64,"
                        f"{base64.b64encode(tts_audio).decode()}"
                    )
                else:
                    await _publish_summary(user_id, response.summary)
                await ws.send_json(response.model_dump())

                if response.is_final:
                    break

    except WebSocketDisconnect:
        logger.info("WebSocket disconnected: user=%s", user_id)
        # 断开连接 → 视为对话结束
        response = await engine.end_dialog()
        await _publish_summary(user_id, response.summary)
    finally:
        _asr_client.close_session(user_id)
        _remove_engine(user_id)
