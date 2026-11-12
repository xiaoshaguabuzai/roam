/**
 * 桌面入口：窗口、本地数据库与网关生命周期。
 */
package roam

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import roam.app.App
import roam.core.memory.ExtractiveSummarizer
import roam.core.memory.MemoryEngine
import roam.core.tool.ToolRegistry
import roam.db.RoamDatabase
import roam.desktop.DesktopClipboardTool
import roam.desktop.GatewayDriver
import java.io.File
import java.util.Properties

fun main() {
    val dbFile = File(System.getProperty("user.home"), ".roam/roam.db").apply { parentFile?.mkdirs() }
    // 带 schema 的构造函数会根据 PRAGMA user_version 自动建库/迁移
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties(), RoamDatabase.Schema)
    val engine = MemoryEngine(
        db = RoamDatabase(driver),
        summarizer = ExtractiveSummarizer(),
        clock = { System.currentTimeMillis() },
    )
    if (!engine.sessionExists("default")) {
        engine.createSession("default", "默认会话", "desktop")
    }

    // 已登录过（profile 存在）就静默拉起网关；首次使用由「登录」按钮引导
    maybeAutoStartBridge()

    application {
        val gateway = GatewayDriver()
        val tools = ToolRegistry(listOf(DesktopClipboardTool()))

        Window(
            onCloseRequest = {
                driver.close()
                exitApplication()
            },
            title = "Roam",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            App(engine, gateway, tools) {
                openBridgeLogin()
            }
        }
    }
}

/** 桌面端的登录发生在 FreeBridge 的持久 profile 里：点登录 = 确保「可见模式」网关在跑。 */
private fun openBridgeLogin() {
    val bridgeDir = findBridgeDir()
    if (bridgeDir == null) {
        info("找不到 bridge 目录。\n请在仓库根目录运行桌面 App，或手动执行：cd bridge; npm install; node server.mjs")
        return
    }

    val health = bridgeHealth()
    if (health?.ready == true) {
        info(
            "FreeBridge 已在运行。\n\n" +
                "请在它打开的浏览器窗口里扫码/手机号登录豆包，登录后回到 App 发消息即可。\n" +
                "如果看不到浏览器窗口，请关闭 server.mjs 后重新点「登录」——网关需要用可见模式启动才能登录。",
        )
        return
    }
    if (health != null) {
        // 进程在、浏览器已关（或页面异常）：先清理再以可见模式重启
        stopExistingBridge()
    }

    val node = findNode()
    if (node == null) {
        info(
            "没有找到 node。\n\n" +
                "请安装 Node 22，或把 node 加入 PATH，或设置环境变量 ROAM_NODE 指向 node.exe。\n" +
                "然后手动执行：\n  cd bridge\n  node login.mjs\n  node server.mjs",
        )
        return
    }

    val log = File(bridgeDir, "server.log")
    val started = runCatching {
        ProcessBuilder(node, "server.mjs")
            .directory(bridgeDir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .apply { environment()["ROAM_HEADLESS"] = "0" }
            .start()
    }.onFailure {
        info("启动 FreeBridge 失败：${it.message}\n\n可以手动执行：cd bridge; node server.mjs")
        return
    }.isSuccess

    if (!started) return

    // 等它真正就绪再提示，避免用户在服务还没起来时就去点
    val deadline = System.currentTimeMillis() + 20_000
    var ready = false
    while (System.currentTimeMillis() < deadline) {
        if (bridgeHealth()?.ready == true) {
            ready = true
            break
        }
        Thread.sleep(500)
    }

    if (ready) {
        info(
            "已启动 FreeBridge（可见模式，日志：bridge/server.log）。\n\n" +
                "请在弹出的浏览器窗口里扫码/手机号登录豆包。\n" +
                "登录后回到 App，通道会自动变成「豆包网关」。",
        )
    } else {
        info(
            "FreeBridge 启动超时。\n\n" +
                "请查看 bridge/server.log；常见原因是端口 8787 被占用或浏览器启动失败。\n" +
                "可以手动执行：cd bridge; node server.mjs",
        )
    }
}

private data class BridgeHealth(val ready: Boolean, val loggedIn: Boolean)

private fun bridgeHealth(): BridgeHealth? = runCatching {
    val conn = java.net.URL("http://127.0.0.1:8787/health").openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 800
    conn.readTimeout = 1200
    val text = conn.inputStream.bufferedReader().readText()
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
    BridgeHealth(
        ready = obj["ready"]?.jsonPrimitive?.booleanOrNull ?: false,
        loggedIn = obj["loggedIn"]?.jsonPrimitive?.booleanOrNull ?: false,
    )
}.getOrNull()

private fun bridgeShutdown() {
    runCatching {
        val conn = java.net.URL("http://127.0.0.1:8787/shutdown").openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 800
        conn.readTimeout = 1500
        conn.doOutput = true
        conn.outputStream.use { it.write(ByteArray(0)) }
        conn.inputStream.close()
    }
}

private fun bridgeHealthy(): Boolean = bridgeHealth()?.ready == true

private fun info(message: String) {
    javax.swing.JOptionPane.showMessageDialog(null, message, "Roam", javax.swing.JOptionPane.INFORMATION_MESSAGE)
}

private fun findBridgeDir(): File? {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    repeat(6) {
        val candidates = listOf(File(dir, "bridge"), File(dir, "composeApp/bridge"))
        candidates.firstOrNull { File(it, "server.mjs").isFile }?.let { return it }
        dir = dir?.parentFile
    }
    return null
}

private fun findNode(): String? {
    System.getenv("ROAM_NODE")?.takeIf { it.isNotBlank() && File(it).canExecute() }?.let { return it }

    System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.map { File(it, "node.exe") }
        ?.firstOrNull { it.canExecute() }
        ?.let { return it.absolutePath }

    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    repeat(6) {
        val candidate = File(dir, ".tools/node/node.exe")
        if (candidate.canExecute()) return candidate.absolutePath
        dir = dir?.parentFile
    }
    return null
}

private fun stopExistingBridge() {
    if (bridgeHealth() == null) return
    bridgeShutdown()
    val deadline = System.currentTimeMillis() + 8000
    while (System.currentTimeMillis() < deadline && bridgeHealth() != null) {
        Thread.sleep(300)
    }
}

/** 已登录过的用户：启动时静默拉起网关（可见模式，便于随时补登录），省掉手动开终端。 */
private fun maybeAutoStartBridge() {
    val existing = bridgeHealth()
    if (existing?.ready == true) return
    if (existing != null) stopExistingBridge()
    val dir = findBridgeDir() ?: return
    if (!File(dir, ".profile").isDirectory) return
    val node = findNode() ?: return
    runCatching {
        ProcessBuilder(node, "server.mjs")
            .directory(dir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(File(dir, "server.log")))
            .apply { environment()["ROAM_HEADLESS"] = "0" }
            .start()
    }
}
