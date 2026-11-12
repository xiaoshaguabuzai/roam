package roam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Composer(
    store: RoamStore,
    value: String,
    onValueChange: (String) -> Unit,
    onOpenModels: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = value.isNotBlank() && !store.isStreaming

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = Dsh.contentMax).fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 24.dp),
        ) {
            Surface(color = Dsh.surfaceAlt, shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)) {
                Column(Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 12.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp, max = 120.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                    if (canSend) {
                                        store.send(value)
                                        onValueChange("")
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Dsh.textPrimary),
                        cursorBrush = SolidColor(Dsh.accent),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        if (compact) "发消息…" else "给 Roam 发消息，内容会自动进入本地记忆",
                                        fontSize = 14.sp,
                                        color = Dsh.placeholder,
                                    )
                                }
                                inner()
                            }
                        },
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Dsh.surface)
                                .clickable { /* 附件入口：M2 接文件/剪贴板 */ },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = Dsh.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))

                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenModels)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (compact) store.channelLabel().substringBefore(" ·") else store.channelLabel(),
                                fontSize = 12.sp,
                                color = Dsh.textSecondary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Dsh.textTertiary, modifier = Modifier.size(14.dp))
                        }

                        Spacer(Modifier.weight(1f))

                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(if (store.isStreaming) Dsh.textPrimary else if (canSend) Dsh.accent else Dsh.border)
                                .clickable(enabled = canSend || store.isStreaming) {
                                    if (!store.isStreaming) {
                                        store.send(value)
                                        onValueChange("")
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (store.isStreaming) {
                                Icon(Icons.Filled.Stop, null, tint = Dsh.surface, modifier = Modifier.size(14.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Dsh.surface, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusFooter(store: RoamStore, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (compact) {
            Text(
                "${store.messages.size} 条 · ${store.pendingTokens} tok",
                fontSize = 11.sp,
                color = Dsh.textTertiary,
                maxLines = 1,
            )
            Text(
                "L1 ${store.l1Count} · L2 ${store.l2Count}",
                fontSize = 11.sp,
                color = Dsh.textTertiary,
                maxLines = 1,
            )
        } else {
            Text(
                "${store.messages.size} 条消息 · ${store.pendingTokens} tok 未压缩",
                fontSize = 11.sp,
                color = Dsh.textTertiary,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Dsh.success))
                Spacer(Modifier.width(6.dp))
                Text(
                    "L1 ${store.l1Count} · L2 ${store.l2Count} · 事实 ${store.facts.size}",
                    fontSize = 11.sp,
                    color = Dsh.textTertiary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(12.dp))
                Text("记忆已落盘", fontSize = 11.sp, color = Dsh.textTertiary, maxLines = 1)
            }
        }
    }
}
