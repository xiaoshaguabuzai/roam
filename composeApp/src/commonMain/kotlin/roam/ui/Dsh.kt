package roam.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 取自 DeepSeek Harness 实际渲染的界面（截图取色 + CSS 变量）：
 * 侧栏 #f9fafb、卡片白、用户气泡 #edf3fe、主色 #4176e6、描边 #e5e5e5。
 */
object Dsh {
    val bg = Color(0xFFF9FAFB)
    val surface = Color(0xFFFFFFFF)
    val surfaceAlt = Color(0xFFF7F7F7)
    val surfaceHover = Color(0xFFECEEF0)
    val selected = Color(0xFFECEEF0)
    val border = Color(0xFFE5E5E5)
    val borderSoft = Color(0xFFF0F0F0)

    val textPrimary = Color(0xFF0F1115)
    val textSecondary = Color(0xFF61666B)
    val textTertiary = Color(0xFF81858C)
    val placeholder = Color(0xFFADB2B8)

    val accent = Color(0xFF4176E6)
    val accentSoft = Color(0xFFEDF3FE)
    val userBubble = Color(0xFFEDF3FE)
    val success = Color(0xFF2E9E5B)
    val danger = Color(0xFFD5534D)

    val sidebarWidth = 264.dp
    val memoryWidth = 360.dp
    val contentMax = 780.dp
    val radiusSm = 8.dp
    val radius = 12.dp
    val radiusLg = 18.dp
}

val DshTypography = Typography(
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)
