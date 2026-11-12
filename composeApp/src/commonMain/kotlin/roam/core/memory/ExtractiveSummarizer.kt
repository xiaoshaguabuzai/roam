package roam.core.memory

import roam.core.model.FactValue
import roam.core.model.Msg
import roam.core.model.estimateTokens

/**
 * 确定性抽取式摘要器：不调用任何模型。
 * 用途：
 *  1. 单测里复现「摘要漂移/重建」这类逻辑问题；
 *  2. 免费通道全部不可用时的离线兜底，保证记忆不断档。
 */
class ExtractiveSummarizer : Summarizer {

    private val keyPattern = Regex("密码|计划|偏好|工位|负责人|日期|发布|结论|风险|待办|电话|地址")

    override suspend fun summarizeSegment(
        sessionId: String,
        messages: List<Msg>,
        maxTokens: Int,
    ): SegmentSummary {
        val sentences = splitSentences(messages)
        val facts = sentences
            .filter { keyPattern.containsMatchIn(it) && it.length <= 80 }
            .distinct()
            .map { FactValue(key = keyOf(it), value = it, sourceMsgIds = messages.map { m -> m.id }) }

        val picked = if (facts.isNotEmpty()) facts.map { it.value } else sentences.take(3)
        return SegmentSummary(content = clamp(picked.joinToString("；"), maxTokens), facts = facts)
    }

    override suspend fun consolidate(
        sessionId: String,
        previous: String?,
        segments: List<String>,
        facts: List<FactValue>,
        maxTokens: Int,
    ): ConsolidatedSummary {
        val body = buildList {
            if (!previous.isNullOrBlank()) add(previous)
            addAll(segments)
        }.joinToString("；")

        val mergedFacts = facts.distinctBy { it.key }
        val content = clamp(body, maxTokens)
        return ConsolidatedSummary(content = content, facts = mergedFacts)
    }

    private fun splitSentences(messages: List<Msg>): List<String> =
        messages
            .flatMap { it.content.split('。', '；', '\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun keyOf(sentence: String): String {
        val hit = keyPattern.find(sentence)?.value ?: "要点"
        return hit
    }

    private fun clamp(text: String, maxTokens: Int): String {
        // 每 token 约 1.5 字符（与 estimateTokens 保持同一口径）
        val maxChars = (maxTokens * 1.5).toInt().coerceAtLeast(20)
        return if (text.length <= maxChars) text else text.take(maxChars)
    }
}
