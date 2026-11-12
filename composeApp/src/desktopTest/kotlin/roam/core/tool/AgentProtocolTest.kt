/**
 * 工具协议单测：解析、修复与降级。
 */
package roam.core.tool

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AgentProtocolTest {

    private val tools = setOf("clipboard.read", "apps.list", "calendar.events")

    @Test
    fun parsesCleanToolCall() {
        val out = AgentProtocol.parse("""{"tool":"apps.list","args":{}}""", tools)
        val call = assertIs<AgentOutput.ToolCall>(out)
        assertEquals("apps.list", call.name)
        assertEquals(JsonObject(emptyMap()), call.args)
    }

    @Test
    fun toleratesCodeFenceAndTrailingComma() {
        val out = AgentProtocol.parse(
            "```json\n{\"tool\":\"clipboard.read\",\"args\":{},}\n```",
            tools,
        )
        assertIs<AgentOutput.ToolCall>(out)
    }

    @Test
    fun parsesFinal() {
        val out = AgentProtocol.parse("""{"final":"今天没有日程。"}""", tools)
        assertEquals("今天没有日程。", assertIs<AgentOutput.Final>(out).text)
    }

    @Test
    fun proseFallsBackInsteadOfFailing() {
        val out = AgentProtocol.parse("现在 14:00，你有两个日程。", tools)
        assertIs<AgentOutput.Prose>(out)
    }

    @Test
    fun unknownToolIsNotExecuted() {
        val out = AgentProtocol.parse("""{"tool":"system.rm_rf","args":{}}""", tools)
        assertIs<AgentOutput.Prose>(out)
    }

    @Test
    fun multipleCallsInArrayAreRejected() {
        val out = AgentProtocol.parse(
            """[{"tool":"apps.list","args":{}},{"tool":"clipboard.read","args":{}}]""",
            tools,
        )
        assertIs<AgentOutput.Invalid>(out)
    }
}

class IntentRouterTest {

    @Test
    fun matchesClipboard() {
        assertEquals("clipboard.read", IntentRouter.match("帮我看看剪贴板里是什么")?.name)
    }

    @Test
    fun matchesApps() {
        assertEquals("apps.list", IntentRouter.match("list the apps on this phone")?.name)
    }

    @Test
    fun matchesCalendar() {
        assertEquals("calendar.events", IntentRouter.match("今天有什么日程")?.name)
    }

    @Test
    fun matchesNotifications() {
        assertEquals("notifications.list", IntentRouter.match("看看最近的手机通知")?.name)
    }

    @Test
    fun unrelatedQuestionDoesNotMatch() {
        assertNull(IntentRouter.match("现在几点了"))
    }
}
