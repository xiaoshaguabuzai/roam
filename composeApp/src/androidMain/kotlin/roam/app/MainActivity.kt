/**
 * Android 入口：组装记忆引擎、工具注册表与 WebView 通道。
 */
package roam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import roam.assistant.DoubaoWebDriver
import roam.core.memory.ExtractiveSummarizer
import roam.core.memory.MemoryEngine
import roam.core.tool.ToolRegistry
import roam.db.RoamDatabase
import roam.tools.AppsTool
import roam.tools.CalendarTool
import roam.tools.ClipboardTool
import roam.tools.NotificationsTool

private const val SESSION_ID = "default"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val driver = AndroidSqliteDriver(RoamDatabase.Schema, applicationContext, "roam.db")
        val engine = MemoryEngine(
            db = RoamDatabase(driver),
            summarizer = ExtractiveSummarizer(),
            clock = { System.currentTimeMillis() },
        )
        if (!engine.sessionExists(SESSION_ID)) {
            engine.createSession(SESSION_ID, "默认会话", "android")
        }

        val tools = ToolRegistry(
            listOf(
                ClipboardTool(this),
                AppsTool(this),
                CalendarTool(this),
                NotificationsTool(this),
            ),
        )

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1001)
        }

        val webDriver = DoubaoWebDriver(this)

        setContent {
            Box(Modifier.fillMaxSize()) {
                // 通道 WebView：整屏布局保证页面以正常视口渲染，被上层 Compose 完全覆盖；
                // 触摸全部交给 Compose，WebView 只作为「后台浏览器」执行注入脚本。
                AndroidView(factory = { webDriver.webView }, modifier = Modifier.fillMaxSize())

                App(engine, webDriver, tools) { target ->
                    startActivity(
                        Intent(this@MainActivity, LoginActivity::class.java)
                            .putExtra("url", target.url)
                            .putExtra("title", target.label),
                    )
                }
            }
        }
    }
}
