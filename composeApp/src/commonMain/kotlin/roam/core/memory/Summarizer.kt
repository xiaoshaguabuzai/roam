/**
 * 摘要器接口：与具体实现解耦。
 */
package roam.core.memory

import roam.core.model.FactValue
import roam.core.model.Msg

data class SegmentSummary(
    val content: String,
    val facts: List<FactValue>,
)

data class ConsolidatedSummary(
    val content: String,
    val facts: List<FactValue>,
)

/**
 * 摘要器抽象：生产环境由免费问答助手（或 GLM-4-Flash）实现，
 * 测试与离线降级用 [ExtractiveSummarizer]。
 */
interface Summarizer {
    suspend fun summarizeSegment(
        sessionId: String,
        messages: List<Msg>,
        maxTokens: Int,
    ): SegmentSummary

    suspend fun consolidate(
        sessionId: String,
        previous: String?,
        segments: List<String>,
        facts: List<FactValue>,
        maxTokens: Int,
    ): ConsolidatedSummary
}
