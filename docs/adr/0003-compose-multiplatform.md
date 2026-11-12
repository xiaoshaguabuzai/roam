# ADR-0003：技术栈改用 Compose Multiplatform（Android + Desktop 共享）

- 状态：已采纳（2026-11-12）
- 变更：v1.0 方案是「纯 Android + MCP 工具层」，用户更正核心技术栈为 **CMP（Compose Multiplatform）**。
- 决策：
  1. UI 与业务层放 `commonMain`，Android 与 Desktop 共用；
  2. 工具层以进程内 `ToolRegistry` 为核心，MCP 降级为可选对外适配器；
  3. 记忆存储改用 SQLDelight（KMP），两端同一份 schema；
  4. 免费通道仍是可插拔 driver：手机 WebView、桌面 FreeBridge 网关。
- 理由：
  1. 这个产品的重操作（批量压缩、记忆重建、会话导出、调试面板）天然适合桌面常驻，手机只做随身入口；
     一套代码同时覆盖两处，比「Android 写完再补一个 Web 后台」省一倍工作量。
  2. 桌面是免费网页通道最稳的宿主（Playwright 持久登录态、可热修选择器），手机 WebView 作为补充。
  3. 与既有选型同栈：Kotlin、Ktor、kotlinx.serialization、MCP Kotlin SDK（jvm 目标）都能直接复用。
- 代价：
  1. Kotlin / Compose 编译器 / CMP 三者版本强耦合，必须锁死并写进构建脚本；
  2. 平台能力要写 `expect/actual`，桌面端部分能力（通知列表、应用列表）缺失，需要 UI 显式降级；
  3. KMP 存储需要两端驱动（AndroidSqliteDriver / JdbcSqliteDriver），SQLite 版本差异要测；
  4. 桌面打包（jpackage/msi）是新的发布面，M3 才处理。
- 明确不做：iOS（MCP Kotlin SDK 无 iOS 目标，且当前无需求）、Web/Wasm（SQLite 与 MCP Server 都没有清晰落地路径）。
- 备选方案与不选原因：
  - 纯 Android：桌面重活无处安放，免费通道调试困难。
  - Flutter：Dart 生态与本项目既有 Kotlin/Ktor/MCP 资产不兼容。
  - Web + React：免费通道驱动只能走服务端，手机端没有原生 WebView 登录态优势。
