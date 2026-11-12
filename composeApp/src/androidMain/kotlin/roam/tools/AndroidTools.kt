/**
 * 只读手机工具：剪贴板、应用、日历与通知。
 */
package roam.tools

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import roam.core.tool.Tool
import roam.core.tool.ToolSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun param(type: String, desc: String): JsonObject = buildJsonObject {
    put("type", type)
    put("description", desc)
}

/** 读取系统剪贴板。Android 10+ 仅前台可读，正好符合本应用的使用场景。 */
class ClipboardTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "clipboard.read",
        description = "读取系统剪贴板最近一次复制的文本",
        parameters = JsonObject(emptyMap()),
    )

    override suspend fun execute(args: JsonObject): String {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = manager.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        return if (text.isNullOrBlank()) "剪贴板为空" else text.take(2000)
    }
}

/** 已安装且可启动的应用列表。 */
class AppsTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "apps.list",
        description = "列出手机上已安装且可启动的应用名称",
        parameters = JsonObject(emptyMap()),
    )

    override suspend fun execute(args: JsonObject): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val names = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
        return if (names.isEmpty()) "没有查询到应用" else names.take(30).joinToString("、")
    }
}

/** 查询未来 N 天的日历事件（需要 READ_CALENDAR）。 */
class CalendarTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "calendar.events",
        description = "查询未来几天内的日历事件；参数 days 为天数，默认 1",
        parameters = buildJsonObject {
            put("days", param("integer", "查询未来几天，默认 1"))
        },
    )

    override suspend fun execute(args: JsonObject): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "error: 未授权日历读取权限"
        }
        val days = args["days"]?.jsonPrimitive?.intOrNull ?: 1
        val start = System.currentTimeMillis()
        val end = start + days * 86_400_000L
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val out = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?",
            arrayOf(start.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            while (cursor.moveToNext()) {
                out += "${cursor.getString(0)} @ ${fmt.format(Date(cursor.getLong(1)))}"
            }
        }
        return if (out.isEmpty()) "未来 $days 天没有日历事件" else out.take(20).joinToString("\n")
    }
}

/** 最近通知（需要用户在系统设置里开启通知使用权）。 */
class NotificationsTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "notifications.list",
        description = "列出最近收到的通知（需要开启通知使用权）；参数 limit 默认 10",
        parameters = buildJsonObject {
            put("limit", param("integer", "最多返回条数，默认 10"))
        },
    )

    override suspend fun execute(args: JsonObject): String {
        if (!NotificationListener.isEnabled(context)) {
            return "error: 未开启通知使用权，请在系统设置里允许 Roam 读取通知"
        }
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 10
        val items = NotificationListener.recent.take(limit)
        return if (items.isEmpty()) "最近没有通知" else items.joinToString("\n") { "${it.app}：${it.title} ${it.text}" }
    }
}
