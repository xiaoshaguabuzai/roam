package roam.core.memory

import roam.db.Fact as FactEntity
import roam.db.Message as MessageEntity
import roam.db.RoamDatabase
import roam.db.Summary as SummaryEntity
import roam.core.model.FactValue
import roam.core.model.Msg
import roam.core.model.Role
import roam.core.model.SessionInfo
import roam.core.model.estimateTokens

data class MemoryConfig(
    val segmentTokens: Int = 4_000,
    val summaryMaxTokens: Int = 400,
    val consolidateMaxTokens: Int = 800,
    val recentTurns: Int = 8,
    val contextBudget: Int = 12_000,
    val maxFacts: Int = 20,
    val retrievalK: Int = 5,
)

/**
 * 本地记忆引擎：L0 原文永存 → L1 分段摘要 → L2 会话滚动摘要 → L3 事实。
 *
 * 设计约束：
 *  - 所有摘要都可从 L0 重建（[rebuild]）；
 *  - 分段边界由「累计 token 达到阈值」唯一确定，增量压缩与重建走同一条规则，
 *    因此 rebuild 的结果可复现；
 *  - 压缩失败不回滚消息，原文永远在。
 */
class MemoryEngine(
    private val db: RoamDatabase,
    private val summarizer: Summarizer,
    private val clock: () -> Long,
    private val config: MemoryConfig = MemoryConfig(),
) {

    // ---------- 会话与消息 ----------

    fun createSession(id: String, title: String, channel: String) {
        val now = clock()
        db.sessionQueries.create(id, title, channel, now, now, "active")
    }

    fun sessions(): List<SessionInfo> =
        db.sessionQueries.all().executeAsList().map {
            SessionInfo(it.id, it.title ?: it.id, it.channel ?: "", it.updated_at)
        }

    fun facts(sessionId: String): List<FactValue> =
        db.factQueries.bySession(sessionId, config.maxFacts.toLong()).executeAsList()
            .map { FactValue(it.fact_key, it.value_, it.source_msg_ids?.split(',') ?: emptyList(), it.confidence) }
    fun sessionExists(id: String): Boolean =
        db.sessionQueries.byId(id).executeAsOneOrNull() != null

    fun messages(sessionId: String): List<Msg> =
        db.messageQueries.bySession(sessionId).executeAsList().map { it.toDomain() }

    fun append(sessionId: String, role: Role, content: String, meta: String? = null): Msg {
        require(content.isNotBlank()) { "消息内容不能为空" }
        val now = clock()
        val seq = db.messageQueries.lastSeq(sessionId).executeAsOne() + 1
        val tokens = estimateTokens(content)
        val id = "${role.name.lowercase()}-$sessionId-$seq"
        db.transaction {
            db.messageQueries.insert(id, sessionId, seq, role.name, content, tokens.toLong(), meta, now)
            db.sessionQueries.touch(now, sessionId)
        }
        return Msg(id, sessionId, seq, role, content, tokens, now)
    }

    fun appendUser(sessionId: String, content: String): Msg = append(sessionId, Role.USER, content)

    fun appendAssistant(sessionId: String, content: String): Msg = append(sessionId, Role.ASSISTANT, content)

    fun pendingTokens(sessionId: String): Int {
        val covered = db.summaryQueries.maxCovered(sessionId).executeAsOne()
        return db.messageQueries.sumTokensAfter(sessionId, covered).executeAsOne().toInt()
    }

    // ---------- 压缩 ----------

    /** 未压缩消息达到阈值时切一段 L1。返回是否发生了压缩。 */
    suspend fun compactIfNeeded(sessionId: String): Boolean {
        if (pendingTokens(sessionId) < config.segmentTokens) return false
        segment(sessionId)
        return true
    }

    /** 取「从已覆盖位置开始、累计 token 达到阈值」的一段消息，作为唯一的分段规则。 */
    suspend fun segment(sessionId: String): SummaryEntity? {
        val covered = db.summaryQueries.maxCovered(sessionId).executeAsOne()
        val pending = db.messageQueries.afterSeq(sessionId, covered).executeAsList()
        if (pending.isEmpty()) return null

        val chunk = mutableListOf<MessageEntity>()
        var acc = 0
        for (m in pending) {
            chunk += m
            acc += m.tokens.toInt()
            if (acc >= config.segmentTokens) break
        }

        val result = summarizer.summarizeSegment(
            sessionId = sessionId,
            messages = chunk.map { it.toDomain() },
            maxTokens = config.summaryMaxTokens,
        )

        val start = chunk.first().seq
        val end = chunk.last().seq
        val id = "l1-$sessionId-$start-$end"
        val now = clock()
        val tokens = estimateTokens(result.content)
        val entity = SummaryEntity(id, sessionId, 1, start, end, result.content, tokens.toLong(), "extractive", null, now)

        db.transaction {
            db.summaryQueries.upsert(id, sessionId, 1L, start, end, result.content, tokens.toLong(), "extractive", null, now)
            result.facts.forEach { upsertFact(sessionId, it, now) }
        }
        return entity
    }

    /** 把当前所有 L1 合并成新的 L2，并标记旧摘要被取代。 */
    suspend fun consolidate(sessionId: String): SummaryEntity? {
        val segments = db.summaryQueries.activeByLevel(sessionId, 1L).executeAsList()
        if (segments.isEmpty()) return null

        val previous = db.summaryQueries.latestActive(sessionId, 2L).executeAsOneOrNull()
        val facts = db.factQueries.bySession(sessionId, config.maxFacts.toLong()).executeAsList()
            .map { FactValue(it.fact_key, it.value_, it.source_msg_ids?.split(',') ?: emptyList(), it.confidence) }

        val result = summarizer.consolidate(
            sessionId = sessionId,
            previous = previous?.content,
            segments = segments.map { it.content },
            facts = facts,
            maxTokens = config.consolidateMaxTokens,
        )

        val start = segments.first().start_seq
        val end = segments.last().end_seq
        val id = "l2-$sessionId-$start-$end"
        val now = clock()
        val tokens = estimateTokens(result.content)
        val entity = SummaryEntity(id, sessionId, 2, start, end, result.content, tokens.toLong(), "extractive", null, now)

        db.transaction {
            db.summaryQueries.upsert(id, sessionId, 2L, start, end, result.content, tokens.toLong(), "extractive", null, now)
            previous?.let { db.summaryQueries.supersede(id, it.id) }
            segments.forEach { db.summaryQueries.supersede(id, it.id) }
            result.facts.forEach { upsertFact(sessionId, it, now) }
        }
        return entity
    }

    fun summaryCount(sessionId: String, level: Int): Int =
        db.summaryQueries.activeByLevel(sessionId, level.toLong()).executeAsList().size

    fun factCount(sessionId: String): Int =
        db.factQueries.bySession(sessionId, config.maxFacts.toLong()).executeAsList().size

    fun latestSummaryText(sessionId: String, level: Int): String? =
        db.summaryQueries.latestActive(sessionId, level.toLong()).executeAsOneOrNull()?.content

    /** 丢弃所有派生数据，从 L0 重新压缩。用于验证漂移与修复。 */
    suspend fun rebuild(sessionId: String) {
        db.summaryQueries.deleteForSession(sessionId)
        db.factQueries.deleteForSession(sessionId)
        while (segment(sessionId) != null) {
            if (summaryCount(sessionId, 1) >= 4) consolidate(sessionId)
        }
        consolidate(sessionId)
    }

    // ---------- 上下文装配 ----------

    fun assembleContext(sessionId: String, query: String, budget: Int = config.contextBudget): String {
        val facts = db.factQueries.bySession(sessionId, config.maxFacts.toLong()).executeAsList()
        val l2 = db.summaryQueries.latestActive(sessionId, 2L).executeAsOneOrNull()
        val recent = db.messageQueries.recent(sessionId, config.recentTurns.toLong()).executeAsList().reversed()
        val retrieved = if (query.isBlank()) {
            emptyList()
        } else {
            db.messageQueries.search(sessionId, query, config.retrievalK.toLong()).executeAsList()
        }

        val factsBlock = facts.joinToString("\n") { "- ${it.fact_key}: ${it.value_}" }
        val recentBlock = recent.joinToString("\n") { "${it.role}: ${it.content}" }

        fun build(withFacts: Boolean, withL2: Boolean, withRetrieved: Boolean): String = buildString {
            if (withFacts && factsBlock.isNotEmpty()) append("【长期事实】\n").append(factsBlock).append("\n\n")
            if (withL2 && l2 != null) append("【会话摘要】\n").append(l2.content).append("\n\n")
            if (withRetrieved && retrieved.isNotEmpty()) {
                append("【相关片段】\n")
                append(retrieved.reversed().joinToString("\n---\n") { "${it.role}: ${it.content}" })
                append("\n\n")
            }
            if (recentBlock.isNotEmpty()) append("【最近对话】\n").append(recentBlock)
        }.trim()

        // 优先级：最近对话 > 事实 > L2 > 检索片段；超出预算就从低优先级开始丢。
        var text = build(true, true, true)
        if (estimateTokens(text) > budget) text = build(true, true, false)
        if (estimateTokens(text) > budget) text = build(true, false, false)
        if (estimateTokens(text) > budget) text = build(false, false, false)
        return trimToBudget(text, budget)
    }

    private fun trimToBudget(text: String, budget: Int): String {
        if (estimateTokens(text) <= budget) return text
        var chars = (budget * 1.5).toInt().coerceAtLeast(1)
        var out = text.takeLast(chars)
        while (estimateTokens(out) > budget && chars > 1) {
            chars = (chars * 0.9).toInt().coerceAtLeast(1)
            out = text.takeLast(chars)
        }
        return out
    }

    // ---------- 导出 ----------

    fun exportMarkdown(sessionId: String): String = buildString {
        val session = db.sessionQueries.byId(sessionId).executeAsOneOrNull()
        append("# ").append(session?.title ?: sessionId).append("\n\n")
        db.messageQueries.bySession(sessionId).executeAsList().forEach { m ->
            append("## ").append(m.role).append(" · seq=").append(m.seq).append("\n\n")
            append(m.content).append("\n\n")
        }
        val l2 = db.summaryQueries.latestActive(sessionId, 2L).executeAsOneOrNull()
        if (l2 != null) append("## 会话摘要\n\n").append(l2.content).append("\n")
    }

    // ---------- 内部 ----------

    private fun upsertFact(sessionId: String, fact: FactValue, now: Long) {
        val id = "fact-$sessionId-${fact.key}"
        db.factQueries.upsert(
            id,
            sessionId,
            fact.key,
            fact.value,
            fact.confidence,
            fact.sourceMsgIds.joinToString(","),
            now,
        )
    }

    private fun MessageEntity.toDomain(): Msg = Msg(
        id = id,
        sessionId = session_id,
        seq = seq,
        role = Role.fromDb(role),
        content = content,
        tokens = tokens.toInt(),
        createdAt = created_at,
    )
}

private fun FactEntity.toFactValue(): FactValue =
    FactValue(fact_key, value_, source_msg_ids?.split(',') ?: emptyList(), confidence)
