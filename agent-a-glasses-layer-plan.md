# Agent-A 开发计划：眼镜端 + 连接层

> **角色**：一切与 Rokid 硬件/CXR SDK 打交道的代码，向上层提供干净抽象
> **技术栈**：Kotlin (Android), CXR-L SDK 1.0.4, CXR-S SDK, Jetpack Compose (仅调试 UI)
> **依赖**：无（独立开发，仅需 Rokid 眼镜真机）
> **交付物**：眼镜端 APK + 手机端 `cxr-core` 模块
> **工期**：4 周

---

## 1. 参考代码分析（CXRLSample 工程）

Agent-A 直接复刻 Sample (C:\Users\11150\Downloads\CXRLSample) 的架构模式，关键文件映射：


| Sample 源文件               | 对应本产品模块                       | 复用程度                                            |
| --------------------------- | ------------------------------------ | --------------------------------------------------- |
| `CXRLApplication.kt`        | `JournalApplication`                 | **几乎照搬**：全局 `sharedLink` + `isSessionReady`  |
| `CxrLinkConnectionHub.kt`   | `LinkConnectionHub`                  | **核心复用**：StateFlow 模式管理所有连接状态        |
| `CxrSessionGate.kt`         | `SessionGate`                        | **核心复用**：CUSTOMAPP 会话创建 + token 连接       |
| `CxrScenePhase` 枚举        | `SessionPhase`                       | 直接复用 Connecting/SceneNotReady/CapabilitiesReady |
| `CxrFeaturePolicy.kt`       | `FeaturePolicy`                      | 扩展为产品功能门控                                  |
| `AudioUsageViewModel.kt`    | 拆分为 M3.1 眼镜端 + M3.3 手机端     | PCM→WAV 转换逻辑直接复用                           |
| `PhotoUsageViewModel.kt`    | `PhotoCapturePipeline`               | JPEG 回调处理逻辑直接复用                           |
| `CustomCmdViewModel.kt`     | `CommandChannel`                     | Caps 编解码逻辑直接复用                             |
| `DeviceControlViewModel.kt` | `DeviceController`                   | 亮度/音量接口直接复用                               |
| `CxrSceneNavigation.kt`     | 删除（本产品不用 Sample 的路由模式） | —                                                  |
| SessionHubViewModel.kt      | 简化（不需要 CustomView 路径）       | 保留 CUSTOMAPP 分支                                 |

---

## 2. 模块拆分

```
cxr-core/                          ← Android Library Module
├── app/
│   └── JournalApplication.kt      ← 全局 Application，持有 sharedLink
├── link/
│   ├── LinkConnectionHub.kt       ← 连接状态总线 (StateFlow 集中管理)
│   ├── LinkSessionGate.kt         ← CXRLink 工厂 + 生命周期管理
│   └── LinkState.kt               ← 状态模型 (LinkState枚举, SessionPhase枚举)
├── auth/
│   └── AuthService.kt             ← 鉴权封装 (AuthorizationHelper)
├── session/
│   ├── SessionManager.kt          ← CUSTOMAPP 会话管理对外接口
│   ├── FeaturePolicy.kt           ← 能力门控规则
│   └── SessionModels.kt           ← 会话数据模型
├── pipeline/
│   ├── photo/
│   │   └── PhotoPipeline.kt       ← 拍照封装 (takePhoto + JPEG回调)
│   └── audio/
│       ├── AudioChunk.kt          ← 音频数据模型
│       └── AudioReceiver.kt       ← 手机端接收眼镜音频流
├── command/
│   ├── CommandChannel.kt          ← 自定义指令通道封装
│   └── CapsProtocol.kt            ← 通信协议定义 (通道名/字段约定)
├── device/
│   └── DeviceController.kt        ← 亮度/音量/设备信息
└── util/
    ├── ApkInstallAccess.kt        ← 从 Sample 照搬
    └── WavBuilder.kt              ← 从 AudioUsageViewModel 提取的 PCM→WAV
```

---

## 3. 核心接口（对外暴露）

### 3.1 `SessionManager` — Agent-B/C 的唯一入口

```kotlin
interface SessionManager {
    val linkState: StateFlow<LinkState>     // Idle → Connecting → LinkReady → SessionBuilt → Disconnected
    val glassState: StateFlow<GlassState>   // 设备信息/佩戴状态/电量
    suspend fun connect(token: String): Result<Unit>
    fun disconnect()
    fun installGlassesApp(apkPath: String, callback: (Boolean) -> Unit)
    fun startGlassesApp()
    fun stopGlassesApp()
}
```

### 3.2 `PhotoPipeline` — Agent-C 消费

```kotlin
interface PhotoPipeline {
    val photoFlow: SharedFlow<PhotoCapture>  // 每次拍照广播
    fun capture(width: Int = 2048, height: Int = 1536, quality: Int = 85)
}

data class PhotoCapture(
    val jpegBytes: ByteArray,
    val timestamp: Long
)
```

### 3.3 `AudioReceiver` — Agent-C 消费

```kotlin
interface AudioReceiver {
    val audioChunkFlow: SharedFlow<AudioChunk>  // VAD截断后的完整语音片段
    fun startListening()
    fun stopListening()
}

data class AudioChunk(
    val pcmData: ByteArray,
    val timestamp: Long,
    val durationMs: Int
)
```

### 3.4 `CommandChannel` — Agent-C 通过此通道与眼镜双向通信

```kotlin
interface CommandChannel {
    val inboundFlow: SharedFlow<CapsMessage>   // 眼镜→手机
    fun sendToGlasses(clientKey: String, caps: Caps)  // 手机→眼镜
}
```

---

## 4. 眼镜端 App 设计 (GlassesJournal)

独立 Android APK，包名 `com.journal.glasses`（需与 `CUSTOMAPP.packageName` 一致）。

### 架构

```
com.journal.glasses/
├── MainActivity.kt           ← 入口 Activity (CUSTOMAPP appStart 目标)
├── MainViewModel.kt          ← CXRServiceBridge 初始化 + subscribe
├── audio/
│   └── GlassesAudioCapture.kt  ← AudioRecord (16kHz/mono), VAD, PCM分帧
├── keys/
│   └── KeyEventRouter.kt     ← KeyReceiver + KeyType → 业务动作映射
├── display/
│   └── StatusScreen.kt       ← 眼镜端极简 Compose UI (仅状态/文字提示)
└── protocol/
    └── CapsProtocol.kt       ← 与手机端完全一致的协议定义
```

### 按键→动作映射


| 按键     | KeyType         | 业务动作                   | Caps 上报                               |
| -------- | --------------- | -------------------------- | --------------------------------------- |
| 单击镜腿 | `CLICK`         | 标记重要时刻 ⭐            | `{type: "moment_mark", timestamp: xxx}` |
| 双击镜腿 | `DOUBLE_CLICK`  | 快速语音笔记（不触发对话） | `{type: "quick_note_start"}`            |
| 长按镜腿 | `LONG_PRESS`    | 开始/停止与 Agent 对话     | `{type: "agent_talk_start/stop"}`       |
| 双指前滑 | `SWIPE_FORWARD` | 播放今日总结 TTS           | `{type: "play_summary"}`                |
| 双指后滑 | `SWIPE_BACK`    | 跳过/返回                  | `{type: "skip"}`                        |

### Caps 协议约定

```
通道名： 眼镜→手机: "journal_event"
字段1: eventType  (String)
字段2: timestamp  (Long)
字段3: metadata   (String, optional)

音频流通道: "audio_stream"
字段1: seq (Int)          // 帧序号
字段2: pcmChunk (byte[])  // 320 bytes PCM (10ms@16kHz/mono/16bit)
字段3: isFinal (Boolean)  // true=语音结束

手机→眼镜: "phone_cmd"
字段1: cmd (String)  // "show_status" | "vibrate" | "update_display"
字段2: text (String) // 可选显示文本
```

---

## 5. Sprint 计划

### Sprint 0：环境搭建（2 天）

- [ ]  从 Sample 复刻 `settings.gradle.kts` + `app/build.gradle.kts`（Maven 仓库 + `client-l:1.0.4`）
- [ ]  创建 `JournalApplication` + `AndroidManifest.xml`（minSdk 31, INTERNET 权限）
- [ ]  手机安装 Rokid AI App ≥ 1.9.0
- [ ]  验证 Gradle Sync + 编译通过
- [ ]  验证 `import com.rokid.cxr.link.CXRLink` 可用

### Sprint 1：鉴权 + 连接（3 天）

- [ ]  实现 `AuthService`：
  - `isRokidAppInstalled()` → `AuthorizationHelper.isRequiredRokidAppInstalled`
  - `requestAuth(permissions)` → `AuthorizationHelper.requestAuthorization`
  - `parseResult()` → `AuthorizationHelper.parseAuthorizationResult` 三分支处理
- [ ]  实现 `LinkConnectionHub`：
  - 照搬 Sample 的 `CxrLinkConnectionHub` 模式
  - `_cxrlConnected` / `_btConnected` StateFlow
  - `_deviceInfo` / `_wearingStatus` / `_brightness` / `_volume`
  - `syncApplicationSessionReady()` 逻辑
- [ ]  验证端到端鉴权：启动 App → 调用 Rokid AI 授权 → 拿到 token

### Sprint 2：CUSTOMAPP 会话 + 眼镜 App 骨架（4 天）

- [ ]  实现 `LinkSessionGate.createCustomAppSession(context, token)`：
  - `configCXRSession(CUSTOMAPP, packageName)`
  - `setCXRLinkCbk(LinkConnectionHub.linkCallback)`
  - `connect(token)`
- [ ]  实现 `SessionManager` 状态机：`Connecting → LinkReady → SessionBuilt`
- [ ]  创建眼镜端 App 骨架：
  - `MainActivity` (exported=true, Launcher intent-filter)
  - `MainViewModel` (`CXRServiceBridge.setStatusListener` + `subscribe`)
  - Gradle 依赖 `cxr-service-bridge`
- [ ]  实现手机端 App 安装流程：
  - `ApkInstallAccess` 权限校验（照搬 Sample）
  - `appUploadAndInstall` + IGlassAppCbk 回调
- [ ]  实现手机端 `appStart` 启动眼镜 App
- [ ]  验证双端链路：`onCXRLConnected(true) && onGlassBtConnected(true)` → `onOpenAppResult(true)`

### Sprint 3：拍照 + 音频采集管道（4 天）

- [ ]  实现 `PhotoPipeline`：
  - `setCXRImageCbk` 注册回调
  - `takePhoto(2048, 1536, 85)` 触发拍照
  - `onImageReceived` → 解码验证 → `SharedFlow.emit(PhotoCapture)`
  - `release()` 注销 callback（置为空实现）
- [ ]  实现眼镜端 `GlassesAudioCapture`：
  - `AudioRecord.Builder` (16kHz, CHANNEL_IN_MONO, PCM_16BIT)
  - VAD 端点检测（静音 1.5s 自动截断）
  - PCM 分帧 320 bytes/帧
  - `sendMessage("audio_stream", Caps, pcmChunk)`
- [ ]  实现手机端 `AudioReceiver`：
  - `subscribe("audio_stream")` 接收 → `SharedFlow.emit(AudioChunk)`
  - WAV 封装工具类（从 `AudioUsageViewModel.buildWavFromPcm` 提取）
- [ ]  验证：拍照获取 JPEG → 显示/保存成功
- [ ]  验证：说话 → 眼镜端采集 → 手机端收到 PCM → 转 WAV 可播放

### Sprint 4：自定义指令通道 + 按键上报（3 天）

- [ ]  实现 `CommandChannel`：
  - `setCXRCustomCmdCbk` 注册
  - `sendCustomCmd("rk_custom_client", Caps)` 发送
  - `onCustomCmdResult` 解析 Caps → `SharedFlow.emit`
- [ ]  实现眼镜端 `KeyEventRouter`：
  - 照搬 Sample `KeyReceiver` + `KeyType` 枚举
  - 按键→业务动作映射（1.4 节）
  - `sendMessage("journal_event", Caps)` 上报
- [ ]  验证：单击眼镜镜腿 → 手机端 `CommandChannel.inboundFlow` 收到 `{type: "moment_mark"}`
- [ ]  验证：长按镜腿 → 眼镜开始录音 → 手机收到 `{type: "agent_talk_start"}` + 后续音频流

### Sprint 5：设备控制 + 集成测试（2 天）

- [ ]  实现 `DeviceController`：
  - `setBrightness(0..15)` / `setVolume(0..15)`
  - `getGlassDeviceInfo()` → `onGlassDeviceInfo` 回调
- [ ]  全链路集成测试：
  - 鉴权 → 连接 → 安装眼镜App → 启动 → 拍照 ✅ → 录音 ✅ → 按键 ✅ → 自定义指令 ✅
- [ ]  异常路径测试：
  - 蓝牙断开重连
  - 眼镜 App crash 恢复
  - token 过期重新授权
- [ ]  输出 `cxr-core.aar` + `glasses-journal.apk` + 接口文档

---

## 6. 输出规范


| 产出                  | 格式                   | 说明                                                               |
| --------------------- | ---------------------- | ------------------------------------------------------------------ |
| `cxr-core`            | Android Library (.aar) | Agent-B/C 通过 Gradle 依赖接入                                     |
| `glasses-journal.apk` | APK                    | 眼镜端可安装应用                                                   |
| `CxrCoreApi.md`       | Markdown               | SessionManager/PhotoPipeline/AudioReceiver/CommandChannel 接口文档 |
| `CapsProtocol.md`     | Markdown               | 通道名称、字段顺序、数据类型约定                                   |

---

## 7. 与 Sample 的关键差异


| Sample 做法                            | Agent-A 改动                      | 原因                                |
| -------------------------------------- | --------------------------------- | ----------------------------------- |
| 支持 CustomView 和 CustomApp 双模式    | 只支持 CUSTOMAPP                  | 产品需要按键/音频/自定义指令全能力  |
| Sample 用`CxrSceneNavigation` 管理路由 | 删除，用接口回调代替              | 不依赖 Sample 的 Activity 路由结构  |
| Hub 内直接用 ViewModel 管理所有状态    | 封装为`SessionManager` 接口       | 对外隐藏实现细节                    |
| `takePhoto(1024, 768, 80)`             | 参数改为`(2048, 1536, 85)`        | 更高分辨率用于 AI 图像分析          |
| Audio 场景手动触发`startAudioStream`   | 改为眼镜端按键触发 + VAD 自动截断 | 无感采集，用户只需说话              |
| — (Sample 无 VAD)                     | 新增 VAD 端点检测                 | 替代 SDK 持续音频流模式，提高可用性 |
