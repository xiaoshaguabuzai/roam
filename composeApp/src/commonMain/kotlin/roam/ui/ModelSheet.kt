/**
 * 模型与登录面板。
 */
package roam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 模型与登录：DSH 的模型选择在输入框上方，这里同样从输入区进入。
 * 只负责登录入口（WebView / 系统浏览器）；通道是否就绪由 AssistantDriver 提供。
 */
@Composable
fun ModelSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLogin: (LoginTarget) -> Unit,
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Dsh.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Dsh.border),
            modifier = Modifier.width(360.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("模型与登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dsh.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "复用网页版登录态作答，不产生按 token 的费用。",
                    fontSize = 12.sp,
                    color = Dsh.textTertiary,
                )

                Spacer(Modifier.height(14.dp))
                LoginTarget.entries.forEachIndexed { index, target ->
                    if (index > 0) {
                        HorizontalDivider(color = Dsh.borderSoft)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(target.label, fontSize = 13.sp, color = Dsh.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    tint = Dsh.textTertiary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("未登录", fontSize = 11.sp, color = Dsh.textTertiary)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(target.url, fontSize = 11.sp, color = Dsh.textTertiary, maxLines = 1)
                        }
                        OutlinedButton(onClick = { onLogin(target) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("登录", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "登录后 Cookie 保存在本机；网页通道接入前，这里仅完成登录动作。",
                    fontSize = 11.sp,
                    color = Dsh.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        color = Dsh.surfaceAlt,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("关闭", fontSize = 12.sp, color = Dsh.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
