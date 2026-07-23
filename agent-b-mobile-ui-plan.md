# Agent-B 开发计划：手机端 UI 前端

> **角色**：用户看到和触摸的一切，从数据展示到交互反馈的全链路前端  
> **技术栈**：Kotlin, Jetpack Compose, Navigation Compose, Coil, Hilt, Room  
> **依赖**：Agent-A (`cxr-core` 接口), Agent-C (`TimelineRepository` 接口), Agent-D (`AiService` 接口)  
> **交付物**：完整手机端 APK  
> **工期**：6 周  
> **策略**：前 3 周用 Mock 数据并行开发 UI，后 3 周对接真实接口

---

## 1. 工程架构

```
app/
├── Application.kt               ← 继承 Agent-A 的 JournalApplication
├── MainActivity.kt              ← 单 Activity + Compose Navigation
├── navigation/
│   ├── NavGraph.kt              ← 全局路由表
│   └── Screen.kt                ← 路由枚举
├── ui/
│   ├── theme/                   ← 产品设计系统 (颜色/字体/形状)
│   ├── components/              ← 通用组件库
│   │   ├── TimelineItem.kt      ← 时间线条目组件
│   │   ├── MoodPicker.kt        ← 情绪选择器
│   │   ├── TagEditor.kt         ← 标签编辑器
│   │   ├── MatchCard.kt         ← 匹配推荐卡片
│   │   ├── SocialCopyCard.kt    ← 社交文案卡片
│   │   ├── IceBreakBubble.kt    ← 破冰对话气泡
│   │   └── GlassStatusBar.kt    ← 眼镜连接状态指示器
│   ├── screen/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt       ← M-UI-1 今日日志流
│   │   │   ├── CalendarScreen.kt   ← 月历热力图
│   │   │   ├── MediaDetailScreen.kt ← 素材详情全屏
│   │   │   └── HomeViewModel.kt
│   │   ├── ai/
│   │   │   ├── SummaryScreen.kt    ← M-UI-2 每日总结卡片
│   │   │   ├── SocialCopyScreen.kt ← 三版文案 Tab
│   │   │   └── AiViewModel.kt
│   │   ├── match/
│   │   │   ├── MatchScreen.kt      ← M-UI-3 每日匹配推荐
│   │   │   ├── MatchDetailScreen.kt ← 共同细节可视化
│   │   │   ├── IceBreakScreen.kt   ← 破冰对话框
│   │   │   ├── FeedScreen.kt       ← 动态流
│   │   │   └── MatchViewModel.kt
│   │   ├── annotate/
│   │   │   ├── AnnotateScreen.kt   ← M-UI-4 标签/情绪/备注编辑
│   │   │   └── AnnotateViewModel.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt   ← M-UI-5
│   │       ├── ProfileScreen.kt
│   │       └── SettingsViewModel.kt
│   └── states/
│       ├── HomeUiState.kt
│       ├── MatchUiState.kt
│       └── AiUiState.kt
├── di/
│   └── AppModule.kt              ← Hilt 依赖注入模块
└── util/
    ├── DateFormatter.kt
    ├── ImagePicker.kt
    └── ClipboardHelper.kt
```

---

## 2. 页面详细设计

### 2.1 今日日志页（HomeScreen）— M-UI-1

**视觉结构**：
```
┌──────────────────────────────┐
│  🔗 眼镜已连接   🏷️ 今天      │  ← GlassStatusBar + 日期
├──────────────────────────────┤
│  ┌──────────────────────────┐│
│  │ 📷 12:03  望京soho        ││  ← TimelineItem (照片)
│  │ [图片缩略图]               ││
│  └──────────────────────────┘│
│  ┌──────────────────────────┐│
│  │ 🎙️ 12:18  语音笔记        ││  ← TimelineItem (音频)
│  │ "路过咖啡店闻到桂花香..."    ││     播放按钮 + 转写文本
│  └──────────────────────────┘│
│  ┌──────────────────────────┐│
│  │ ⭐ 12:25  重要时刻         ││  ← 眼镜按键标记，高亮
│  │ [照片] + "桂花拿铁很好喝"   ││
│  └──────────────────────────┘│
│  ┌──────────────────────────┐│
│  │ 💬 14:30  Agent对话        ││  ← Agent 对话摘要
│  │ 今天聊了周末计划...         ││
│  └──────────────────────────┘│
│                              │
│  [今天的AI总结卡片预览]         │  ← 底部常驻，点击进入 SummaryScreen
└──────────────────────────────┘
```

**交互**：
- `LazyColumn` 时间线，下拉刷新
- 点击 `TimelineItem` → `MediaDetailScreen`
- 左滑条目 → 快速添加标签
- FAB "+" 按钮 → 手动添加照片/备忘录
- 眼镜标记的瞬间 ⭐ 高亮动画

### 2.2 日历视图（CalendarScreen）

**视觉结构**：
```
┌──────────────────────────────┐
│  ←  2026年 7月  →            │
├──────────────────────────────┤
│  日  一  二  三  四  五  六    │
│       1   2  [3]  4   5   6  │  ← [3] 今天
│  [7] [8]  9  10  11  12  13  │  ← [7][8] 绿色=有日志，深浅表示丰富度
│  14  15  16  17  18  19  20  │
│  21  22  23  24  25  26  27  │
│  28  29  30  31              │
└──────────────────────────────┘
```

**交互**：
- 点击日期 → 跳转 HomeScreen 对应日期
- 长按日期 → 预览当日关键词
- 热力图：颜色深浅 = 当日素材数量

### 2.3 每日总结（SummaryScreen）— M-UI-2

**视觉结构**：
```
┌──────────────────────────────┐
│  2026年7月23日 周四            │
│                              │
│  ☕ 望京  🍂 桂花  😊 平静      │  ← 3-5个关键词标签
│       🏃 通勤  📝 工作         │
│                              │
│  今天又是平凡但有趣的一天。     │  ← 叙述性文字 (100-200字)
│  上午在望京SOHO处理工作，       │
│  中午路过楼下的咖啡店，         │
│  闻到了今年第一缕桂花香...      │
│                              │
│  [编辑总结]  [生成社交文案 →]   │
└──────────────────────────────┘
```

### 2.4 社交文案页（SocialCopyScreen）

**视觉结构**：
```
┌──────────────────────────────┐
│  [朋友圈] [小红书] [Instagram] │  ← 三Tab切换
├──────────────────────────────┤
│  推荐配图:                    │
│  [图1] [图2] [图3]  ←可替换   │
│                              │
│  ┌──────────────────────────┐│
│  │ "今天的快乐是桂花味儿的 🍂  ││  ← 文案预览区
│  │  在楼下闻到了今年第一缕桂花香││
│  │  咖啡店老板说他也闻到了。   ││
│  │  秋天真的来了。"           ││
│  └──────────────────────────┘│
│                              │
│  [📋 一键复制]  [✏️ 编辑]  [🔄 换一版]│
└──────────────────────────────┘
```

**交互**：
- "一键复制" → 系统剪贴板 + Toast "已复制"
- "编辑" → 文案变为可编辑 TextField
- "换一版" → 调用 AI 重新生成（显示 skeleton loading）
- 配图点击 → 从当日照片中选择替换

### 2.5 匹配推荐（MatchScreen）— M-UI-3

**视觉结构**：
```
┌──────────────────────────────┐
│  今日共鸣 (3)                  │
│                              │
│  ┌──────────────────────────┐│
│  │  🧑 @小明                 ││
│  │  ────────────────────────││
│  │  你们今天都在望京 ☕        ││  ← 共同细节
│  │  都标记了"平静"情绪         ││
│  │  都去了咖啡店              ││
│  │  ────────────────────────││
│  │  他也闻到了桂花香          ││  ← 补充细节
│  │                          ││
│  │  [跳过]        [打招呼 →] ││
│  └──────────────────────────┘│
│                              │
│  ┌──────────────────────────┐│  ← 第二张卡片
│  │  ...                     ││
│  └──────────────────────────┘│
│                              │
│  ┌──────────────────────────┐│  ← 第三张卡片
│  │  ...                     ││
│  └──────────────────────────┘│
└──────────────────────────────┘
```

**设计关键**：
- **不显示 头像/照片**（PRD 规定：非颜值导向匹配）
- 以共同细节为卡片主体，用 emoji + 文字
- 卡片支持左右滑动（但交互用按钮，不用 Tinder 滑卡模式）
- 每天固定 3 张

### 2.6 破冰对话（IceBreakScreen）

**视觉结构**：
```
┌──────────────────────────────┐
│  ←  与 @小明 的对话           │
│  匹配于：望京咖啡店            │
├──────────────────────────────┤
│                              │
│  🤖 "你也经常去那家咖啡店？    │  ← 系统生成的破冰语
│      他家的桂花拿铁很好喝 ☕"   │
│                              │
│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│  │ "对啊！你也是常客？"      │ │  ← 预制回复选项1
│  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│  │ "还没试过，下次尝尝"      │ │  ← 预制回复选项2
│  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│  │ "..."                   │ │  ← 跳过
│  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│                              │
│  剩余对话轮次: 2               │  ← 至多3轮
└──────────────────────────────┘
```

### 2.7 标签/情绪编辑器（AnnotateScreen）— M-UI-4

**视觉结构**：
```
┌──────────────────────────────┐
│  编辑这一刻 (12:25 望京咖啡店)  │
│  [照片缩略图]                  │
│                              │
│  情绪                         │
│  😊  😌  😤  😢  🥰  🤔     │  ← 6种情绪快捷选择
│  [自定义...]                  │
│                              │
│  标签                         │
│  [#咖啡] [#桂花] [#探店]      │  ← AI推荐标签
│  [+ 添加标签]                 │  ← 手动输入
│                              │
│  备注                         │
│  ┌──────────────────────────┐│
│  │ "桂花拿铁意外地好喝..."    ││  ← 自由文本
│  └──────────────────────────┘│
│                              │
│  [保存]                       │
└──────────────────────────────┘
```

### 2.8 设置（SettingsScreen）— M-UI-5

```
设置项列表：
├── 个人资料 (头像/昵称)
├── 眼镜管理
│   ├── 连接状态 (信号强度)
│   ├── 眼镜电量
│   ├── 固件版本
│   └── [断开连接]
├── 素材同步
│   ├── 自动同步开关
│   ├── WiFi-Only 开关
│   └── 同步频率
├── 隐私
│   ├── 动态流可见范围
│   └── 位置共享开关
├── 数据管理
│   ├── 素材保留说明 (30天)
│   ├── [导出日志]
│   └── [清除本地缓存]
└── 关于
```

---

## 3. 数据流设计

### 3.1 UI State 模型

```kotlin
// 主页状态
data class HomeUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val entries: List<TimelineEntryUi> = emptyList(),
    val summaryPreview: SummaryPreview? = null,
    val glassConnected: Boolean = false,
    val glassBattery: Int = 0
)

// 匹配状态
data class MatchUiState(
    val isLoading: Boolean = true,
    val dailyMatches: List<MatchCardUi> = emptyList(),
    val hasMore: Boolean = false
)

// AI 状态
data class AiUiState(
    val isGenerating: Boolean = false,
    val summary: SummaryUi? = null,
    val socialCopies: List<SocialCopyUi> = emptyList(),
    val selectedPlatform: SocialPlatform = SocialPlatform.WECHAT_MOMENTS
)
```

### 3.2 ViewModel 依赖注入

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,       // Agent-A
    private val timelineRepo: TimelineRepository,     // Agent-C
    private val aiService: AiService                  // Agent-D (via Agent-C)
) : ViewModel() {
    // ...
}
```

---

## 4. Sprint 计划

### Sprint 0：工程搭建 + 主题设计（3 天）

- [ ] 创建 Android 项目，配置 Hilt + Navigation Compose + Coil
- [ ] 建立 `dependencies {}` 引用 `cxr-core`（用本地 `.aar` 或 `:cxr-core` module）
- [ ] 设计系统：颜色板、字体（中文优先 Noto Sans SC）、组件间距规范
- [ ] 实现通用组件：`GlassStatusBar`, `TimelineItem`, `MoodPicker`, `TagEditor`
- [ ] 实现 `NavGraph` 骨架（占位 Screen + 路由跳转验证）

### Sprint 1：主页 & 日历（5 天）

- [ ] `HomeScreen` 完整实现：
  - `LazyColumn` + `TimelineItem` 列表
  - 四种条目类型渲染（照片/音频/标记/对话）
  - 下拉刷新 + 加载动画
- [ ] `CalendarScreen`：
  - 自定义 Compose 月历组件
  - 热力图颜色映射
- [ ] `MediaDetailScreen`：
  - 图片全屏（支持双指缩放）
  - 音频播放器（ExoPlayer WAV）
- [ ] Mock 数据：生成 7 天丰富时间表数据

### Sprint 2：AI 总结 & 文案页（4 天）

- [ ] `SummaryScreen`：
  - 关键词流式布局（`FlowRow`）
  - 叙事文字区域 + 编辑模式切换
- [ ] `SocialCopyScreen`：
  - 三 Tab 切换 + 内容滑动
  - 配图选择器（HorizontalPager）
  - 一键复制（ClipboardManager）+ Toast
- [ ] Skeleton Loading：AI 生成时的骨架屏动画

### Sprint 3：标签 & 情绪编辑（3 天）

- [ ] `AnnotateScreen`：
  - 情绪快捷选择（6 种表情动画）
  - AI 推荐标签列表（`FlowRow` + Chip）
  - 自定义标签输入
  - 备注 TextField
- [ ] 手势交互：左滑 TimelineItem → 展开快捷操作（加标签/标重要/删除）

### Sprint 4：匹配 & 社交（5 天）

- [ ] `MatchScreen`：
  - 3 张 MatchCard 纵向排列
  - 跳过/打招呼按钮
  - 空状态："今天的共鸣还在路上..."
- [ ] `MatchDetailScreen`：
  - 共同细节对比（类似 Git diff 双栏）
  - 破冰语入口按钮
- [ ] `IceBreakScreen`：
  - 聊天气泡 UI
  - 预制选项列表（3 选 1）
  - 轮次计数器 + 第 3 轮后引导交换微信
- [ ] `FeedScreen`：
  - 类似 Instagram 的信息流
  - 点赞动画 + 评论展开

### Sprint 5：设置 + 剩余页面 + 端到端集成（5 天）

- [ ] `SettingsScreen` 全部设置项
- [ ] `ProfileScreen` 头像/昵称编辑
- [ ] 替换 Mock 数据为真实接口调用（对接 Agent-A/C/D）
- [ ] `GlassStatusBar` 实时显示连接状态（观察 `SessionManager.linkState`）
- [ ] 错误处理：网络断开/蓝牙断开/服务不可用的 UI 降级
- [ ] 端到端测试：眼镜按键 → 时间线实时出现标记 ⭐ → 点击编辑标签 → AI 生成总结

---

## 5. UI/UX 规范

### 设计原则
- **隐私优先**：默认不公开展示任何内容，每项分享需主动确认
- **低调科技感**：深色主题为主（匹配眼镜使用场景），柔和绿色调（呼应 Rokid 品牌色）
- **零学习成本**：交互模式对标微信/Instagram 用户已有习惯
- **眼镜优先**：核心操作在眼镜端完成，手机端更多是"浏览/编辑/管理"

### 关键指标埋点
```kotlin
// 在 ViewModel 中埋关键行为
eventTracker.track("timeline_view", mapOf("entry_count" to entries.size))
eventTracker.track("social_copy_copy", mapOf("platform" to platform.name, "edited" to edited))
eventTracker.track("match_greet", mapOf("match_id" to matchId, "position" to position))
eventTracker.track("mood_select", mapOf("mood" to mood.name))
```
