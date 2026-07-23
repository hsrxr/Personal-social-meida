# Agent-C 开发计划：数据管线 + 后端服务

> **角色**：数据从眼镜/手机产生到落盘到被查询的完整链路，以及云端 API 网关  
> **技术栈**：Kotlin (Room/本地存储), Go/Node.js (后端), PostgreSQL, Redis, OSS SDK  
> **依赖**：Agent-A (`cxr-core` 的 SessionManager/AudioReceiver/PhotoPipeline 接口)  
> **交付物**：手机端 `data` 模块 + 云端 API 服务  
> **工期**：5 周

---

## 1. 模块拆分为两个子 Agent 域

```
┌────────────────────────────┐
│  Agent-C1: 手机端数据层     │  → Kotlin, Room, Repository
│  (本地存储 + 素材聚合)       │
├────────────────────────────┤
│  Agent-C2: 云端服务        │  → Go/Node, PostgreSQL, Redis, OSS
│  (API + 存储 + 消息推送)    │
└────────────────────────────┘
```

---

## 2. Agent-C1：手机端数据层

### 2.1 架构

```
data/
├── local/
│   ├── AppDatabase.kt          ← Room 数据库定义
│   ├── dao/
│   │   ├── JournalDao.kt       ← DailyJournal CRUD
│   │   ├── EntryDao.kt         ← TimelineEntry CRUD
│   │   ├── TagDao.kt           ← 标签/情绪
│   │   ├── MatchDao.kt         ← 匹配记录
│   │   └── UserDao.kt          ← 本地用户Profile
│   ├── entity/
│   │   ├── DailyJournalEntity.kt
│   │   ├── TimelineEntryEntity.kt
│   │   ├── TagEntity.kt
│   │   ├── MatchEntity.kt
│   │   └── UserEntity.kt
│   └── converter/
│       └── LocalDateConverter.kt
├── repository/
│   ├── TimelineRepositoryImpl.kt   ← M6 时间线引擎实现
│   ├── MaterialRepositoryImpl.kt   ← M5 素材聚合管线实现
│   ├── AnnotationRepositoryImpl.kt ← M7 标签/情绪标注实现
│   ├── MatchRepositoryImpl.kt
│   └── UserRepositoryImpl.kt
├── pipeline/
│   ├── MaterialIngestion.kt    ← 素材接收总线（Audio/Photo/Manual入口）
│   ├── MaterialNormalizer.kt   ← 时间戳归→化/去重/富化
│   ├── MediaUploader.kt        ← OSS后台上传队列
│   └── SyncWorker.kt           ← WorkManager 云端同步
├── remote/
│   ├── ApiService.kt           ← Retrofit 接口定义
│   ├── dto/                    ← 网络数据传输对象
│   └── SyncManager.kt          ← 离线/在线切换策略
└── di/
    └── DataModule.kt           ← Hilt 注入
```

### 2.2 Room 数据库 Schema

```kotlin
@Database(
    entities = [
        DailyJournalEntity::class,
        TimelineEntryEntity::class,
        TagEntity::class,
        MatchEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase()

// ===== 核心表 =====

@Entity(tableName = "daily_journals")
data class DailyJournalEntity(
    @PrimaryKey val date: String,             // "2026-07-23"
    val summary: String? = null,
    val keywords: String? = null,             // JSON Array
    val mood: String? = null,
    val entryCount: Int = 0,
    val lastModified: Long = 0
)

@Entity(
    tableName = "timeline_entries",
    indices = [Index("date"), Index("timestamp")]
)
data class TimelineEntryEntity(
    @PrimaryKey val id: String,               // UUID
    val date: String,                         // "2026-07-23"
    val timestamp: Long,                      // epoch millis
    val type: String,                         // PHOTO | AUDIO | NOTE | AGENT_DIALOG | MOMENT_MARK
    val source: String,                       // GLASSES | PHONE | MANUAL
    val localPath: String?,                   // 本地文件路径
    val remoteUrl: String?,                   // OSS URL
    val thumbnailPath: String?,
    val durationMs: Int?,                     // 音频时长
    val transcription: String?,               // 音频转写文本
    val locationName: String?,                // POI名称
    val latitude: Double?,                    // GPS
    val longitude: Double?,
    val aiAnnotation: String? = null,         // JSON: Vision/NLP结果
    val isStarred: Boolean = false,           // 眼镜按键标记
    val syncStatus: String = "PENDING",       // PENDING | SYNCING | SYNCED | FAILED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val name: String,                         // "#咖啡"
    val type: String,                         // MOOD | ACTIVITY | LOCATION | CUSTOM | AI_SUGGESTED
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2.3 Repository 接口（对外暴���给 Agent-B）

```kotlin
interface TimelineRepository {
    fun getJournal(date: LocalDate): Flow<DailyJournal>
    fun getJournals(range: ClosedRange<LocalDate>): Flow<List<DailyJournal>>
    fun getEntries(date: LocalDate): Flow<List<TimelineEntry>>
    suspend fun addEntry(entry: TimelineEntry): String
    suspend fun updateAnnotation(entryId: String, annotation: AiAnnotation)
}

interface AnnotationRepository {
    fun getTags(entryId: String): Flow<List<Tag>>
    suspend fun addTags(entryId: String, tags: List<Tag>)
    suspend fun setMood(date: LocalDate, mood: Mood)
    suspend fun toggleStar(entryId: String)
}
```

### 2.4 素材聚合管线 (MaterialIngestion)

```kotlin
class MaterialIngestion @Inject constructor(
    private val audioReceiver: AudioReceiver,     // Agent-A
    private val photoPipeline: PhotoPipeline,     // Agent-A
    private val commandChannel: CommandChannel,   // Agent-A
    private val entryDao: EntryDao,
    private val mediaUploader: MediaUploader
) {
    fun start() {
        // 注册三个数据源
        collectAudioChunks()
        collectPhotos()
        collectKeyEvents()
    }

    private fun collectAudioChunks() {
        audioReceiver.audioChunkFlow.collect { chunk ->
            val entry = TimelineEntry(
                type = EntryType.AUDIO,
                source = Source.GLASSES,
                pcmData = chunk.pcmData,
                durationMs = chunk.durationMs
            )
            processEntry(entry)
        }
    }

    private fun collectPhotos() { /* 类似 */ }
    private fun collectKeyEvents() { /* 眼镜按键 → moment_mark */ }
    
    private suspend fun processEntry(entry: TimelineEntry) {
        // 1. 时间戳归一化
        val normalized = MaterialNormalizer.normalize(entry)
        // 2. 去重检查
        if (isDuplicate(normalized)) return
        // 3. GPS富化：逆地理编码
        val enriched = enrichWithLocation(normalized)
        // 4. 写入 Room
        entryDao.insert(enriched.toEntity())
        // 5. 后台上传 OSS
        mediaUploader.enqueue(enriched)
    }
}
```

---

## 3. Agent-C2：云端服务

### 3.1 服务架构

```
                  ┌──────────────┐
                  │  Nginx/ALB   │
                  └──────┬───────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌────────┐ ┌────────┐ ┌──────────┐
        │ Auth   │ │ API    │ │ WebSocket│
        │ Service│ │ Gateway│ │ Gateway  │
        └────────┘ └───┬────┘ └────┬─────┘
                       │           │
              ┌────────┼─────┬─────┼──────────┐
              ▼        ▼     ▼     ▼           ▼
        ┌────────┐ ┌──────┐ ┌──────┐ ┌──────────┐
        │Journal │ │Match │ │Upload│ │Message   │
        │Service │ │Engine│ │Service│ │Broker    │
        └───┬────┘ └──┬───┘ └──┬───┘ └────┬─────┘
            │         │        │          │
            ▼         ▼        ▼          ▼
        ┌────────────────────────────────────┐
        │           PostgreSQL               │
        │  + Redis (Cache / Session)         │
        └────────────────────────────────────┘
            │
            ▼
        ┌──────────────────┐
        │  OSS (阿里云/AWS)  │
        │  + CDN            │
        └──────────────────┘
```

### 3.2 技术选型

| 服务 | 技术 | 说明 |
|------|------|------|
| API Gateway | Go (Gin/Fiber) 或 Node.js (Fastify) | RESTful API，JWT 中间件 |
| Auth Service | Go + JWT | 手机号验证码登录，签发/刷新 token |
| Journal Service | Go/Node | 日志 CRUD，时间线聚合查询 |
| Match Engine | Go (后台 Job) | 每日 00:00 批量计算匹配结果 |
| Upload Service | Go/Node + OSS SDK | 直传 OSS，返回预签名 URL |
| WebSocket | Go (gorilla/ws) | 实时 ASR 流式返回、Agent 对话 |
| 消息推送 | Firebase Admin SDK | 匹配通知、每日总结推送 |

### 3.3 PostgreSQL 核心表

```sql
-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_hash VARCHAR(64) UNIQUE NOT NULL,
    nickname VARCHAR(50),
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    last_active_at TIMESTAMPTZ
);

-- 每日日志（云端聚合版，比手机端 Room 更精简）
CREATE TABLE journals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    date DATE NOT NULL,
    summary TEXT,
    keywords JSONB,           -- ["咖啡", "望京", "桂花"]
    mood VARCHAR(20),
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, date)
);

-- 单条素材条目
CREATE TABLE entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    journal_id UUID REFERENCES journals(id),
    timestamp TIMESTAMPTZ NOT NULL,
    type VARCHAR(20) NOT NULL,
    source VARCHAR(20),
    media_url TEXT,
    thumbnail_url TEXT,
    duration_ms INT,
    transcription TEXT,
    location POINT,           -- PostGIS
    location_name VARCHAR(200),
    annotations JSONB,        -- Vision/NLP 结构化结果
    tags JSONB,               -- [{name: "咖啡", type: "ACTIVITY"}, ...]
    is_starred BOOLEAN DEFAULT FALSE,
    sync_status VARCHAR(20) DEFAULT 'active',
    expires_at TIMESTAMPTZ,   -- 30天后过期
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 匹配记录
CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id UUID REFERENCES users(id),
    user_b_id UUID REFERENCES users(id),
    match_date DATE NOT NULL,
    common_details JSONB,     -- [{type: "location", value: "望京"}, ...]
    ice_break_message TEXT,
    status VARCHAR(20) DEFAULT 'pending',  -- pending | accepted | skipped
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 破冰对话
CREATE TABLE ice_break_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID REFERENCES matches(id),
    sender_id UUID REFERENCES users(id),
    content TEXT NOT NULL,
    round INT DEFAULT 1,
    is_system_generated BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 动态流
CREATE TABLE feed_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    journal_id UUID REFERENCES journals(id),
    content TEXT,
    media_urls JSONB,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT now()
);
```

### 3.4 REST API 设计

```yaml
# ===== 用户 =====
POST   /api/v1/auth/login          # 手机号验证码登录 → JWT
GET    /api/v1/users/me             # 获取当前用户Profile
PUT    /api/v1/users/me             # 更新Profile

# ===== 日志 =====
GET    /api/v1/journals             # 列表 (query: from, to)
GET    /api/v1/journals/:date       # 单日详情
PUT    /api/v1/journals/:date/summary  # 更新AI总结
PUT    /api/v1/journals/:date/mood     # 更新情绪
PUT    /api/v1/journals/:date/public   # 设为公开

# ===== 条目 =====
POST   /api/v1/entries              # 上传素材 (multipart)
GET    /api/v1/entries/:id          # 详情
PUT    /api/v1/entries/:id/tags     # 更新标签
PUT    /api/v1/entries/:id/star     # 标星
DELETE /api/v1/entries/:id          # 删除

# ===== 匹配 =====
GET    /api/v1/matches/daily        # 今日推荐 (3条)
POST   /api/v1/matches/:id/accept   # 接受匹配
POST   /api/v1/matches/:id/skip     # 跳过匹配
POST   /api/v1/matches/:id/message  # 发送破冰消息

# ===== 动态流 =====
GET    /api/v1/feed                 # Feed 列表
POST   /api/v1/feed/:id/like        # 点赞
POST   /api/v1/feed/:id/comment     # 评论

# ===== AI (透传到 Agent-D) =====
POST   /api/v1/ai/summary           # 触发每日总结生成
POST   /api/v1/ai/social-copy       # 触发社交文案生成
GET    /api/v1/ai/summary/:date     # 获取生成的总结
GET    /api/v1/ai/social-copy/:id   # 获取生成的文案

# ===== WebSocket =====
WS     /ws/agent-chat               # Agent 实时对话 (ASR流式上传 + TTS下行)
WS     /ws/sync                     # 数据同步 (增量上传)
```

### 3.5 上传策略

```kotlin
// 手机端 MediaUploader
class MediaUploader {
    fun enqueue(entry: TimelineEntry) {
        when {
            isWifi() -> uploadNow(entry)
            isCellular() -> {
                if (entry.type == EntryType.PHOTO && entrySize > 5MB) {
                    deferToWifi(entry)  // 大图片延迟
                } else {
                    uploadNow(entry)    // 音频/文本立即上传
                }
            }
        }
    }

    // 30天素材生命周期
    fun scheduleCleanup() {
        // WorkManager PeriodicWork
        // DELETE FROM entries WHERE created_at < now() - INTERVAL '30 days' AND type IN ('PHOTO', 'AUDIO')
        // 保留 summary / transcription / tags（文本记录）
    }
}
```

---

## 4. Sprint 计划

### Sprint 0：基础设施搭建（3 天）

- [ ] **Agent-C1**：创建 `data` module，配置 Room + Hilt
- [ ] **Agent-C1**：定义全部 Entity + DAO + TypeConverter
- [ ] **Agent-C2**：搭建 Go 项目骨架（Gin/Fiber + PostgreSQL + Redis 驱动）
- [ ] **Agent-C2**：执行数据库 DDL，创建全部表
- [ ] **Agent-C2**：配置 Docker Compose（PostgreSQL + Redis + API 服务）
- [ ] **Agent-C2**：实现 Auth 基础接口 (`/login`, `/me`, JWT 中间件)

### Sprint 1：手机端数据层核心（5 天）

- [ ] **Agent-C1**：实现 `TimelineRepositoryImpl`
  - `getJournal(date)` → Room Flow 查询 + 实时观测
  - `getJournals(range)` → 日期范围查询
  - `addEntry` → 插入 + 触发 MaterialIngestion pipeline
- [ ] **Agent-C1**：实现 `MaterialIngestion` 素材聚合管线
  - 注册 Agent-A 的 `AudioReceiver.audioChunkFlow` 消费
  - 注册 Agent-A 的 `PhotoPipeline.photoFlow` 消费
  - 注册 Agent-A 的 `CommandChannel.inboundFlow` 按键事件
  - `MaterialNormalizer`：时间戳归一化、去重
  - GPS → POI 富化（调用逆地理编码 API）
- [ ] **Agent-C1**：实现 `MediaUploader`
  - WorkManager 后台上传队列
  - WiFi-only 大文件策略
  - 30 天清理定时任务
- [ ] **Agent-C1**：手动插入测试数据，验证 Room 查询 + Flow 观测链路

### Sprint 2：云端 Journal API（4 天）

- [ ] **Agent-C2**：实现 Journal CRUD API
  - `GET /journals` — 分页查询，支持日期范围
  - `GET /journals/:date` — 单日详情（含 entries 列表）
  - `PUT /journals/:date/summary` — 保存 AI 总结
- [ ] **Agent-C2**：实现 Entries API
  - `POST /entries` — 上传素材（图片/音频 multipart）
  - `PUT /entries/:id/tags` — 标签更新
- [ ] **Agent-C2**：实现 OSS 上传服务
  - 生成预签名 URL
  - 设置对象 30 天 TTL（Lifecycle Policy）
- [ ] **Agent-C1**：实现 `ApiService` Retrofit 接口 + `SyncManager` 离线队列
- [ ] 端到端验证：照片采集 → 本地 Room 存储 → 后台上传 OSS → 云端 API 返回

### Sprint 3：标签/情绪 + Feed（3 天）

- [ ] **Agent-C1**：实现 `AnnotationRepositoryImpl`
  - Tag CRUD + Mood 更新
- [ ] **Agent-C2**：实现 Feed API
  - `GET /feed` — 按时间降序，分页
  - `POST /feed/:id/like`
  - `POST /feed/:id/comment`
- [ ] **Agent-C1**：实现 `MatchRepositoryImpl`
  - 本地缓存匹配结果
  - 离线时可查看历史匹配

### Sprint 4：匹配服务（4 天）

- [ ] **Agent-C2**：实现 Match API
  - `GET /matches/daily` — 获取当日 3 个匹配
  - `POST /matches/:id/accept` — 接受
  - `POST /matches/:id/skip` — 跳过
  - `POST /matches/:id/message` — 发送破冰消息
- [ ] **Agent-C2**：实现 Ice Break 对话逻辑
  - 3 轮限制（超过 3 轮返回 `{"status": "limit_reached", "suggest_exchange_wechat": true}`）
  - 系统生成破冰语存储
- [ ] **Agent-C2**：实现 Firebase 推送服务
  - 每日匹配通知："今天有 3 个人和你去了同一个地方"
  - 每日总结通知："你的 7 月 23 日总结已生成"

### Sprint 5：WebSocket + 集成测试（3 天）

- [ ] **Agent-C2**：实现 `/ws/agent-chat`
  - 接收音频 chunk → 转发 Agent-D ASR 服务 → 返回实时转写文本
  - 管理连接状态（心跳 30s）
- [ ] **Agent-C2**：实现 `/ws/sync`
  - 增量同步：手机端上传 PENDING 条目
  - 冲突解决策略：服务端 last-write-wins
- [ ] 全链路集成测试：
  - 手机端 → 云端 → OSS 上传完整链路
  - 离线→在线同步
  - 并发压力测试（1000 用户匹配计算）
- [ ] API 文档输出（OpenAPI/Swagger）

---

## 5. 消息队列设计（Agent-C ↔ Agent-D 异步通信）

```yaml
# Redis Streams 或 RabbitMQ
streams:
  ai.summary.request:     # Agent-C → Agent-D: 触发每日总结
    fields: {user_id, date, entries_json}
  ai.summary.response:    # Agent-D → Agent-C: 总结结果
    fields: {user_id, date, summary, keywords}
  
  ai.social_copy.request: # Agent-C → Agent-D: 触发文案生成
    fields: {user_id, summary_id, platforms[]}
  ai.social_copy.response: 
    fields: {user_id, summary_id, copies: [{platform, text, suggested_photos}]}
  
  ai.match.request:       # Agent-C → Agent-D: 触发匹配计算
    fields: {user_id, date}
  ai.match.response:
    fields: {user_id, matches: [{matched_user_id, common_details[], ice_break}]}
```
