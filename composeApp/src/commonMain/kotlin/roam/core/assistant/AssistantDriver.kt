package roam.core.assistant

enum class DriverStatus {
    /** 通道可用（已登录、页面就绪）。 */
    READY,

    /** 需要用户登录网页。 */
    NEED_LOGIN,

    /** 当前平台没有可用实现。 */
    UNAVAILABLE,
}

/**
 * 免费网页通道的统一抽象。
 * 实现可能跑在 Android WebView（手机）或远端网关（桌面），上层只关心流式文本。
 */
interface AssistantDriver {
    val id: String
    val label: String

    fun status(): DriverStatus

    fun ask(
        prompt: String,
        question: String,
        onDelta: (String) -> Unit, // 每次传完整快照，不是增量
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    )
}
