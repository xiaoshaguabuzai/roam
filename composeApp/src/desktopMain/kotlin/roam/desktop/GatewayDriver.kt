/**
 * 桌面通道：通过 HTTP 连接 FreeBridge。
 */
package roam.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import roam.core.assistant.AssistantDriver
import roam.core.assistant.DriverStatus
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * 桌面通道：通过本机 FreeBridge（Playwright + 持久登录态）调用豆包网页。
 * 健康检查每 8 秒一次，避免在 Compose 组合里做阻塞网络请求。
 */
class GatewayDriver(private val baseUrl: String = "http://127.0.0.1:8787") : AssistantDriver {

    override val id: String = "doubao-bridge"
    override val label: String = "豆包网关"

    @Volatile
    private var running = false

    @Volatile
    private var loggedIn = false

    private val health = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "roam-bridge-health").apply { isDaemon = true }
    }

    init {
        health.scheduleWithFixedDelay({ refreshHealth() }, 0, 8, TimeUnit.SECONDS)
    }

    private fun refreshHealth() {
        runCatching {
            val conn = URL("$baseUrl/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 1200
            conn.readTimeout = 1500
            val text = conn.inputStream.bufferedReader().readText()
            val obj = Json.parseToJsonElement(text).jsonObject
            running = obj["ready"]?.jsonPrimitive?.booleanOrNull ?: false
            loggedIn = obj["loggedIn"]?.jsonPrimitive?.booleanOrNull ?: false
        }.onFailure {
            running = false
            loggedIn = false
        }
    }

    override fun status(): DriverStatus = when {
        !running -> DriverStatus.UNAVAILABLE
        loggedIn -> DriverStatus.READY
        else -> DriverStatus.NEED_LOGIN
    }

    override fun ask(
        prompt: String,
        question: String,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        Thread {
            try {
                val body = buildJsonObject {
                    put("prompt", prompt)
                    put("question", question)
                }.toString()

                val conn = URL("$baseUrl/ask").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 3000
                conn.readTimeout = 180_000
                conn.outputStream.use { it.write(body.toByteArray()) }

                if (conn.responseCode != 200) {
                    if (conn.responseCode == 409) {
                        ui { onError("网关正忙（上一轮还没结束），请稍后再试") }
                        return@Thread
                    }
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    ui { onError("网关返回 ${conn.responseCode}：${err.take(120)}") }
                    return@Thread
                }

                var finished = false
                conn.inputStream.bufferedReader().forEachLine { line ->
                    val obj = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
                        ?: return@forEachLine

                    obj["delta"]?.jsonPrimitive?.contentOrNull?.let { text -> ui { onDelta(text) } }
                    obj["error"]?.jsonPrimitive?.contentOrNull?.let { err ->
                        finished = true
                        ui { onError(err) }
                    }
                    obj["done"]?.jsonPrimitive?.booleanOrNull?.let { done ->
                        if (done) {
                            finished = true
                            val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            ui { onDone(text) }
                        }
                    }
                }
                if (!finished) ui { onError("网关连接中断") }
            } catch (e: Exception) {
                ui { onError(e.message ?: "网关请求失败") }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun ui(block: () -> Unit) {
        SwingUtilities.invokeLater(block)
    }
}
