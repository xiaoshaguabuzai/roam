# M0 可行性实验

每个实验独立可跑，结论汇总到 [../docs/feasibility-report.md](../docs/feasibility-report.md)。

## 环境

本机没有系统级 JDK/Node/Gradle，全部使用 `.tools/` 便携版：

```powershell
.\scripts\setup-toolchain.ps1      # 幂等：缺什么装什么，并生成 .tools\env.ps1
. .\.tools\env.ps1                # 当前会话生效（JAVA_HOME / PATH / ANDROID_HOME）
```

## E1 网页登录态能否被驱动 —— `e1-web-driver/`

```powershell
cd spikes\e1-web-driver
npm install --registry=https://registry.npmmirror.com

node probe.mjs --mode probe --site doubao     # 无登录探测：可达性/登录墙/输入框/截图
node probe.mjs --mode probe --site tongyi
node guest.mjs doubao                          # 访客态直接提问，观察是否触发风控
node probe.mjs --mode login --site doubao      # 人工登录一次，保存 storageState
node probe.mjs --mode ask   --site doubao      # 用登录态问一句，测首字/整答时延
```

产物在 `out/`（截图 + JSON）与 `.auth/`（登录态，已 gitignore，**不要提交或分享**）。

## E2 无 Function Calling 的工具协议 —— `e2-tool-protocol/`

```powershell
node run.mjs
```

`protocol.mjs` 是解析器规则原型，`cases.mjs` 是真实脏输出用例集。
生产实现按同一规则移植到 Kotlin（`android/core-agent`）。

## E3 MCP on Android（JVM 同构验证） —— `e3-mcp-server/`

```powershell
. .\.tools\env.ps1
cd spikes\e3-mcp-server
gradle --console=plain run            # 起 server + 官方 Client 自连自测
gradle --console=plain run --args="--server-only"   # 只起服务，供 curl / 外部客户端连
```

验证：官方 `io.modelcontextprotocol:kotlin-sdk` 0.9.0 的 Streamable HTTP server/client、
4 个只读工具的 list/call、缺参时返回工具级错误而不掉连接。

## E4 记忆策略成本对比 —— `e4-memory/`

```powershell
node sim.mjs
```

对比「全量重放 / 分层摘要 / 分层+检索」的 token 成本与早期事实留存。
这是结构性模拟（确定性摘要器），真实回答准确率需接入登录态后补测。

## E7 CMP 跨端冒烟 —— `e7-cmp/`

```powershell
. .\.tools\env.ps1
cd spikes\e7-cmp
gradle --console=plain :composeApp:compileKotlinDesktop :composeApp:assembleDebug
gradle --console=plain :composeApp:createDistributable    # 桌面 app-image
```

验证：同一份 `commonMain` UI 同时编译出 Android APK 与桌面程序。
版本必须成组：Kotlin 2.2.21 ↔ compose 编译器插件 2.2.21 ↔ Compose Multiplatform 1.9.3 ↔ AGP 8.13.2。
`local.properties` 里的 `sdk.dir` 指向本机 Android SDK（已 gitignore）。
