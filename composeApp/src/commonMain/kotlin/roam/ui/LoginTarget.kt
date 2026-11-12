/**
 * 网页登录入口：按目标生成登录 URL。
 */
package roam.ui

/** 免费通道登录入口：Android 用内置 WebView 打开，桌面用系统浏览器。 */
enum class LoginTarget(val label: String, val url: String) {
    DOUBAO("豆包网页", "https://www.doubao.com/chat/"),
    QIANWEN("千问网页", "https://www.qianwen.com/"),
}
