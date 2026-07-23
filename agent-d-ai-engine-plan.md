# Agent-D 开发计划：AI 引擎 + Agent 对话

> **角色**：所有需要调用模型/算法的部分——语音对话 Agent、每日总结/文案生成、视觉分析、匹配推荐  
> **技术栈**：Python (FastAPI), LLM API (GPT-4o/Claude), ASR (Whisper/SenseVoice), TTS (Edge TTS/火山引擎), FAISS/Sentence-Transformer, Redis Streams  
> **依赖**：Agent-C（提供 `MaterialStore` 素材数据 + 消息队列）  
> **交付物**：AI 推理服务集群  
> **工期**：5 周  
> **前置条件**：Agent-C 的 journals/entries API 就绪 + 消息队列可用

---

## 1. 服务拆分

```
ai-services/
├── agent-dialog/              ← M-Agent 语音对话引擎（最高优先级）
│   ├── main.py                ← FastAPI + WebSocket endpoint
│   ├── asr_client.py          ← 流式语音识别客户端
│   ├── llm_agent.py           ← LLM 对话核心（System Prompt + 上下文管理）
│   ├── tts_client.py          ← 语音合成客户端
│   ├── dialog_summarizer.py   ← 对话结束 → 结构化摘要
│   └── prompt_templates/
│       └── agent_system.txt   ← System Prompt 模板
│
├── journal-summary/           ← M9 每日总结引擎
│   ├── main.py
│   ├── summarizer.py          ← Prompt 工程 + LLM 调用
│   ├── keyword_extractor.py
│   └── prompt_templates/
│
├── social-copy/               ← M10 社交文案生成器
│   ├── main.py
│   ├── copy_generator.py      ← 三风格 Prompt 变体
│   ├── feedback_learner.py    ← 用户行为反馈优化
│   └── prompt_templates/
│       ├── wechat_moments.txt
│       ├── xiaohongshu.txt
│       └── instagram.txt
│
├── vision-tagger/             ← M7 视觉/情绪自动标注
│   ├── main.py
│   ├── image_analyzer.py      ← GPT-4V/Claude Vision 场景描述
│   ├── sentiment_analyzer.py  ← NLP 情感分析
│   └── activity_classifier.py ← 活动类型分类
│
├── match-engine/              ← M11 匹配引擎
│   ├── main.py
│   ├── embedder.py            ← Sentence-Transformer 向量化
│   ├── matcher.py             ← FAISS 相似度搜索
│   ├── ice_break_generator.py ← 破冰语生成
│   └── cold_start.py          ← 冷启动策略
│
├── common/
│   ├── config.py              ← 全局配置（API keys, endpoints）
│   ├── llm_client.py          ← LLM API 统一封装（支持多模型切换）
│   ├── redis_client.py        ← Redis Streams 消费/发布
│   └── models.py              ← 共享数据模型（Pydantic）
│
├── Dockerfile
└── requirements.txt
```

---

## 2. M-Agent：语音对话引擎（核心模块）

### 2.1 System Prompt

```
你是用户的私人日志助手，运行在 Rokid 智能眼镜上。

你的角色：
1. 倾听用户讲述日常生活，追问有趣细节
2. 在用户说完后给出简短共鸣和自然追问（1-2句）
3. 在对话自然结束或用户主动结束时，将对话结构化为日志条目

行为规范：
- 保持温暖、好奇、不评判的语气
- 每次回复不超过30个字（眼镜端语音播放限制）
- 不做搜索引擎、不做知识问答、不做闲聊
- 如果用户话语中包含明显的地点/活动/感官信息，主动标注
- 如果用户情绪明显（开心/沮丧/焦虑），记录但不直接评论

对话结束信号：
- 用户说"好了/就这样/记下来/回头聊"
- 用户3秒沉默
- 用户摘掉眼镜（onGlassWearingStatus=false）

对话结束后，生成以下结构化摘要：
{
  "location": "地点名",
  "activity": "活动类型",
  "sensory": "感官细节（如有）",
  "mood": "推断情绪",
  "people": "提及的人",
  "highlights": "值得记住的瞬间",
  "raw_summary": "50字以内自然语言摘要"
}
```

### 2.2 对话状态机

```python
class DialogState(Enum):
    IDLE = "idle"
    LISTENING = "listening"        # 用户正在说话
    THINKING = "thinking"           # LLM 正在生成回复
    SPEAKING = "speaking"           # TTS 正在播放
    SUMMARIZING = "summarizing"     # 对话结束，生成摘要

class AgentDialogEngine:
    def __init__(self):
        self.state = DialogState.IDLE
        self.context: list[dict] = []  # 对话上下文
        self.MAX_TURNS = 10            # 最大对话轮次
        self.SILENCE_TIMEOUT = 3.0     # 静音超时（秒）
        self.turn_count = 0

    async def on_user_speech(self, text: str) -> AgentResponse:
        self.context.append({"role": "user", "content": text})
        self.state = DialogState.THINKING
        
        # 检查结束信号
        if self._is_end_signal(text) or self.turn_count >= self.MAX_TURNS:
            return await self._summarize()
        
        # LLM 生成回复
        reply = await llm_complete(
            system=self.SYSTEM_PROMPT,
            messages=self.context[-6:],  # 最近6轮上下文
            max_tokens=60
        )
        self.context.append({"role": "assistant", "content": reply})
        self.turn_count += 1
        self.state = DialogState.SPEAKING
        
        return AgentResponse(text=reply, audio=self._tts(reply), is_final=False)

    async def _summarize(self) -> AgentResponse:
        self.state = DialogState.SUMMARIZING
        summary = await llm_complete(
            system="从以下对话中提取结构化信息，输出JSON。",
            messages=self.context,
            response_format="json"
        )
        self.state = DialogState.IDLE
        self.context.clear()
        self.turn_count = 0
        return AgentResponse(
            text="已保存。",
            is_final=True,
            summary=json.loads(summary)
        )

    def _is_end_signal(self, text: str) -> bool:
        end_phrases = ["好了", "就这样", "记下来", "回头聊", "没了", "说完了", "拜拜"]
        return any(phrase in text for phrase in end_phrases)
```

### 2.3 WebSocket 端点

```python
@router.websocket("/ws/agent-chat/{user_id}")
async def agent_chat(ws: WebSocket, user_id: str):
    await ws.accept()
    engine = AgentDialogEngine()
    
    async def receive_audio():
        """接收音频 chunk → ASR → 推送文本到对话队列"""
        asr_session = asr_client.create_session()
        while True:
            audio_chunk = await ws.receive_bytes()
            text = await asr_session.feed(audio_chunk)
            if text:
                response = await engine.on_user_speech(text)
                await ws.send_json(response.model_dump())
                if response.is_final:
                    # 对话结束，发布摘要到消息队列
                    await redis.publish("ai.dialog.summary", response.summary)
                    asr_session.close()
                    return

    async def send_tts_audio():
        """引擎生成的对话回复，异步TTS后推送到眼镜"""
        # 由 engine 内部的 TTS 客户端驱动
        pass

    await asyncio.gather(receive_audio(), send_tts_audio())
```

---

## 3. 每日总结引擎 (M9)

### 3.1 Prompt 工程

```
System:
你是个人日志助手。根据用户一天的第一人称素材，生成简洁、温暖的每日总结。
要求：
- 关键词：3-5个，以名词/动词/情绪词为主
- 叙述文字：100-200字，第二人称
- 语气：温暖、平实，不煽情
- 如果有特殊事件（偶遇/新发现/开心时刻），优先提及

Input:
{entries_json}
Output (JSON only):
{
  "keywords": ["咖啡", "望京", "桂花", "平静"],
  "summary": "今天...",
  "mood": "平静",
  "highlight": "在楼下咖啡店闻到了今年第一缕桂花香"
}
```

### 3.2 批量处理

```python
class JournalSummarizer:
    def __init__(self, llm_client, redis_client):
        self.llm = llm_client
        self.redis = redis_client

    async def run_daily_batch(self):
        """每日 21:00 批量处理当天所有活跃用户的日志"""
        today = date.today()
        # 从消息队列消费需要处理的用户列表
        user_ids = await self.redis.xread("ai.summary.request")
        
        for user_id in user_ids:
            entries = await self.material_store.get_daily_entries(user_id, today)
            if len(entries) < 2:  # 素材太少，跳过
                continue
            result = await self.generate(today, entries)
            await self.redis.xadd("ai.summary.response", {
                "user_id": user_id,
                "date": str(today),
                **result
            })

    async def generate(self, date, entries) -> dict:
        prompt = self._build_prompt(entries)
        response = await self.llm.complete(
            system=SUMMARY_SYSTEM_PROMPT,
            user=prompt,
            response_format="json",
            temperature=0.7
        )
        return json.loads(response)
```

---

## 4. 社交文案生成器 (M10)

### 4.1 三风格 Prompt 设计

```python
PLATFORM_PROMPTS = {
    "wechat_moments": """
你是朋友圈文案助手。基于用户的每日总结，生成朋友圈风格的分享文案。
要点：
- 口语化、亲切，像和朋友聊天
- 2-3句话即可，不要太长
- 适当使用1-2个emoji，不要堆砌
- 重点放在个人感受和趣事上
    """,
    
    "xiaohongshu": """
你是小红书文案助手。基于用户的每日总结，生成小红书笔记风格文案。
要点：
- 种草/分享语气，活泼积极
- 分点式列出（如适用），结构清晰
- emoji丰富但适度
- 包含2-3个话题标签 #
- 重点放在场景氛围和生活美学上
    """,
    
    "instagram": """
你是Instagram文案助手。基于用户的每日总结，生成Instagram风格文案。
要点：
- 视觉导向，短句+氛围感
- 可以用英文短语穿插
- 包含3-5个hashtag
- 重点放在moment和vibe上
    """
}
```

### 4.2 反馈学习

```python
class FeedbackLearner:
    """记录用户行为，优化后续生成"""
    
    def __init__(self):
        self.events = []  # 生产环境应持久化
    
    def record(self, user_id: str, platform: str, action: str, copy_id: str):
        """action: 'copied' | 'edited' | 'regenerated' | 'ignored'"""
        self.events.append({
            "user_id": user_id,
            "platform": platform,
            "action": action,
            "copy_id": copy_id,
            "timestamp": time.time()
        })
    
    def get_user_preference(self, user_id: str) -> dict:
        """分析用户偏好 → 调整 Prompt 参数"""
        user_events = [e for e in self.events if e["user_id"] == user_id]
        # 简单统计：复制率最高的平台 = 用户偏好平台
        platform_counts = Counter(e["platform"] for e in user_events if e["action"] == "copied")
        preferred = platform_counts.most_common(1)
        return {"preferred_platform": preferred[0][0] if preferred else "wechat_moments"}
```

---

## 5. 视觉/情绪标注 (M7)

### 5.1 图片分析

```python
class ImageAnalyzer:
    async def analyze(self, image_url: str) -> ImageAnnotation:
        """使用 GPT-4V 分析图片内容"""
        response = await self.llm.vision(
            image_url=image_url,
            prompt="""
分析这张图片，输出JSON：
{
  "scene": "场景描述（如'咖啡店内景'）",
  "objects": ["识别到的物体列表"],
  "activity": "活动类型推断（咖啡/工作/运动/社交等）",
  "indoor": true/false,
  "quality_score": 1-10 (构图质量评分),
  "is_social_worthy": true/false (是否适合发社交媒体)
}
"""
        )
        return ImageAnnotation(**json.loads(response))

class SentimentAnalyzer:
    async def analyze_text(self, text: str) -> dict:
        """NLP情感分析（从音频转写文本推断情绪）"""
        response = await self.llm.complete(
            system="分析以下文本的情绪倾向，输出JSON: {mood: '开心'/'平静'/'烦躁'/'兴奋'/'疲惫'/'中性', confidence: 0-1}",
            user=text,
            response_format="json"
        )
        return json.loads(response)

class ActivityClassifier:
    ACTIVITIES = ["咖啡", "工作", "运动", "通勤", "社交", "购物", "餐饮", 
                   "阅读", "户外", "音乐", "旅行", "居家", "其他"]
    
    async def classify(self, image_annotation: ImageAnnotation, 
                       transcription: str, location_name: str) -> str:
        """多模态综合判断活动类型"""
        # 简单规则 + LLM 综合判断
        # ...
```

---

## 6. 匹配引擎 (M11)

### 6.1 向量化匹配

```python
class MatchEngine:
    def __init__(self):
        self.embedder = SentenceTransformer('BAAI/bge-small-zh-v1.5')  # 中文优化
        self.index_cache: dict[str, faiss.IndexFlatIP] = {}  # 每天一个索引
    
    async def compute_daily_embeddings(self, date: date):
        """每日 00:00 计算所有用户的日志向量"""
        journals = await self.material_store.get_all_journals(date)
        
        texts = []
        user_ids = []
        for j in journals:
            # 拼接成统一文本用于向量化
            text = f"地点:{j.location_names} 活动:{j.activities} 情绪:{j.mood} 标签:{j.tags} 总结:{j.summary}"
            texts.append(text)
            user_ids.append(j.user_id)
        
        embeddings = self.embedder.encode(texts, normalize_embeddings=True)
        
        # 构建 FAISS 索引
        index = faiss.IndexFlatIP(embeddings.shape[1])  # 内积相似度
        index.add(embeddings)
        self.index_cache[str(date)] = index
        
        # 存储 user_id → index 映射
        self.user_index_map[str(date)] = user_ids
    
    async def find_matches(self, user_id: str, date: date, top_k: int = 3) -> list:
        """为用户找到最佳匹配"""
        date_key = str(date)
        if date_key not in self.index_cache:
            await self.compute_daily_embeddings(date)
        
        index = self.index_cache[date_key]
        user_ids = self.user_index_map[date_key]
        
        # 用户自身向量
        user_idx = user_ids.index(user_id)
        user_vec = self.embedder.encode([...])  # 用户当天的向量
        
        # FAISS 搜索
        distances, indices = index.search(user_vec.reshape(1, -1), top_k + 1)  # +1 排除自己
        
        results = []
        for dist, idx in zip(distances[0], indices[0]):
            if user_ids[idx] == user_id:  # 跳过自己
                continue
            common_details = await self._extract_common_details(user_id, user_ids[idx], date)
            results.append({
                "matched_user_id": user_ids[idx],
                "similarity": float(dist),
                "common_details": common_details
            })
        
        return results[:top_k]
    
    async def _extract_common_details(self, user_a: str, user_b: str, date: date) -> list:
        """提取共同细节（用于匹配卡片展示）"""
        journal_a = await self.material_store.get_journal(user_a, date)
        journal_b = await self.material_store.get_journal(user_b, date)
        
        common = []
        if journal_a.location_name == journal_b.location_name:
            common.append({"type": "location", "value": journal_a.location_name})
        if journal_a.mood == journal_b.mood:
            common.append({"type": "mood", "value": journal_a.mood})
        shared_tags = set(journal_a.tags) & set(journal_b.tags)
        for tag in shared_tags:
            common.append({"type": "tag", "value": tag})
        
        return common
```

### 6.2 破冰语生成

```python
class IceBreakGenerator:
    async def generate(self, common_details: list) -> str:
        response = await self.llm.complete(
            system="""基于两个用户的共同点，生成一条友好的破冰问候。
要求：15字以内，自然不刻意，像一个朋友在聊天。""",
            user=f"共同点：{json.dumps(common_details, ensure_ascii=False)}",
            max_tokens=30
        )
        return response.strip()
```

### 6.3 冷启动策略

```python
class ColdStartStrategy:
    async def get_fallback_matches(self, user_id: str, top_k: int = 3) -> list:
        """当真实用户不足时，提供AI种子匹配"""
        active_users_count = await self.get_active_user_count()
        
        if active_users_count < 100:
            # 扩大时间窗口：从"同日"放宽到"同周"
            return await self.find_weekly_matches(user_id, top_k)
        
        if active_users_count < 10:
            # 极端冷启动：展示"城市共感"而非具体匹配
            return [{
                "matched_user_id": None,
                "type": "city_vibe",
                "message": "今天有 {n} 个人也在望京附近，他们都提到了桂花香"
            }]
        
        return []
```

---

## 7. 消息队列消费

```python
class AiWorker:
    """AI Worker 消费 Agent-C 的消息，处理后发布结果"""
    
    def __init__(self):
        self.redis = redis.asyncio.Redis()
        self.summarizer = JournalSummarizer(...)
        self.copy_generator = CopyGenerator(...)
        self.match_engine = MatchEngine(...)
        self.vision_tagger = ImageAnalyzer(...)
    
    async def run(self):
        """持续消费消息队列"""
        async for msg in self.redis.xread_group(
            group="ai-workers",
            consumer="worker-1",
            streams={
                "ai.summary.request": ">",
                "ai.social_copy.request": ">",
                "ai.match.request": ">",
                "ai.vision_tag.request": ">"
            }
        ):
            stream = msg.stream
            data = msg.data
            
            if stream == "ai.summary.request":
                result = await self.summarizer.generate(data["date"], data["entries"])
                await self.redis.xadd("ai.summary.response", {
                    "user_id": data["user_id"], "date": data["date"], **result
                })
            
            elif stream == "ai.social_copy.request":
                copies = await self.copy_generator.generate(data)
                await self.redis.xadd("ai.social_copy.response", {
                    "user_id": data["user_id"], "copies": json.dumps(copies)
                })
            
            elif stream == "ai.match.request":
                matches = await self.match_engine.find_matches(data["user_id"], data["date"])
                await self.redis.xadd("ai.match.response", {
                    "user_id": data["user_id"], "matches": json.dumps(matches)
                })
            
            elif stream == "ai.vision_tag.request":
                annotation = await self.vision_tagger.analyze(data["image_url"])
                await self.redis.xadd("ai.vision_tag.response", {
                    "entry_id": data["entry_id"], "annotation": annotation.json()
                })
```

---

## 8. Sprint 计划

### Sprint 0：环境搭建 + LLM Client（3 天）

- [ ] Python 项目脚手架（FastAPI + Poetry/pip）
- [ ] `LLMClient` 统一封装：
  - OpenAI API (GPT-4o/GPT-4V)
  - Anthropic API (Claude)
  - 支持模型切换 + 降级策略
- [ ] Redis Streams 消费者/生产者封装
- [ ] Docker Compose（all services）
- [ ] `common/models.py` — Pydantic 数据模型（对齐 Agent-C 的 JSON 格式）

### Sprint 1：Agent 对话引擎（6 天）

- [ ] `AgentDialogEngine` 完整实现
  - 状态机：IDLE → LISTENING → THINKING → SPEAKING → SUMMARIZING
  - System Prompt 调优
  - 结束信号检测
  - 对话摘要结构化提取
- [ ] ASR 客户端
  - Whisper API 或 本地 SenseVoice 模型
  - 流式识别 → 文本实时推送
- [ ] TTS 客户端
  - Edge TTS (免费) 或 火山引擎 TTS
  - 自然语音合成，目标延迟 < 500ms
- [ ] WebSocket 端点实现
- [ ] 测试：文本对话 → 回复生成 → TTS 音频验证

### Sprint 2：每日总结 + 标签标注（5 天）

- [ ] `JournalSummarizer` 实现
  - Prompt 模板 + LLM 调用
  - 批量处理（每日 21:00）
- [ ] `ImageAnalyzer` 实现
  - GPT-4V / Claude Vision 场景描述
  - 构图质量评分
- [ ] `SentimentAnalyzer` + `ActivityClassifier` 实现
- [ ] 消息队列集成：
  - 消费 `ai.summary.request` → 生产 `ai.summary.response`
  - 消费 `ai.vision_tag.request` → 生产 `ai.vision_tag.response`
- [ ] 端到端测试：输入一天素材 → 输出总结 + 自动标签

### Sprint 3：社交文案生成器（3 天）

- [ ] `CopyGenerator` 三风格实现
  - 朋友圈 / 小红书 / Instagram 三种 Prompt 变体
  - 配图推荐（基于 Vision quality_score）
- [ ] `FeedbackLearner` 用户偏好追踪
- [ ] 消息队列集成
- [ ] 测试：同一份总结 → 三种风格的文案对比

### Sprint 4：匹配引擎（4 天）

- [ ] `MatchEngine.compute_daily_embeddings` 每日向量化
- [ ] `MatchEngine.find_matches` FAISS 搜索
- [ ] `IceBreakGenerator` 破冰语生成
- [ ] `ColdStartStrategy` 冷启动策略
- [ ] 消息队列集成
- [ ] 模拟测试：100 用户 × 30 天数据 → 匹配质量评估

### Sprint 5：集成测试 + 性能优化（3 天）

- [ ] 全链路测试：
  - Agent 对话 → 摘要入库 → 每日总结 → 社交文案 → 匹配推荐
- [ ] 性能指标：
  - Agent 对话延迟（ASR+TTS 端到端 < 2s）
  - 匹配计算速度（1000 用户 < 5 分钟）
  - 每日总结批量生成（每用户 < 10s）
- [ ] 异常处理：
  - LLM API 超时/降级
  - 空素材时跳过生成
  - 用户无权限时拒绝处理
- [ ] API 文档 + Prompt 模板归档
