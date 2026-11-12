package roam.app

import androidx.compose.runtime.Composable
import roam.core.assistant.AssistantDriver
import roam.core.memory.MemoryEngine
import roam.core.tool.ToolRegistry
import roam.ui.ChatScreen
import roam.ui.LoginTarget

@Composable
fun App(engine: MemoryEngine, driver: AssistantDriver?, registry: ToolRegistry?, onLogin: (LoginTarget) -> Unit) {
    ChatScreen(engine, driver, registry, onLogin)
}
