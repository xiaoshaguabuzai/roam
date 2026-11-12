package roam.desktop

import kotlinx.serialization.json.JsonObject
import roam.core.tool.Tool
import roam.core.tool.ToolSpec
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

/** 桌面端只读工具：剪贴板。手机端的应用/日历/通知在桌面没有对应能力，故不注册。 */
class DesktopClipboardTool : Tool {
    override val spec = ToolSpec(
        name = "clipboard.read",
        description = "读取桌面系统剪贴板最近一次复制的文本",
        parameters = JsonObject(emptyMap()),
    )

    override suspend fun execute(args: JsonObject): String {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val text = runCatching {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
        return if (text.isNullOrBlank()) "剪贴板为空" else text.take(2000)
    }
}
