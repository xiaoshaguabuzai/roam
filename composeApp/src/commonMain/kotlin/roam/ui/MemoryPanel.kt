package roam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 右侧记忆面板：对应 DSH 右侧的文档面板，这里放 L2 摘要、事实与压缩状态。 */
@Composable
fun MemoryPanel(store: RoamStore, modifier: Modifier = Modifier) {
    Column(modifier.background(Dsh.surface)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("记忆", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dsh.textPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { store.rebuild() }) {
                Icon(Icons.Outlined.Refresh, "重建", tint = Dsh.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = Dsh.border)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("会话摘要")
            Spacer(Modifier.height(8.dp))
            Text(
                store.summary ?: "还没有生成摘要。消息累计到 4k token 后会自动压缩成分段摘要。",
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = if (store.summary == null) Dsh.textTertiary else Dsh.textPrimary,
            )

            Spacer(Modifier.height(22.dp))
            SectionTitle("事实 · ${store.facts.size}")
            Spacer(Modifier.height(8.dp))
            if (store.facts.isEmpty()) {
                Text("暂无。含「密码 / 计划 / 偏好 / 日期」等关键词的句子会被抽成事实。", fontSize = 12.sp, color = Dsh.textTertiary, lineHeight = 19.sp)
            } else {
                store.facts.forEach { fact ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fact.key, fontSize = 12.sp, color = Dsh.textSecondary, modifier = Modifier.width(64.dp))
                        Text(
                            fact.value,
                            fontSize = 12.sp,
                            color = Dsh.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("压缩状态")
            Spacer(Modifier.height(6.dp))
            StatRow("未压缩消息", "${store.pendingTokens} tok")
            StatRow("L1 分段摘要", "${store.l1Count} 条")
            StatRow("L2 会话摘要", "${store.l2Count} 条")

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { store.compressNow() }) {
                    Text("立即压缩", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { store.rebuild() }) {
                    Text("从原文重建", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, color = Dsh.textTertiary, letterSpacing = 0.6.sp)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Dsh.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = Dsh.textPrimary)
    }
}
