# Roam 技术方案（v1.1 · Compose Multiplatform 全栈）

> 状态：已批准方向，M0 执行中
> 技术栈：Compose Multiplatform 1.9.3 + Kotlin 2.2.21 + AGP 8.13.2 + Gradle 8.14 + JDK 21
> 目标平台：Android 8.0+（minSdk 26）、Desktop（Windows/macOS/Linux），共享 `commonMain`
> 原则：**零模型费用、记忆本地可审计、失败必降级、绝不丢消息**

## 0. 相对 v1.0 的变更

| 项 | v1.0 | v1.1（CMP） |
|---|---|---|
| 技术栈 | 纯 Android（Kotlin + Compose） | **Compose Multiplatform**：Android 与桌面共用 UI/业务 |
| 工具层 | MCP 是核心 | 进程内 `ToolRegistry` 是核心；**MCP 降级为可选对外适配器** |
| 记忆存储 | Room（Android） | **SQLDelight**（KMP，Android + Desktop 同一套 SQL） |
| 网关 | 可选 | 桌面端可直接充当网关宿主，地位上升 |
| 不变 | — | 免费通道 driver 抽象、三层记忆算法、工具调用协议、验收标准 |

## 1. 定位与差异化

市面「手机 Agent」= 付费模型 API + 云端记忆 + 封闭工具链 + 订阅制。
Roam = **零边际模型成本** + **本地可审计记忆** + **一套 CMP 代码同时覆盖手机与桌面** + **可自托管**。

CMP 不是为了「跨平台而跨平台」，它解决三个具体问题：

1. **重活放桌面**：批量压缩、记忆重建、会话导出、调试面板都适合在桌面常驻运行；手机只做随身入口。
2. **免费通道双形态**：桌面上用 Playwright 持久化登录态最稳、最好热修；手机上用 WebView 直驱。两者实现同一个 `AssistantDriver` 接口，上层无感。
3. **一套记忆格式**：同一份 SQLDelight schema 在两端运行，导出/导入/迁移不需要两套实现。

### 非目标

- 不做 iOS（MCP Kotlin SDK 无 iOS 目标；当前无需求）。
- 不做无障碍自动化（本轮只读工具 + 应用内动作）。
- 不上架应用商店；不做多用户 SaaS；不接任何按 token 计费的模型。

## 2. 已核实的现状

| 项 | 结论 |
|---|---|
| 工作区 | `E:\roam`，已 `git init`，首轮 20 个文件已提交 |
| 文件权限 | 曾因 Windows ACL 导致命令无法启动，已修复（回滚脚本在 `E:\roam-acl-recovery\`） |
| 工具链 | 无系统级 JDK/Node/Gradle，已装便携版：JDK 21.0.13、Node 22.23.5、Gradle 8.14 |
| Android SDK | `C:\Users\Administrator\AppData\Local\Android\Sdk`，platforms 28–37，build-tools 30–36 |
| 模拟器 | CPU 虚拟化固件未开启 + 未装加速驱动 → **不可用**，端到端需真机 |
| CMP 版本 | Compose Multiplatform 1.9.3（2025-11-06）、Kotlin compose 编译器插件 2.2.21、AGP 8.13.2，插件标记在 Maven Central / Google Maven 均可解析（不依赖 Plugin Portal） |
| MCP Kotlin SDK | 0.9.0，KMP 目标：jvm / js / wasmJs / linux / macos / mingw，**无 iOS**，与「Android + 桌面」路线一致 |
| 存储 | SQLDelight 2.4.1（KMP，Android 用 `AndroidSqliteDriver`，桌面用 `JdbcSqliteDriver`） |
| 网络 | doubao.com / qianwen.com / bigmodel / 火山方舟 / Maven / Google Maven / GitHub 均可达 |

## 3. 总体架构

```
┌──────────────────── Compose Multiplatform · composeApp ────────────────────┐
│ commonMain                                                                  │
│   UI: Chat / Memory / Settings / Trace                                      │
│   AgentRuntime · ContextAssembler · ToolRegistry · AssistantRouter          │
│   MemoryEngine(SQLDelight) · CompressionScheduler · Redactor                │
│                                                                             │
│ androidMain                          desktopMain                            │
│   WebViewDriver（豆包/千问登录态）      GatewayDriver（FreeBridge HTTP）      │
│   剪贴板/通知/日历 actual              剪贴板/文件 actual                     │
│   前台服务 + 通知                      窗口 / 托盘 / 后台压缩                  │
│   MCP 适配器（可选，JVM）              MCP 适配器（可选，JVM）                 │
└───────────────┬─────────────────────────────────────────────────────────────┘
                │ 局域网 / 本机 HTTP
┌───────────────▼──────────────────┐   ┌────────────────────────────────────┐
│ FreeBridge（Node/TS，自托管）     │   │ 可被任意 MCP 客户端调用（可选）      │
│  · Playwright 持久 Chrome profile │   │  Claude Desktop / Cursor / Inspector │
│  · doubao / qwen / tongyi driver  │   └────────────────────────────────────┘
│  · OpenAI 兼容 /v1/chat/completions│
│  · MCP Streamable HTTP 反向代理    │
└───────────────────────────────────┘
```

运行形态：
- **手机单机**：WebViewDriver + 本地 SQLDelight + 只读工具。
- **桌面单机**：GatewayDriver（连 FreeBridge）或直接内嵌驱动 + 本地 SQLDelight + 更强的工具集（文件、剪贴板）。
- **两端共用**：同一份记忆 schema、同一套 prompt 协议、同一套压缩算法；跨端同步留到 Phase 3。

### 3.1 仓库结构

```
roam/
├─ composeApp/                 # CMP 应用（Android + Desktop）
│  ├─ src/commonMain/          # UI、AgentRuntime、MemoryEngine、Driver 接口、ToolRegistry
│  ├─ src/androidMain/         # WebViewDriver、系统能力 actual、前台服务、AndroidManifest
│  ├─ src/desktopMain/         # 桌面窗口、GatewayDriver、系统能力 actual、托盘
│  └─ src/commonTest/          # 记忆/协议/压缩的纯逻辑单测（两端共用）
├─ bridge/                     # Node/TS FreeBridge（Playwright + OpenAI 兼容 + MCP 代理）
├─ docs/                       # 方案、可行性报告、ADR
└─ scripts/                    # 便携工具链
```

### 3.2 模块职责

| 模块 | 职责 | 不做什么 |
|---|---|---|
| `AgentRuntime` | 会话状态机、工具循环、预算与超时、trace | 不直接触网、不碰数据库细节 |
| `AssistantRouter` | 通道健康度打分、粘性会话、熔断、降级 | 不解析工具调用 |
| `ContextAssembler` | 按 token 预算拼装上下文 | 不生成摘要 |
| `MemoryEngine` | 消息/摘要/事实持久化、压缩任务、检索、重建 | 不直接调模型（通过 Runtime 注入的摘要器） |
| `ToolRegistry` | 工具 schema、权限分级、执行、审计 | 不做业务编排 |
| `FreeBridge` | 登录态托管、选择器热更新、协议转换 | 不存用户记忆 |

## 4. 关键设计

### 4.1 免费通道抽象（不变）

```kotlin
interface AssistantDriver {
    val id: String                  // doubao-web / qwen-web / gateway / glm-free
    val caps: Caps                  // streaming, nativeToolCall, vision, ctxTokens
    suspend fun health(): Health
    fun chat(req: ChatRequest): Flow<Chunk>
}
```

| 实现 | 所在 source set | 说明 |
|---|---|---|
| `WebViewDriver` | androidMain | 前台服务持有隐藏 WebView，登录态持久化，注入脚本抓流式回答 |
| `GatewayDriver` | commonMain（HTTP 客户端） | 连 PC 上的 FreeBridge，选择器可热更新，最稳 |
| `FreeApiDriver` | commonMain | 官方免费额度（如 GLM-4-Flash），原生 Function Calling |
| `OnDeviceDriver` | 可选 | 端侧小模型离线兜底 |

> E1 实测修正：网页版**不是「登录就有免费额度」，而是「登录态才能过风控」**。
> 访客态一发消息就触发验证码（豆包图像验证码、千问滑块），登录流程是必需能力。

### 4.2 工具层：进程内优先，MCP 只做对外适配

- 核心是 `ToolRegistry`：每个工具声明 `name / description / JsonSchema / 权限级别 / 平台实现`，Runtime 直接调用，零协议开销、可单测。
- 工具调用协议（无原生 Function Calling 时）沿用文本契约：

```json
{"thought":"...","tool":"clipboard.read","args":{"maxChars":2000}}
```
```json
{"final":"给用户的回答"}
```

- 解析器规则已在 E2 验证：代码块、前后缀、尾逗号、中文引号、字段别名、`args` 字符串、数组包裹单调用、多调用歧义、纯文本降级。
- **落地补充（M1-4）**：只读工具先走 `IntentRouter` 本地意图匹配（剪贴板/应用/日历/通知），
  命中就执行并把结果回喂网页助手；未命中再依赖模型的 JSON 输出。原因是豆包网页并不总是遵守
  文本协议，纯靠提示词约束会让工具链路时灵时不灵。最多 3 步，结果以工具卡片展示。
- **MCP 适配器（可选）**：把同一个 `ToolRegistry` 导出为 MCP Server（`kotlin-sdk-server` + Ktor CIO，Streamable HTTP），让桌面 MCP 客户端调用手机/桌面的工具。只在 JVM 侧，不属于核心链路。
- 必须安装 `ContentNegotiation { json(McpJson) }`，否则 Ktor 会把 `JSONRPCMessage` 响应降级成 **406 Not Acceptable**（E3 实测）。
- 端口运行时探测（本机 8765 被百度输入法占用）。

### 4.3 记忆：三层压缩（沿用 v1.0，存储换 SQLDelight）

| 层级 | 内容 | 触发 | 目标长度 |
|---|---|---|---|
| L0 | 用户/助手/工具消息，append-only | 每轮 | 全量 |
| L1 | 分段摘要 + 抽取事实 | 未压缩消息 ≥ 4k token | ≤400 token |
| L2 | 会话滚动摘要（= 「全部会话总结压缩」） | 空闲 10min / 会话结束 / 每 4 条 L1 | ≤800 token |
| L3 | 用户画像与稳定事实 | L2 合并时 | ≤50 条 |

- **不在每轮重放全部会话**：E4 实测 200 轮全量重放累计 106 万 token，分层方案 10.5 万（9.9%），早期事实 4/4 保留。
- 上下文装配（预算 = 通道上限 × 0.6；网页 12k，原生 FC 32k）：
  `system+工具清单 → L3 事实 → L2 摘要 → 检索命中 L1/L0（FTS top-5）→ 最近 8 轮 L0`。
- L0 是唯一事实源；摘要可从 L0 重建；事实带 `source_msg_ids`。

### 4.4 平台能力：`expect/actual` 清单

| 能力 | androidMain | desktopMain |
|---|---|---|
| 剪贴板读取 | `ClipboardManager`（Android 10+ 限前台） | `Toolkit.getDefaultToolkit().systemClipboard` |
| 通知列表 | `NotificationListenerService`（需授权） | 不支持（返回空 + 能力标记） |
| 日历 | `CalendarContract`（需 READ_CALENDAR） | ICS 文件解析（可选） |
| 文件 | SAF（`ACTION_OPEN_DOCUMENT_TREE`） | `java.nio.file`（用户选定根目录） |
| 应用列表 | `PackageManager` | 无 |
| 分享 | `Intent.ACTION_SEND` | 系统分享/复制（可选） |

统一的 `PlatformCapabilities` 在启动时探测，缺失能力在 UI 上显式置灰，而不是运行时报错。

### 4.5 单轮数据流

```
输入 → L0 落库 → 装配上下文 → Router 选通道 → 流式输出
     → 解析工具调用 → ToolRegistry 执行（或 MCP 适配器）→ 结果落库+摘要 → 回喂（≤4 步）
     → 最终回答落库 → 异步压缩入队 → trace 落库
```

## 5. 数据模型（SQLDelight 草案）

```sql
CREATE TABLE session(
  id TEXT PRIMARY KEY, title TEXT, channel TEXT,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, state TEXT NOT NULL
);

CREATE TABLE message(
  id TEXT PRIMARY KEY, session_id TEXT NOT NULL, seq INTEGER NOT NULL,
  role TEXT NOT NULL, content TEXT NOT NULL, tokens INTEGER NOT NULL,
  meta TEXT, created_at INTEGER NOT NULL
);
CREATE INDEX ix_message_session_seq ON message(session_id, seq);

CREATE TABLE summary(
  id TEXT PRIMARY KEY, session_id TEXT NOT NULL, level INTEGER NOT NULL,
  start_seq INTEGER NOT NULL, end_seq INTEGER NOT NULL, content TEXT NOT NULL,
  tokens INTEGER NOT NULL, model TEXT, superseded_by TEXT, created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX ux_summary_span ON summary(session_id, level, start_seq, end_seq);

CREATE TABLE fact(
  id TEXT PRIMARY KEY, session_id TEXT, key TEXT NOT NULL, value TEXT NOT NULL,
  confidence REAL NOT NULL, source_msg_ids TEXT, updated_at INTEGER NOT NULL
);

CREATE TABLE tool_audit(
  id TEXT PRIMARY KEY, session_id TEXT, tool TEXT NOT NULL, args_digest TEXT,
  status TEXT NOT NULL, duration_ms INTEGER NOT NULL, egress INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
```

- 全文检索：优先 `FTS5`（Android 8+ 系统 SQLite 已带），用 migration 里的原生语句创建；不可用时退化为 `LIKE` + 关键词打分（`MemoryEngine` 里同一接口）。
- 驱动：`AndroidSqliteDriver` / `JdbcSqliteDriver`，由 `expect class DatabaseDriverFactory` 提供。

## 6. 压缩与装配伪代码（不变）

```
onMessagePersisted(session):
    if pending_tokens(session) >= 4000: enqueue(SEGMENT, session, span)
onIdle(session, 10min) or onSessionEnd(session):
    enqueue(CONSOLIDATE, session)

SEGMENT:    L0[span] -> {summary, facts[], open_questions[]} -> 写 L1、upsert facts
CONSOLIDATE: L2 + 未 superseded 的 L1 + facts -> 新 L2(≤800) + facts
            漂移自检：抽 3 条 L0 验证事实有据；旧摘要标 superseded
失败：指数退避 3 次；仍失败保留 pending，下次继续；永不阻塞聊天
```

## 7. 可行性验证

| 实验 | 状态 | 结论 |
|---|---|---|
| E1 网页登录态驱动 | ⚠️ 部分通过 | 两站可达、输入框就绪；访客态必触发验证码；登录态待人工登录后补测 |
| E2 工具协议解析 | ✅ 规则通过 | 36/36 脏输出用例，25.3µs/次；真实模型命中率待测 |
| E3 MCP 适配器 | ✅ 通过 | 官方 Kotlin SDK 0.9.0 端到端 PASS；已降级为可选适配器 |
| E4 记忆成本 | ✅ 结构验证 | 分层为全量的 9.9%，事实 4/4 保留 |
| E5 端到端 | ⛔ 未执行 | 无真机、模拟器不可用 |
| E7 CMP 跨端冒烟 | 🔄 进行中 | 同一 `commonMain` 能否同时产出 APK 与桌面程序 |

## 8. 里程碑

| 阶段 | 内容 | 工期 | 验收 |
|---|---|---|---|
| M0 | 工具链 + E1/E2/E3/E4/E7 + 报告 | 5–8 天 | 门禁有明确结论 |
| M1 | CMP 骨架（Android + Desktop）、聊天 UI、SQLDelight 记忆、豆包 WebViewDriver、4 个只读工具、压缩任务 | 12 天 | 两端都能跑通「问答 + 记忆」，桌面能导出/重建 |

> 实现进展：M1-1（骨架 + 记忆 + 7 个单测）、M1-2（DSH 风格 UI）、M1-3（豆包网页真实通道）
> 已完成并在真机验证；只读工具与压缩调度见 README 的 M1-4。
| M2 | 通义/千问 driver、Router 熔断、FreeBridge 网关、MCP 适配器、记忆面板 | 8 天 | 通道故障自动降级不丢消息 |
| M3 | 桌面打包（msi/dmg/deb）、Android 安装包、隐私脱敏面板、漂移自检 | 6 天 | 达到 DoD |

## 9. 风险与对策

| 风险 | 概率 | 对策 |
|---|---|---|
| 网页反爬/风控、验证码 | 高 | 真实 UA/WebView 指纹、低频、人机登录、熔断；桌面网关优先 |
| 选择器失效 | 高 | 版本化 + 多候选 + 运行时监控 + 桌面端热更新 |
| 账号被封/限流 | 中 | 自用小号、限速、不自动操作敏感动作 |
| 无 FC 导致工具错调 | 中高 | 严格 JSON 契约、修复重试、双脑开关、单步降级 |
| 摘要漂移 | 中 | 原文为源、事实带来源、可重建、定期全量 consolidate |
| 隐私数据外发 | 中 | 本地脱敏、`never_send` 名单、工具级授权、外发明示 |
| CMP 版本耦合 | 中 | Kotlin / Compose 编译器 / CMP 三者版本锁死在同一组（2.2.21 / 1.9.3）并在 CI 固定 |
| KMP 存储差异 | 中 | SQLDelight 两端驱动分开实现、同一 schema；桌面用 JDBC、Android 用系统 SQLite |
| 桌面端分发体积/签名 | 低 | `compose.desktop` 打包 + jpackage；先出 Windows，再 macOS/Linux |
| 模拟器不可用 | 高 | Robolectric/单测先行，真机验证 E5 |

## 10. 验收标准（DoD）

1. 同一 `commonMain` 同时构建出 Android APK 与桌面可执行程序，聊天 UI 行为一致。
2. 单轮 P50 ≤8s、P95 ≤45s（网关通道 P95 ≤20s）；通道故障自动降级且零丢消息。
3. 200 轮会话后，早期事实抽测 20 题准确率 ≥85%；摘要可从 L0 重建。
4. 工具调用端到端成功率 ≥85%；非法 JSON 不卡死会话。
5. 记忆可导出（Markdown/JSON）、可删除、可重建；两端同一份格式。
6. 出站审计无任何付费模型 API 域名；敏感数据脱敏测试集命中率 100%。

## 11. 假设

- 单人自用、侧载；一台 Windows PC 可选常开。
- 「免费」= 不产生按 token 费用；接受免费通道限流与无 SLA。
- 仅中文场景；不做多用户；不做 iOS；不做无障碍自动化。
