/**
 * 文本工具协议：解析、修复与降级。
 */
package roam.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AgentOutput {
    data class ToolCall(val name: String, val args: JsonObject) : AgentOutput
    data class Final(val text: String) : AgentOutput
    data class Prose(val text: String) : AgentOutput
    data class Invalid(val reason: String) : AgentOutput
}

/**
 * 文本工具协议解析器（对应 E2 spike 的验证规则）：
 * 容忍代码块、前后缀、尾逗号、中文引号、字段别名；无法解析时优雅降级为直接回答。
 */
object AgentProtocol {

    fun parse(raw: String, knownTools: Set<String>): AgentOutput {
        val text = raw.trim()
        if (text.isEmpty()) return AgentOutput.Invalid("empty")

        val cleaned = text.replace("```json", "").replace("```", "").trim()

        // 根数组里出现多个调用：判无效，避免静默只执行第一个。
        if (cleaned.startsWith("[")) {
            runCatching { Json.parseToJsonElement(repair(cleaned)) }.getOrNull()
                ?.let { element ->
                    if (element is kotlinx.serialization.json.JsonArray && element.size > 1) {
                        return AgentOutput.Invalid("multiple_tool_calls")
                    }
                }
        }

        scanJsonObjects(cleaned).forEach { candidate ->
            val obj = runCatching { Json.parseToJsonElement(repair(candidate)).jsonObject }.getOrNull()
                ?: return@forEach

            val name = stringField(obj, "tool") ?: stringField(obj, "name") ?: stringField(obj, "function")
            if (name != null && name in knownTools) {
                val args = (obj["args"] ?: obj["arguments"] ?: obj["parameters"]) as? JsonObject
                return AgentOutput.ToolCall(name, args ?: JsonObject(emptyMap()))
            }

            val final = stringField(obj, "final") ?: stringField(obj, "answer")
            if (!final.isNullOrBlank()) return AgentOutput.Final(final)
        }

        return AgentOutput.Prose(cleaned)
    }

    private fun stringField(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** 扫描平衡的 JSON 对象（正确处理字符串与转义）。 */
    internal fun scanJsonObjects(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '{') { i++; continue }
            var depth = 0
            var inString = false
            var escaped = false
            var j = i
            while (j < text.length) {
                val c = text[j]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                } else when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            out += text.substring(i, j + 1)
                            i = j
                            break
                        }
                    }
                }
                j++
            }
            i++
        }
        return out
    }

    /** 常见小瑕疵的修复：尾逗号、中文引号、全角冒号。 */
    internal fun repair(raw: String): String = raw
        .replace('\uFEFF', ' ')
        .replace('“', '"')
        .replace('”', '"')
        .replace('：', ':')
        .replace(Regex(",\\s*([}\\]])"), "$1")
        .trim()
}
