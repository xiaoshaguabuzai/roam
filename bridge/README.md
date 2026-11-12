# FreeBridge

桌面端的免费通道网关：用 Playwright 驱动带持久登录态的 Edge，把网页版豆包包装成本地 HTTP 接口。
Android 端不需要它（手机用内置 WebView 直驱）。

## 用法

```powershell
cd bridge
npm install --registry=https://registry.npmmirror.com

# 方式一：桌面 App 里点「登录」（推荐）
#   App 会调用 /shutdown 关掉无头网关，再以可见模式启动 browser，直接登录即可

# 方式二：手动
node login.mjs   # 先停掉 server；登录完回车
node server.mjs
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
| POST | `/shutdown` | 关闭浏览器并退出，供桌面 App 切换「无头 ↔ 可见」 |

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
