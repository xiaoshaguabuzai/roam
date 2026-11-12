package roam.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import roam.core.assistant.AssistantDriver
import roam.core.assistant.DriverStatus
import roam.core.memory.MemoryEngine
import roam.core.model.FactValue
import roam.core.model.Msg
import roam.core.model.SessionInfo
import roam.core.tool.AgentOutput
import roam.core.tool.AgentProtocol
import roam.core.tool.IntentRouter
import roam.core.tool.ToolRegistry

/** 轨迹（对应 DSH 的「轨迹」页）：记录本地记忆发生了什么。 */
data class TraceEvent(val at: Long, val kind: String, val text: String)

data class ToolCallRecord(val name: String, val result: String)

/**
 * UI 状态与记忆引擎之间的薄控制器。
 * 模型通道尚未接入，助手回复由本地骨架流式模拟，用于验证交互与落库链路。
 */
class RoamStore(
    private val engine: MemoryEngine,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val driver: AssistantDriver? = null,
    private val registry: ToolRegistry? = null,
) {
    var sessions by mutableStateOf<List<SessionInfo>>(emptyList()); private set
    var counts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var currentId by mutableStateOf(""); private set
    var messages by mutableStateOf<List<Msg>>(emptyList()); private set
    var streamingText by mutableStateOf<String?>(null); private set
    var isStreaming by mutableStateOf(false); private set
    var summary by mutableStateOf<String?>(null); private set
    var facts by mutableStateOf<List<FactValue>>(emptyList()); private set
    var pendingTokens by mutableStateOf(0); private set
    var l1Count by mutableStateOf(0); private set
    var l2Count by mutableStateOf(0); private set
    var trace by mutableStateOf<List<TraceEvent>>(emptyList()); private set
    var tab by mutableStateOf(0); private set
    var showMemory by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var lastTools by mutableStateOf<List<ToolCallRecord>>(emptyList()); private set

    fun boot() {
        if (engine.sessions().isEmpty()) {
            engine.createSession("default", "默认会话", "local")
            push("会话", "创建默认会话")
        }
        currentId = engine.sessions().first().id
        refresh()
    }

    fun select(id: String) {
        if (id == currentId) return
        currentId = id
        trace = emptyList()
        refresh()
    }

    fun newSession() {
        val n = sessions.size + 1
        val id = "s-${clock()}"
        engine.createSession(id, "新会话 $n", "local")
        currentId = id
        trace = emptyList()
        push("会话", "新建会话 #$n")
        refresh()
    }

    fun selectTab(index: Int) { tab = index }

    fun toggleMemory() { showMemory = !showMemory }

    fun setMemory(open: Boolean) { showMemory = open }

    /** 输入框上方的通道标签：本地骨架 / 豆包网页（已登录/未登录）。 */
    fun channelLabel(): String = when {
        driver == null -> "本地骨架 · 未接模型"
        driver.status() == DriverStatus.READY -> driver.label
        else -> "${driver.label} · 未登录"
    }

    fun hasChannel(): Boolean = driver != null

    /** 助手消息头部标签：真实通道显示通道名，降级时显示本地骨架。 */
    fun assistantLabel(): String = driver?.label ?: "本地骨架"

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || isStreaming) return
        val sid = currentId

        engine.appendUser(sid, body)
        push("写入", "用户消息 → L0")
        refresh()

        if (driver != null && driver.status() == DriverStatus.READY) {
            startAgent(sid, body)
            return
        }

        scope.launch {
            isStreaming = true
            streamingText = ""
            val reply = buildReply(body)
            for (chunk in reply.chunked(2)) {
                streamingText = (streamingText ?: "") + chunk
                delay(14)
            }
            engine.appendAssistant(sid, reply)
            streamingText = null
            isStreaming = false
            finishTurn(sid)
        }
    }

    private fun startAgent(sid: String, question: String) {
        isStreaming = true
        streamingText = ""
        lastTools = emptyList()
        push("通道", "${driver?.label ?: "通道"} 开始回答")
        askStep(sid, question, 0, mutableListOf())
    }

    /** 工具循环：模型输出 tool → 执行 → 回喂结果；最多 3 步。 */
    private fun askStep(sid: String, question: String, step: Int, records: MutableList<ToolCallRecord>, retries: Int = 0) {
        val activeDriver = driver ?: return

        // 第 0 步先看本地意图：网页助手不一定遵守 JSON 协议，常见只读请求直接命中工具。
        if (step == 0 && records.isEmpty()) {
            val intent = IntentRouter.match(question)
            if (intent != null && registry?.has(intent.name) == true) {
                scope.launch {
                    val result = registry.call(intent.name, intent.args)
                    records += ToolCallRecord(intent.name, result)
                    lastTools = records.toList()
                    push("工具", "${intent.name} → ${result.take(24)}")
                    streamingText = ""
                    askStep(sid, question, step + 1, records)
                }
                return
            }
        }
        val instruction = registry?.instruction().orEmpty()
        val history = records.joinToString("\n") { "- ${it.name} 返回：${it.result.take(600)}" }
        val prompt = buildString {
            if (instruction.isNotBlank()) appendLine(instruction)
            appendLine("用户问题：$question")
            if (history.isNotBlank()) {
                appendLine("已完成的工具调用：")
                appendLine(history)
                appendLine("请基于结果给出 final；若信息不足可再调用工具（最多 3 步）。")
            }
        }

        activeDriver.ask(
            prompt = prompt,
            question = question,
            onDelta = { full -> streamingText = full },
            onDone = { raw ->
                if (!isStreaming) return@ask
                val output = AgentProtocol.parse(raw, registry?.names() ?: emptySet())
                val placeholder = output is AgentOutput.Final &&
                    output.text.trim().let { it == "<回答>" || it == "<answer>" || it == "回答" }
                if (placeholder && retries < 1) {
                    streamingText = ""
                    askStep(sid, question, step, records, retries + 1)
                    return@ask
                }
                when (output) {
                    is AgentOutput.ToolCall -> {
                        if (step >= 3 || registry == null || !registry.has(output.name)) {
                            finishAnswer(sid, raw, records)
                        } else {
                            scope.launch {
                                val result = registry.call(output.name, output.args)
                                records += ToolCallRecord(output.name, result)
                                lastTools = records.toList()
                                push("工具", "${output.name} → ${result.take(24)}")
                                streamingText = ""
                                askStep(sid, question, step + 1, records)
                            }
                        }
                    }

                    is AgentOutput.Final -> finishAnswer(sid, output.text, records)
                    is AgentOutput.Prose -> finishAnswer(sid, output.text, records)
                    is AgentOutput.Invalid -> finishAnswer(sid, raw, records)
                }
            },
            onError = { err ->
                if (!isStreaming) return@ask
                streamingText = null
                isStreaming = false
                engine.appendAssistant(sid, "豆包网页通道出错：$err")
                push("通道", "失败：$err")
                refresh()
            },
        )
    }

    private fun finishAnswer(sid: String, text: String, records: List<ToolCallRecord>) {
        streamingText = null
        isStreaming = false
        lastTools = records.toList()
        engine.appendAssistant(sid, text.ifBlank { "（通道没有返回内容）" })
        if (records.isNotEmpty()) push("工具", "本轮调用 ${records.size} 个工具")
        refresh()
        finishTurn(sid)
    }

    /** 一轮结束后再做压缩检查，避免阻塞回答流。 */
    private fun finishTurn(sid: String) {        scope.launch {
            if (engine.compactIfNeeded(sid)) {
                push("压缩", "生成 L1 分段摘要")
            }
            if (engine.summaryCount(sid, 1) >= 2) {
                engine.consolidate(sid)
                push("压缩", "合并 L1 → L2")
            }
            refresh()
        }
    }

    fun compressNow() {
        scope.launch {
            engine.compactIfNeeded(currentId)
            if (engine.summaryCount(currentId, 1) > 0) engine.consolidate(currentId)
            push("压缩", "手动触发一次压缩")
            refresh()
        }
    }

    fun rebuild() {
        scope.launch {
            engine.rebuild(currentId)
            push("重建", "从 L0 重算全部摘要")
            refresh()
        }
    }

    fun dismissNotice() { notice = null }

    private fun refresh() {
        sessions = engine.sessions()
        counts = sessions.associate { it.id to engine.messages(it.id).size }
        messages = engine.messages(currentId)
        summary = engine.latestSummaryText(currentId, 2)
        facts = engine.facts(currentId)
        pendingTokens = engine.pendingTokens(currentId)
        l1Count = engine.summaryCount(currentId, 1)
        l2Count = engine.summaryCount(currentId, 2)
    }

    private fun push(kind: String, text: String) {
        trace = (trace + TraceEvent(clock(), kind, text)).takeLast(50)
    }

    private fun buildReply(input: String): String {
        val n = messages.size + 1
        val hint = if (driver != null) {
            "当前通道未登录：点输入框上方的模型入口登录豆包网页后，这里会用真实回答。"
        } else {
            "接入网页通道后，这里会替换成真实回答。"
        }
        return "已记录「${input.take(32)}」。\n\n" +
            "这是本地骨架回复，不是模型输出：消息已作为第 $n 条写入本地 SQLite，" +
            "当前会话还有 $pendingTokens token 未压缩。$hint"
    }
}

fun relativeTime(now: Long, at: Long): String {
    val d = (now - at).coerceAtLeast(0)
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        else -> "${d / 86_400_000} 天前"
    }
}
