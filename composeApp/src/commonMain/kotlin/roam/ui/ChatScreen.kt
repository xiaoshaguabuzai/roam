package roam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import roam.core.assistant.AssistantDriver
import roam.core.tool.ToolRegistry
import roam.core.memory.MemoryEngine
import roam.core.model.platformNow

@Composable
fun ChatScreen(engine: MemoryEngine, driver: AssistantDriver?, registry: ToolRegistry?, onLogin: (LoginTarget) -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember(engine, driver) { RoamStore(engine, scope, { platformNow() }, driver, registry) }
    var input by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }

    LaunchedEffect(store) { store.boot() }

    val now = remember(store.currentId) { platformNow() }

    MaterialTheme(typography = DshTypography) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Dsh.bg)) {
            val compact = maxWidth < 600.dp
            val wide = maxWidth >= 1000.dp
            val showSidebar = maxWidth >= 760.dp
            val dockedMemory = wide && store.showMemory
            val drawerMemory = !wide && store.showMemory

            // 宽屏默认展开记忆面板；窄屏保持关闭，避免一进 App 就盖住对话。
            LaunchedEffect(wide) { store.setMemory(wide) }

            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Row(Modifier.fillMaxSize()) {
                    if (showSidebar) {
                        Sidebar(store, now, Modifier.width(Dsh.sidebarWidth).fillMaxHeight())
                        VerticalDivider(color = Dsh.border)
                    }

                    Column(Modifier.weight(1f).fillMaxHeight().background(Dsh.surface).imePadding()) {
                        ChatHeader(store, compact)
                        TabBar(store)
                        Box(Modifier.weight(1f)) {
                            if (store.tab == 0) MessageList(store, now) else TraceList(store, now)
                        }
                        Composer(
                            store = store,
                            value = input,
                            onValueChange = { input = it },
                            onOpenModels = { showModels = true },
                            compact = compact,
                        )
                        StatusFooter(store, compact)
                    }

                    if (dockedMemory) {
                        VerticalDivider(color = Dsh.border)
                        MemoryPanel(store, Modifier.width(Dsh.memoryWidth).fillMaxHeight())
                    }
                }

                if (!wide) {
                    AnimatedVisibility(
                        visible = drawerMemory,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable { store.toggleMemory() })
                            MemoryPanel(
                                store,
                                Modifier.align(Alignment.CenterEnd).width(300.dp).fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                    .background(Dsh.surface),
                            )
                        }
                    }
                }
            }

            ModelSheet(
                visible = showModels,
                onDismiss = { showModels = false },
                onLogin = { target ->
                    showModels = false
                    onLogin(target)
                },
            )
        }
    }
}

@Composable
private fun ChatHeader(store: RoamStore, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            store.sessions.firstOrNull { it.id == store.currentId }?.title ?: "会话",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Dsh.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Dsh.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(Dsh.success))
        if (!compact) {
            Spacer(Modifier.width(6.dp))
            Text("记忆引擎就绪", fontSize = 11.sp, color = Dsh.textTertiary)
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = { store.toggleMemory() }) {
            Icon(Icons.Outlined.Folder, "记忆面板", tint = Dsh.textTertiary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { }) {
            Icon(Icons.Outlined.MoreHoriz, null, tint = Dsh.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TabBar(store: RoamStore) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TabItem("对话", store.tab == 0) { store.selectTab(0) }
            TabItem("轨迹", store.tab == 1) { store.selectTab(1) }
        }
        HorizontalDivider(color = Dsh.border)
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) Dsh.textPrimary else Dsh.textTertiary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.width(18.dp).height(2.dp).clip(CircleShape)
                .background(if (selected) Dsh.accent else Color.Transparent),
        )
    }
}
