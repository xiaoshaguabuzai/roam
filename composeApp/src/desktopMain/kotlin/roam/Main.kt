package roam

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import roam.app.App
import roam.core.memory.ExtractiveSummarizer
import roam.core.memory.MemoryConfig
import roam.core.memory.MemoryEngine
import roam.db.RoamDatabase
import java.io.File
import java.util.Properties

private const val DEMO_SESSION = "demo"

fun main(args: Array<String>) {
    val demo = args.contains("--demo")

    val dbFile = File(System.getProperty("user.home"), ".roam/roam.db").apply { parentFile?.mkdirs() }
    // 带 schema 的构造函数会根据 PRAGMA user_version 自动建库/迁移
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties(), RoamDatabase.Schema)
    val engine = MemoryEngine(
        db = RoamDatabase(driver),
        summarizer = ExtractiveSummarizer(),
        clock = { System.currentTimeMillis() },
        // 演示模式把分段阈值调小，便于一次看到 L1/L2 与事实面板
        config = if (demo) MemoryConfig(segmentTokens = 120) else MemoryConfig(),
    )
    if (demo) seedDemo(engine)
    if (!engine.sessionExists("default")) {
        engine.createSession("default", "默认会话", "desktop")
    }

    application {
        Window(
            onCloseRequest = {
                driver.close()
                exitApplication()
            },
            title = "Roam",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            App(engine, null, null) { target ->
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(target.url)) }
            }
        }
    }
}

/** 仅用于本地验收 UI 的示例数据；正式使用不会自动写入。 */
private fun seedDemo(engine: MemoryEngine) {
    if (engine.sessionExists(DEMO_SESSION)) return
    engine.createSession(DEMO_SESSION, "演示会话", "local")
    val lines = listOf(
        "记住：我家 WiFi 密码是 roam-2026-wifi。",
        "我的发布计划定在 12 月 18 日，负责人是李工。",
        "我偏好回答先给结论，再给三条理由。",
        "我的工位在 3 号楼 502。",
        "第 5 条：会议纪要要压缩成三条要点，风险是依赖未就绪。",
        "第 6 条：下周把周报写短一点，先结论后数据。",
    )
    lines.forEach {
        engine.appendUser(DEMO_SESSION, it)
        engine.appendAssistant(DEMO_SESSION, "收到，已记下。")
    }
    runBlocking {
        engine.compactIfNeeded(DEMO_SESSION)
        if (engine.summaryCount(DEMO_SESSION, 1) > 0) engine.consolidate(DEMO_SESSION)
    }
}
