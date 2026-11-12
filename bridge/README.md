# FreeBridge

桌面端的免费通道网关：用 Playwright 驱动带持久登录态的 Edge，把网页版豆包包装成本地 HTTP 接口。
Android 端不需要它（手机用内置 WebView 直驱）。

## 用法

```powershell
cd bridge
npm install --registry=https://registry.npmmirror.com

# 1) 首次登录（此时必须先停掉 server，否则 profile 被占用）
node login.mjs
#    在弹出的 Edge 窗口里扫码/手机号登录豆包，完成后回终端按回车

# 2) 启动网关
node server.mjs
#    [bridge] listening on http://127.0.0.1:8787

# 3) 另开终端跑桌面 App
cd ..
.\gradlew.bat :composeApp:run
```

桌面 App 会自动轮询 `/health`（每 8 秒），输入框上方的 chip 会显示：

| 状态 | 含义 |
|---|---|
| `豆包网关 · 未启动` | server.mjs 没运行 |
| `豆包网关 · 未登录` | server 在跑，但 profile 里没有有效登录态 |
| `豆包网关` | 就绪，可以直接提问 |

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | `{ ready, busy, loggedIn, url }` |
| POST | `/ask` | body `{ prompt, question }`，返回 NDJSON：`{"delta":"..."}` … `{"done":true,"text":"..."}` 或 `{"error":"..."}` |

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `ROAM_BRIDGE_PORT` | `8787` | 监听端口 |
| `ROAM_HEADLESS` | `0` | 设为 `1` 隐藏浏览器窗口（登录时需要 `0`） |

## 已知限制

- 与手机端同一套 DOM 选择器，豆包改版会失效；
- 登录态保存在 `bridge/.profile`（已 gitignore），换机器需重新登录；
- `loggedIn` 同时检查 cookie 与 `from_logout` 标记，登出后会回到「未登录」；
- 热门问题会触发豆包风控，表现为错误提示而不是卡死。
