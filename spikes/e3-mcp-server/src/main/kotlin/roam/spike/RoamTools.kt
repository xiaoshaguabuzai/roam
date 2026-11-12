package roam.spike

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * E3 工具集：只读、可在 Android 上真实实现（此处用固定样例数据模拟系统 API）。
 * Schema 尽量贴近 MCP 规范：每个参数带 type / description。
 */
fun roamServer(): Server = Server(
    serverInfo = Implementation(name = "roam-android-mcp", version = "0.1.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
    ),
) {
    addTool(
        name = "clipboard.read",
        description = "读取系统剪贴板最近一次复制的文本。敏感内容会在本地脱敏后再返回。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("maxChars") {
                    put("type", "integer")
                    put("description", "最多返回多少字符，默认 2000")
                }
            },
            required = emptyList(),
        ),
    ) { req ->
        val max = req.arguments?.get("maxChars")?.jsonPrimitive?.intOrNull ?: 2000
        val text = "E:\\roam 的构建产物已上传；验证码 123456 请勿外传"
        CallToolResult.success(
            buildJsonObject {
                put("text", text.take(max))
                put("truncated", text.length > max)
                put("redacted", true)
            }.toString(),
        )
    }

    addTool(
        name = "notifications.list",
        description = "列出最近的通知（应用名、标题、时间）。正文会先脱敏。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("sinceMinutes") {
                    put("type", "integer")
                    put("description", "只返回最近 N 分钟内的通知，默认 60")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "最多返回条数，默认 20")
                }
            },
            required = emptyList(),
        ),
    ) { req ->
        val limit = req.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
        val items = (1..limit).map {
            mapOf("app" to "微信", "title" to "第 $it 条通知", "at" to "10:0$it")
        }
        CallToolResult.success(buildJsonObject { put("items", items.toString()) }.toString())
    }

    addTool(
        name = "calendar.events",
        description = "查询指定时间范围内的日历事件。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("from") { put("type", "string"); put("description", "起始日期 yyyy-MM-dd") }
                putJsonObject("to") { put("type", "string"); put("description", "结束日期 yyyy-MM-dd") }
            },
            required = listOf("from", "to"),
        ),
    ) { req ->
        val from = req.arguments?.get("from")?.jsonPrimitive?.content
        if (from.isNullOrBlank()) {
            CallToolResult.error("缺少必填参数 from")
        } else {
            CallToolResult.success(
                """{"events":[{"title":"产品评审","start":"09:30","end":"10:30"},{"title":"面试","start":"14:00","end":"15:00"}]}""",
            )
        }
    }

    addTool(
        name = "apps.list",
        description = "列出已安装且可直接打开的应用。",
        inputSchema = ToolSchema(properties = JsonObject(emptyMap()), required = emptyList()),
    ) {
        CallToolResult.success("""{"apps":["微信","日历","飞书","浏览器"]}""")
    }
}
