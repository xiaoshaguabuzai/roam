/**
 * 工具抽象、参数规格与注册表。
 */
package roam.core.tool

import kotlinx.serialization.json.JsonObject

/** 工具声明：name/描述/参数 schema 会拼进给模型的提示词。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val dangerous: Boolean = false,
)

interface Tool {
    val spec: ToolSpec

    /** 返回给模型看的纯文本结果；抛异常会被注册表转成 error 文本。 */
    suspend fun execute(args: JsonObject): String
}

class ToolRegistry(private val tools: List<Tool>) {

    private val byName = tools.associateBy { it.spec.name }

    fun names(): Set<String> = byName.keys

    fun specs(): List<ToolSpec> = byName.values.map { it.spec }

    fun has(name: String): Boolean = byName.containsKey(name)

    suspend fun call(name: String, args: JsonObject): String {
        val tool = byName[name] ?: return "error: 未知工具 $name"
        return runCatching { tool.execute(args) }
            .getOrElse { "error: ${it.message ?: it::class.simpleName}" }
    }

    /** 给模型的工具清单 + 输出契约。 */
    fun instruction(): String = buildString {
        appendLine("你是一个运行在 Android 手机上的助手。凡是需要手机上的信息，必须调用工具，不要编造。")
        appendLine("输出规则：每轮只输出一个 JSON 对象，不要解释、不要 Markdown 代码块。")
        appendLine("调用工具：{\"tool\":\"工具名\",\"args\":{...}}")
        appendLine("直接回答：{\"final\":\"真正的回答内容\"}")
        appendLine("注意：final 里必须写真实答案，不要把 <回答> 这类占位符原样输出。")
        appendLine("示例：{\"final\":\"今天没有日程。\"}")
        appendLine("最多 3 步；拿到工具结果后尽快给出 final。")
        appendLine("可用工具：")
        specs().forEach { spec ->
            appendLine("- ${spec.name}：${spec.description}")
            appendLine("  参数 schema：${spec.parameters}")
        }
    }
}
