"""全局配置——从环境变量读取，支持 .env 文件覆盖。"""

from __future__ import annotations

import os
from pathlib import Path
from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置，所有值均可通过环境变量 / .env 覆盖。"""

    # --- LLM ---
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    anthropic_api_key: str = ""
    default_llm_model: str = "gpt-4o"
    default_vision_model: str = "gpt-4o"
    llm_request_timeout: float = 30.0
    llm_max_retries: int = 2

    # --- Redis ---
    redis_url: str = "redis://localhost:6379/0"
    redis_stream_maxlen: int = 10000

    # --- Agent-C (data pipeline) ---
    agent_c_base_url: str = "http://localhost:8001"

    # --- ASR ---
    asr_provider: str = "openai"  # "openai" | "local_whisper" | "sensevoice"
    whisper_model: str = "base"   # used when asr_provider="local_whisper"

    # --- TTS ---
    tts_provider: str = "edge"    # "edge" | "volcano"
    tts_voice: str = "zh-CN-XiaoxiaoNeural"

    # --- Sentence Transformer ---
    embedding_model: str = "BAAI/bge-small-zh-v1.5"
    embedding_dim: int = 512

    # --- Matching ---
    match_top_k: int = 3
    match_cold_start_threshold: int = 100  # 低于此用户数启用冷启动策略
    match_daily_compute_hour: int = 0      # UTC 0点执行每日向量化

    # --- Summary ---
    summary_daily_hour: int = 21           # UTC+8 21:00
    summary_min_entries: int = 2           # 素材不足时跳过

    # --- Dialog ---
    dialog_max_turns: int = 10
    dialog_silence_timeout: float = 3.0    # 秒
    dialog_max_reply_tokens: int = 60

    # --- App ---
    app_host: str = "0.0.0.0"
    app_port: int = 8002
    debug: bool = False

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


@lru_cache()
def get_settings() -> Settings:
    return Settings()
