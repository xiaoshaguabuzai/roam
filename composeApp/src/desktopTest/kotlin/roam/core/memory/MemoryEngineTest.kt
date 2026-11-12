package roam.core.memory

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import roam.core.model.Role
import roam.core.model.estimateTokens
import roam.db.RoamDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryEngineTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var engine: MemoryEngine
    private var now = 1_000L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = RoamDatabase.Schema)
        engine = MemoryEngine(
            db = RoamDatabase(driver),
            summarizer = ExtractiveSummarizer(),
            clock = { now++ },
            config = MemoryConfig(
                segmentTokens = 40,
                contextBudget = 600,
                recentTurns = 4,
                maxFacts = 10,
            ),
        )
        engine.createSession("s1", "测试会话", "test")
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun seed(count: Int, template: (Int) -> String) {
        repeat(count) { i -> engine.appendUser("s1", template(i)) }
    }

    @Test
    fun appendAssignsIncreasingSeqAndRoles() {
        engine.appendUser("s1", "第一条")
        engine.appendAssistant("s1", "第二条")

        val msgs = engine.messages("s1")
        assertEquals(2, msgs.size)
        assertEquals(listOf(1L, 2L), msgs.map { it.seq })
        assertEquals(Role.USER, msgs[0].role)
        assertEquals(Role.ASSISTANT, msgs[1].role)
    }

    @Test
    fun segmentTriggersOnlyWhenPendingReachesThreshold() = runBlocking {
        seed(1) { "短消息 $it" }
        assertTrue(engine.pendingTokens("s1") < 40)
        assertEquals(false, engine.compactIfNeeded("s1"))

        seed(3) { i -> "第 $i 条：这是一段用于触发分段摘要的会议纪要内容。" }
        assertTrue(engine.pendingTokens("s1") >= 40)
        assertTrue(engine.compactIfNeeded("s1"))

        // 原文不丢，只是多了一条 L1
        assertEquals(4, engine.messages("s1").size)
        assertEquals(1, engine.summaryCount("s1", 1))
    }

    @Test
    fun consolidateMergesSegmentsAndSupersedesThem() = runBlocking {
        seed(6) { i -> "第 $i 条：会议纪要需要压缩成三条要点，风险是依赖未就绪。" }

        engine.compactIfNeeded("s1")
        engine.compactIfNeeded("s1")
        assertEquals(2, engine.summaryCount("s1", 1))

        val l2 = engine.consolidate("s1")
        assertNotNull(l2)
        assertEquals(0, engine.summaryCount("s1", 1))
        assertEquals(1, engine.summaryCount("s1", 2))
        assertTrue(engine.latestSummaryText("s1", 2)!!.isNotBlank())
    }

    @Test
    fun rebuildIsIdempotentAndKeepsRawMessages() = runBlocking {
        seed(9) { i -> "第 $i 条：密码是 roam-$i，发布计划定在 12 月 18 日。" }
        engine.compactIfNeeded("s1")
        engine.consolidate("s1")

        engine.rebuild("s1")
        val first = engine.latestSummaryText("s1", 2)

        engine.rebuild("s1")
        val second = engine.latestSummaryText("s1", 2)

        assertEquals(first, second)
        assertEquals(9, engine.messages("s1").size)
        assertEquals(1, engine.summaryCount("s1", 2))
    }

    @Test
    fun assembleKeepsFactsAndRecentTurns() = runBlocking {
        engine.appendUser("s1", "记住：我家 WiFi 密码是 roam-2026-wifi。")
        engine.appendUser("s1", "第 2 条：这周把周报写短一点。")
        engine.appendUser("s1", "第 3 条：发布计划定在 12 月 18 日。")
        engine.compactIfNeeded("s1")

        val ctx = engine.assembleContext("s1", "WiFi 密码是什么")
        assertTrue(ctx.contains("roam-2026-wifi"), "事实应进入上下文: $ctx")
    }

    @Test
    fun assembleRespectsBudget() {
        seed(60) { i -> "第 $i 条：" + "一些很长很长的内容。".repeat(3) }
        val ctx = engine.assembleContext("s1", "会议", budget = 200)
        assertTrue(estimateTokens(ctx) <= 200, "实际 ${estimateTokens(ctx)} tokens")
    }

    @Test
    fun exportContainsAllMessages() {
        engine.appendUser("s1", "导出测试第一条")
        engine.appendAssistant("s1", "导出测试第二条")

        val md = engine.exportMarkdown("s1")
        assertTrue(md.contains("测试会话"))
        assertTrue(md.contains("导出测试第一条"))
        assertTrue(md.contains("导出测试第二条"))
    }
}
