package roam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import roam.core.model.Msg
import roam.core.model.Role

@Composable
fun MessageList(store: RoamStore, now: Long, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val extra = (if (store.streamingText != null) 1 else 0) +
        (if (store.facts.isNotEmpty()) 1 else 0) +
        (if (store.lastTools.isNotEmpty()) 1 else 0)
    val itemCount = store.messages.size + extra

    LaunchedEffect(itemCount, store.streamingText?.length) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthInMax(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(store.messages, key = { it.id }) { message ->
                if (message.role == Role.USER) UserMessage(message, now) else AssistantMessage(message, store.assistantLabel())
            }
            store.streamingText?.let { text ->
                item("streaming") { StreamingMessage(text, store.assistantLabel()) }
            }
            if (store.lastTools.isNotEmpty()) {
                item("tool-calls") { ToolCallsCard(store.lastTools) }
            }
            if (store.facts.isNotEmpty()) {
                item("memory-card") {
                    MemoryToolCard(
                        factCount = store.facts.size,
                        l1 = store.l1Count,
                        l2 = store.l2Count,
                        preview = store.facts.take(3).map { it.key to it.value },
                    )
                }
            }
        }
    }
}

private fun Modifier.widthInMax(): Modifier =
    this.then(Modifier.fillMaxWidth()).then(Modifier.widthIn(max = Dsh.contentMax))

@Composable
private fun UserMessage(message: Msg, now: Long) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = Dsh.userBubble,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Dsh.textPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${relativeTime(now, message.createdAt)} · ${message.tokens} tok", fontSize = 11.sp, color = Dsh.textTertiary)
    }
}

@Composable
private fun AssistantMessage(message: Msg, label: String) {
    Column(Modifier.fillMaxWidth()) {
        AssistantHeader(label)
        Spacer(Modifier.height(8.dp))
        Text(message.content, fontSize = 14.sp, lineHeight = 23.sp, color = Dsh.textPrimary)
    }
}

@Composable
private fun StreamingMessage(text: String, label: String) {
    Column(Modifier.fillMaxWidth()) {
        AssistantHeader("正在生成 · $label")
        Spacer(Modifier.height(8.dp))
        Text(text + "▍", fontSize = 14.sp, lineHeight = 23.sp, color = Dsh.textPrimary)
    }
}

@Composable
private fun AssistantHeader(status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Dsh.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text("R", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Dsh.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text("Roam", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Dsh.textPrimary)
        Spacer(Modifier.width(8.dp))
        Text(status, fontSize = 11.sp, color = Dsh.textTertiary)
    }
}

/** 对应 DSH 消息流里的工具调用卡片：这里展示「记忆已更新」。 */
@Composable
private fun MemoryToolCard(factCount: Int, l1: Int, l2: Int, preview: List<Pair<String, String>>) {
    Surface(
        color = Dsh.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Dsh.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Dsh.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Memory, null, tint = Dsh.accent, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("记忆已更新", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Dsh.textPrimary)
                    Text("$factCount 条事实 · L1 $l1 · L2 $l2", fontSize = 11.sp, color = Dsh.textTertiary)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Dsh.textTertiary, modifier = Modifier.size(16.dp))
            }
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Dsh.borderSoft)
                preview.forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(key, fontSize = 12.sp, color = Dsh.textSecondary, modifier = Modifier.width(76.dp))
                        Text(
                            value,
                            fontSize = 12.sp,
                            color = Dsh.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 工具调用卡片：展示本轮实际执行过的手机能力。 */
@Composable
private fun ToolCallsCard(records: List<ToolCallRecord>) {
    Surface(
        color = Dsh.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Dsh.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Dsh.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Memory, null, tint = Dsh.accent, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("已调用 ${records.size} 个工具", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Dsh.textPrimary)
            }
            records.forEach { record ->
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Dsh.borderSoft)
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(record.name, fontSize = 12.sp, color = Dsh.textSecondary, modifier = Modifier.width(120.dp))
                    Text(
                        record.result.replace("\n", " "),
                        fontSize = 12.sp,
                        color = Dsh.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun TraceList(store: RoamStore, now: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthInMax(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("trace-head") {
                Text("会话轨迹", fontSize = 13.sp, color = Dsh.textSecondary)
            }
            items(store.trace) { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(event.kind, fontSize = 11.sp, color = Dsh.accent, modifier = Modifier.width(48.dp))
                    Text(event.text, fontSize = 13.sp, color = Dsh.textPrimary, modifier = Modifier.weight(1f))
                    Text(relativeTime(now, event.at), fontSize = 11.sp, color = Dsh.textTertiary)
                }
            }
            if (store.trace.isEmpty()) {
                item("trace-empty") {
                    Text("还没有轨迹。发一条消息，会看到写入、压缩、合并的事件。", fontSize = 13.sp, color = Dsh.textTertiary)
                }
            }
        }
    }
}
