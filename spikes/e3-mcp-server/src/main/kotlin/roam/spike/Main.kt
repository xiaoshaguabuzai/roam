package roam.spike

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

// 8765 在本机被百度输入法占用；端口必须可配置，Android 上也要先探测空闲端口再监听。
private val PORT = System.getenv("ROAM_MCP_PORT")?.toIntOrNull() ?: 18765
private val URL = "http://127.0.0.1:$PORT/mcp"

fun main(args: Array<String>) {
    val serverOnly = args.contains("--server-only")

    val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = PORT) {
        // SDK 的 Streamable HTTP 响应是 JSONRPCMessage 对象而不是字符串，
        // 没有 ContentNegotiation 时 Ktor 会把它降级成 406 Not Acceptable。
        install(ContentNegotiation) { json(McpJson) }
        mcpStreamableHttp("/mcp") { roamServer() }
    }.start(wait = false)
    println("[server] MCP Streamable HTTP listening on $URL")

    if (serverOnly) {
        Thread.currentThread().join()
    }

    val failures = runBlocking { selfTest() }
    engine.stop(300, 1000)
    exitProcess(if (failures.isEmpty()) 0 else 1)
}

private suspend fun selfTest(): List<String> {
    val failures = mutableListOf<String>()
    val http = HttpClient(ClientCIO) { install(SSE) }

    val t0 = System.nanoTime()
    val transport = StreamableHttpClientTransport(http, URL)
    val client = Client(Implementation(name = "roam-selftest", version = "0.1.0"))
    client.connect(transport)
    val connectMs = (System.nanoTime() - t0) / 1_000_000

    val caps = client.serverCapabilities
    println("[client] connected in ${connectMs}ms, server=${client.serverVersion?.name} caps.tools=${caps?.tools != null}")
    if (caps?.tools == null) failures += "server 未声明 tools 能力"

    val t1 = System.nanoTime()
    val tools = client.listTools().tools
    val listMs = (System.nanoTime() - t1) / 1_000_000
    println("[client] tools/list: ${tools.size} 个，${listMs}ms")
    tools.forEach { println("         - ${it.name}: ${it.description?.take(40)}") }
    if (tools.size != 4) failures += "期望 4 个工具，实际 ${tools.size}"

    val calls = listOf(
        Triple("clipboard.read", mapOf("maxChars" to 20), "redacted"),
        Triple("calendar.events", mapOf("from" to "2026-11-13", "to" to "2026-11-14"), "events"),
        Triple("apps.list", emptyMap<String, Any?>(), "apps"),
        Triple("notifications.list", mapOf("limit" to 3), "items"),
    )
    for ((name, argv, expect) in calls) {
        val t = System.nanoTime()
        val res = client.callTool(name, argv)
        val ms = (System.nanoTime() - t) / 1_000_000
        val text = res.content.firstOrNull()?.toString().orEmpty()
        val ok = res.isError != true && text.contains(expect)
        println("[client] tools/call $name -> ${if (ok) "OK" else "FAIL"} (${ms}ms) ${text.take(90)}")
        if (!ok) failures += "调用 $name 失败: $text"
    }

    // 必填参数缺失时，工具级错误必须回到调用方而不是断开连接
    val bad = client.callTool("calendar.events", mapOf("from" to ""))
    println("[client] tools/call calendar.events (缺参) -> isError=${bad.isError}")
    if (bad.isError != true) failures += "缺参场景应返回 isError=true"

    client.close()
    http.close()
    println(if (failures.isEmpty()) "[result] PASS" else "[result] FAIL ${failures}")
    return failures
}
