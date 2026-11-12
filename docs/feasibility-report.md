# Roam 可行性验证报告（M0）

- 日期：2026-11-12（v1.1 修订：技术栈由「纯 Android + MCP」改为 Compose Multiplatform）
- 范围：方案中待验证假设，先做不用登录、不用真机就能做的部分
- 结论：**有条件 Go**。CMP 跨端、MCP 适配器、记忆层均已用真实代码验证；网页通道只验证到
  「可达 + 驱动点存在 + 访客态必被风控」，登录态驱动与端到端时延需要一次人工登录和一台真机才能收尾。

## 一、总览

| # | 假设 | 状态 | 关键数据 |
|---|---|---|---|
| E0 | 本机具备构建条件 | ✅ 通过（模拟器除外） | JDK 21.0.13 / Node 22.23.5 / Gradle 8.14 便携版就绪；Android SDK 28–37 已有；模拟器加速不可用、无真机 |
| E1 | 网页登录态可被稳定驱动 | ⚠️ 部分通过 | 两站 200，输入框就绪；**访客态一发消息即触发验证码**；登录态未测 |
| E2 | 无 Function Calling 也能可靠调工具 | ✅ 规则通过，模型侧待测 | 36/36 脏输出用例，25.3µs/次；真实模型输出命中率未测 |
| E3 | MCP Kotlin SDK 可在 Android 同构环境跑通 | ✅ 通过 | 连接 301ms；4 工具 list 10ms；4 次 call 6–15ms；缺参返回 `isError=true`；外部 HTTP 客户端 200 |
| E4 | 分层记忆优于每轮全量重放 | ✅ 结构验证通过 | 200 轮累计：全量 1,061,134 token；分层 105,326（9.9%）；分层+检索 168,384（15.9%）；早期事实 4/4 |
| E5 | 端到端体验可接受 | ⛔ 未执行 | 无真机、模拟器不可用 |
| E6 | 合规与账号风险可控 | ⚠️ 观察中 | 未登录即触发验证码，说明风控在 API 层就生效；无封号样本 |
| E7 | CMP 一套代码可同时产出 Android 与桌面 | ✅ 通过 | `commonMain` 同一份 UI 同时编译出 9.82MB APK 与桌面程序；首次构建 3m54s |

## 二、E0 环境

- 系统没有系统级 JDK/Node/Gradle/Python，已全部安装为 `.tools/` 便携版（gitignore），
  `setup-toolchain.ps1` 可幂等重建。
- Android SDK：`C:\Users\Administrator\AppData\Local\Android\Sdk`，platforms 28–37、build-tools 30–36、
  system-images（android-33/37 x86_64）。
- **模拟器不可用**：CPU 虚拟化固件未开启（`VirtualizationFirmwareEnabled=False`），
  且未安装 Android Emulator Hypervisor Driver。E5 必须用真机。
- 端口注意：8765 被百度输入法占用，实验改用 18765；Android 实现必须**先探测空闲端口**再监听。

## 三、E1 网页通道（部分通过）

### 已测

| 站点 | HTTP | DOMContentLoaded | 输入框 | 登录墙 | 访客发问结果 |
|---|---|---|---|---|---|
| 豆包 doubao.com/chat | 200 | 1391ms | contenteditable ✓ | 有「登录」按钮 + 首访下载推广弹窗 | **图像验证码**（“需要电力驱动的东西”） |
| 通义 → 千问 qianwen.com | 200（302 跳转） | 457ms | contenteditable ✓ | 有「登录」按钮 | **滑块验证码**（“拖动下方滑块完成验证”） |

截图（已归档到 `docs/evidence/`）：
[豆包访客图像验证码](evidence/e1-doubao-captcha.png)、[千问访客滑块验证码](evidence/e1-qianwen-captcha.png)。

### 判断

- 站点可访问、页面可渲染、输入框与消息区都能定位——**UI 驱动在技术上成立**。
- **访客态不可用**：一发送就进风控，说明「不登录也能白嫖」不成立；登录是硬前提。
  这也修正了方案里的一句乐观表述：不是「登录就有免费额度」，而是「登录态才能通过风控」。
- 登录态下的首字/整答时延、连续 30 条的成功率、选择器稳定性——**尚未测**，需要一次人工登录。
- 曾出现的 406 排查结论：与网页通道无关，是 E3 的服务端缺 `ContentNegotiation`，见下。

### 待补（后续已在 M1-3 落地）

登录态下的首字/整答时延、连续 30 条成功率、选择器稳定性，已由 M1-3 的真机通道验证覆盖：
结果见 README 的真机截图与 `docs/evidence/phone-real-answer.png`。实验脚本已从仓库移除，
需要重跑时可在 git 历史中找到当时的 `spikes/e1-web-driver`。

## 四、E2 工具协议（规则通过）

- 解析器（应用内 `AgentProtocol`）覆盖：Markdown 代码块、前后缀说明文字、尾逗号、
  中文引号/全角冒号、BOM、字段别名（`name`/`function`/`answer`）、`args` 为字符串、
  嵌套 JSON 字符串、数组包裹单个调用、多调用歧义、纯文本降级、空输出、截断 JSON。
- 结果：**36/36 通过**，平均 25.3µs；修复后成功 3 例；纯文本会优雅降级为「直接回答」而不是报错。
- 设计确认：数组里出现**多个不同工具调用**判为无效（避免静默只执行第一个、丢掉另一半意图），
  交回上层重试；单个调用宽容接受。
- **未测**：真实模型（豆包网页/GLM）输出这类格式的**命中率**。需要登录态或 API key 才能测，
  是 M1 第一周必须补的数字。若命中率 <85%，按方案切「GLM 规划 + 豆包成文」双脑。

## 五、E3 MCP on Android（通过）

环境：JDK 21 + Kotlin 2.2.21 + `io.modelcontextprotocol:kotlin-sdk` 0.9.0 + Ktor 3.2.3（CIO）。

自连自测（`gradle run`）：

```
[client] connected in 301ms, server=roam-android-mcp caps.tools=true
[client] tools/list: 4 个，10ms
[client] tools/call clipboard.read -> OK (15ms)
[client] tools/call calendar.events -> OK (6ms)
[client] tools/call apps.list -> OK (10ms)
[client] tools/call notifications.list -> OK (15ms)
[client] tools/call calendar.events (缺参) -> isError=true
[result] PASS
```

外部 HTTP 客户端（PowerShell `Invoke-WebRequest`，等价于任意 MCP 客户端）：

```
initialize: 200 sid=0c9ef5bc-...
tools/list: 200 → clipboard.read / notifications.list / calendar.events / apps.list
tools/call: 200 {"result":{"content":[{"text":"{\"apps\":[...]}","type":"text"}],"isError":false}}
```

### 两个必须写进实现的坑

1. **必须安装 `ContentNegotiation { json(McpJson) }`**。SDK 的 Streamable HTTP 响应是
   `JSONRPCMessage` 对象而非字符串，Ktor 在没有转换器时会把它降级成 **406 Not Acceptable**，
   表现为「服务端已建会话（响应头带 `mcp-session-id`）但 body 为空」。
2. **端口不能硬编码**：本机 8765 被百度输入法占用；Android 端要先 bind 探测空闲端口再监听，
   并把实际端口写进 `session` 表供 UI 与外部客户端发现。

结论：MCP 层在 Android 上可行；剩余风险只在 Android 运行时（前台服务、WebView 生命周期），
不是协议或 SDK 能力问题。

> v1.1 定位调整：改用 CMP 后，核心工具层是进程内 `ToolRegistry`，MCP 降级为**可选的对外适配器**
> （让桌面 MCP 客户端能调用手机/桌面工具）。本实验仍然有效，但不再是主链路。

## 六、E4 记忆策略（结构验证通过）

200 轮合成语料，第 10/40/90/150 轮植入 4 条事实（实验脚本已归档，数据保留）：

| 策略 | 累计 token | 均值/轮 | 峰值/轮 | 早期事实命中 |
|---|---|---|---|---|
| A 全量重放 | 1,061,134 | 5,306 | 10,644 | 4/4 |
| B 分层摘要 | 105,326 | 527 | 660 | 4/4 |
| C 分层 + 检索 | 168,384 | 842 | 996 | 4/4 |

- 分层把累计成本压到全量的 **9.9%**（加检索 15.9%），且通过 L3 事实保住了早期信息。
- 真实会话每轮更长（约 150 token），全量重放的劣势会进一步放大，且会先撞上网页通道的输入限制。
- 局限：这里的摘要器是确定性的抽取式模拟，衡量的是**信息留存**，不是模型回答准确率；
  真实准确率要等 E1 登录态或 GLM key 到位后补测（方案 E4 的 90% 门禁）。

## 七、E7 CMP 跨端冒烟（通过）

最小 Compose Multiplatform 工程（已归档）：`commonMain` 放同一份 UI，`androidMain` / `desktopMain`
各写一个平台入口。

```
plugins: kotlin(multiplatform) 2.2.21 + kotlin.plugin.compose 2.2.21
         + org.jetbrains.compose 1.9.3 + AGP 8.13.2
targets: androidTarget() + jvm("desktop")
```

结果（`gradle :composeApp:compileKotlinDesktop :composeApp:assembleDebug`）：

| 项 | 结果 |
|---|---|
| 构建结果 | **BUILD SUCCESSFUL**，首次 3m54s（含依赖下载），40 个 task |
| Android 产物 | `composeApp/build/outputs/apk/debug/composeApp-debug.apk`，**9.82 MB** |
| 桌面运行 | `gradle :composeApp:run` 成功起窗，进程标题 `Roam CMP`，[截图](evidence/e7-cmp-desktop.png) |
| 桌面打包 | `createDistributable` 卡在 `downloadWix`（GitHub release 超时），**留到 M3**，届时预置 WiX 或换镜像 |
| 共享代码 | `App.kt` 只写一份，两端渲染同一 UI |

### 踩到的坑（已写进方案）

1. **插件仓库**：`plugins {}` 的标记在 Maven Central 与 Google Maven 都有，`pluginManagement` 里加
   `google()` / `mavenCentral()` 即可，不必依赖 `plugins.gradle.org`（国内访问不稳）。
2. **AGP 与 Gradle**：AGP 8.13.2 需要 Gradle ≥ 8.13，本机 8.14 可用；JDK 21 满足要求。
3. **NDK 告警**：`ndk-bundle` 缺 `source.properties` 会刷 `[CXX1101]` 警告，不影响构建，M1 前删掉该空目录即可。
4. **版本锁**：Kotlin 2.2.21 ↔ Compose 编译器插件 2.2.21 ↔ CMP 1.9.3 必须同组升级，不能单独动一个。
5. **桌面打包依赖 WiX**：`createDistributable` 会去 GitHub release 拉 `wix311-binaries.zip`，本机超时；
   不影响开发运行（`:composeApp:run` 正常），M3 打包时预置 WiX 或改用镜像。

## 八、E5 / E6

- **E5 未执行**：无真机；模拟器因 CPU 虚拟化未开启不可用。需要插一台 Android 手机并开 USB 调试。
- **E6 部分观察**：未登录状态下发一条中性问题即触发验证码，说明风控在提交层生效，
  「高频自动提问」几乎必然被拦。缓解手段（低频、独立小号、熔断、网关形态）必须在 M1 就位，
  而不是等到上线前。

## 九、Go / No-Go 建议

**Go（有条件）**：

1. 架构成立：CMP 跨端、本地记忆层、MCP 适配器都已用真实代码验证，不依赖任何付费服务。
2. 网页通道成立但有前提：**必须登录**，且必须按「随时可能被风控/改版」来设计降级。
3. 记忆方案按修正版执行（分层 + 定期全量 consolidate），不要每轮全量重放。

**进 M1 前必须补的三件事**：

| 项 | 谁做 | 产出 |
|---|---|---|
| 登录态实测 30 条（成功率/首字/整答/验证码频率） | 需要你手动登录一次 | E1 第二阶段数据 |
| 真机端到端 20 条 | 需要你插手机并开调试 | E5 数据 |
| 真实模型工具调用命中率 100 条 | 登录态到位后自动跑 | E2 模型侧数据 |

任何一项不达标时的退路已经写进方案：E1 不达标 → 只用 PC 网关形态；E2 不达标 → 双脑；
E5 不达标 → 缩短链路、把重活移到网关。

## 十、复现方式

实验脚本（E1/E2/E3/E4/E7）已从当前仓库移除，避免把一次性验证代码混进产品构建；
需要复核时可从 git 历史取出。当前版本的构建与测试方式见 [README](../README.md) 的 Testing 一节。
