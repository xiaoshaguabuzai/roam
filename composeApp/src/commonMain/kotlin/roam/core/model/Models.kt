/**
 * 领域模型：消息、会话、事实与工具调用记录。
 */
package roam.core.model

/** 消息角色。DB 里存字符串，便于迁移与人工排查。 */
enum class Role {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM;

    companion object {
        fun fromDb(value: String): Role = entries.firstOrNull { it.name == value } ?: USER
    }
}

/** 领域模型：与 SQLDelight 生成的实体解耦，避免 UI 层依赖数据库代码。 */
data class Msg(
    val id: String,
    val sessionId: String,
    val seq: Long,
    val role: Role,
    val content: String,
    val tokens: Int,
    val createdAt: Long,
)

/** 从对话中抽取出的稳定事实（L3）。 */
data class FactValue(
    val key: String,
    val value: String,
    val sourceMsgIds: List<String> = emptyList(),
    val confidence: Double = 0.6,
)

/**
 * 中文为主的粗略 token 估计：约 1.5 字符 / token。
 * 真实通道会用官方 tokenizer 校正，这里只需要一个稳定、可比较的预算单位。
 */
fun estimateTokens(text: String): Int =
    ((text.length / 1.5).toInt() + 1).coerceAtLeast(1)

/** 会话列表项。 */
data class SessionInfo(
    val id: String,
    val title: String,
    val channel: String,
    val updatedAt: Long,
)