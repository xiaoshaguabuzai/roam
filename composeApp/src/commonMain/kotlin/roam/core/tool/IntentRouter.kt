package roam.core.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class IntentToolCall(val name: String, val args: JsonObject)

/**
 * 本地意图兜底：网页助手不一定遵守 JSON 工具协议，常见的只读请求在这里直接命中，
 * 先取到真实数据再交给它组织回答。不是替代模型，而是让工具链路不被协议服从性卡死。
 */
object IntentRouter {

    fun match(text: String): IntentToolCall? {
        val t = text.lowercase()
        return when {
            t.contains("剪贴板") || t.contains("clipboard") || t.contains("复制的内容") ->
                IntentToolCall("clipboard.read", JsonObject(emptyMap()))

            (t.contains("应用") || t.contains("app")) &&
                (t.contains("列表") || t.contains("list") || t.contains("哪些") || t.contains("installed")) ->
                IntentToolCall("apps.list", JsonObject(emptyMap()))

            t.contains("日历") || t.contains("日程") || t.contains("calendar") || t.contains("event") ->
                IntentToolCall("calendar.events", buildJsonObject { put("days", 1) })

            t.contains("通知") || t.contains("notification") ->
                IntentToolCall("notifications.list", buildJsonObject { put("limit", 10) })

            else -> null
        }
    }
}
