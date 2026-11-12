/**
 * 豆包网页通道（Android）：注入提问并轮询回答。
 */
package roam.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import roam.core.assistant.AssistantDriver
import roam.core.assistant.DriverStatus

/**
 * 豆包网页通道（Android）。
 *
 * 在应用内保持一个 WebView 打开豆包，复用用户手动登录后的 Cookie：
 * 发送问题时注入脚本写入输入框并点发送，随后轮询最后一条回答文本，以完整快照回调上层。
 *
 * DOM 自动化天然脆弱：改版、风控、页面未加载都会失败。因此每条失败路径都返回明确错误，
 * 由上层降级到本地骨架，不会卡住会话。
 */
@SuppressLint("SetJavaScriptEnabled")
class DoubaoWebDriver(context: Context) : AssistantDriver {

    override val id: String = "doubao-web"
    override val label: String = "豆包网页"

    /** 常驻 WebView，由 MainActivity 以 1:1 尺寸放到 Compose 下层。 */
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 去掉 "; wv" 标记，避免站点把应用内 WebView 识别成受限浏览器
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        loadUrl(HOME)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var lastText = ""
    private var idleTicks = 0

    init {
        CookieManager.getInstance().setAcceptCookie(true)
    }

    override fun status(): DriverStatus {
        val cookies = CookieManager.getInstance().getCookie(HOME).orEmpty()
        val loggedIn = cookies.contains("sessionid=") ||
            cookies.contains("sessionid_ss=") ||
            cookies.contains("sid_tt=")
        return if (loggedIn) DriverStatus.READY else DriverStatus.NEED_LOGIN
    }

    override fun ask(
        prompt: String,
        question: String,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        handler.post {
            val my = ++generation
            lastText = ""
            idleTicks = 0

            webView.evaluateJavascript(INSTALL_JS, null)
            webView.evaluateJavascript(
                "window.__roamSend(${JSONObject.quote(prompt)}, ${JSONObject.quote(question)})",
            ) { raw ->
                val result = unquote(raw)
                if (result != "sent") {
                    onError(
                        when (result) {
                            "no-input" -> "找不到输入框（页面未加载或已改版）"
                            else -> "发送失败：$result"
                        },
                    )
                    return@evaluateJavascript
                }
                poll(my, 0, onDelta, onDone, onError)
            }
        }
    }

    /**
     * 每 500ms 读取一次页面：
     * - 先识别验证码/登录墙；
     * - 再取回答快照；文本变化即回调，连续 6 次无变化视为生成结束；
     * - 超过 240 次（约 2 分钟）报超时。
     */
    private fun poll(
        my: Int,
        attempt: Int,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (my != generation) return
        if (attempt > 240) {
            onError("等待回答超时")
            return
        }

        handler.postDelayed({
            if (my != generation) return@postDelayed
            // 提问后可能发生路由刷新，注入脚本会丢；每轮幂等补注入
            webView.evaluateJavascript(INSTALL_JS, null)
            webView.evaluateJavascript("window.__roamBlocked?window.__roamBlocked():''") { blockedRaw ->
                val blocked = unquote(blockedRaw)
                if (blocked.isNotEmpty()) {
                    onError(
                        if (blocked == "captcha") "页面要求验证码，请在豆包页面里手动完成验证" else "页面要求登录",
                    )
                    return@evaluateJavascript
                }

                webView.evaluateJavascript("window.__roamAnswer?window.__roamAnswer():''") { raw ->
                    val text = unquote(raw)
                    if (text.isNotEmpty() && text != lastText) {
                        onDelta(text)
                        lastText = text
                        idleTicks = 0
                    } else if (text.isNotEmpty()) {
                        idleTicks++
                    }

                    if (lastText.isNotEmpty() && idleTicks >= 6) {
                        onDone(lastText)
                    } else {
                        poll(my, attempt + 1, onDelta, onDone, onError)
                    }
                }
            }
        }, 500)
    }

    /** evaluateJavascript 返回的是 JSON 字面量；字符串需要还原，null 视为空。 */
    private fun unquote(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching {
            when (val value = JSONTokener(raw).nextValue()) {
                is String -> value
                else -> raw
            }
        }.getOrDefault(raw)
    }

    private companion object {
        const val HOME = "https://www.doubao.com/chat/"

        /**
         * 幂等注入：发送提问、读取最后一条回答、识别验证码/登录墙。
         * 只暴露 __roamSend / __roamAnswer / __roamBlocked 三个入口。
         */
        val INSTALL_JS = """
            (function(){
              if (window.__roamInstalled) return;
              window.__roamInstalled = true;

              window.__roamSend = function(text, question){
                try {
                  window.__roamLastPrompt = text;
                  window.__roamQuestion = question || text;
                  var els = Array.prototype.slice.call(document.querySelectorAll('[contenteditable="true"], textarea'));
                  var input = null;
                  for (var i = 0; i < els.length; i++) {
                    var e = els[i];
                    var r = e.getBoundingClientRect();
                    if (e.offsetParent !== null && r.width > 80 && r.height > 10) { input = e; break; }
                  }
                  if (!input) return 'no-input';

                  input.focus();
                  document.execCommand('selectAll', false, null);
                  document.execCommand('insertText', false, text);
                  var now = (input.innerText || input.value || '');
                  if (now.indexOf(text) < 0) {
                    input.innerText = text;
                    input.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:text}));
                  }

                  // 等 React 状态更新后再点发送；发送键是带 send-btn-wrapper 的容器
                  setTimeout(function(){
                    var wrap = document.querySelector('[class*="send-btn-wrapper"]');
                    var btn = wrap ? (wrap.querySelector('button') || wrap) : null;
                    if (!btn) {
                      var btns = Array.prototype.slice.call(document.querySelectorAll('button'));
                      for (var j = 0; j < btns.length; j++) {
                        var t = (btns[j].innerText || '').trim();
                        if ((t === '发送' || t === 'Send') && !btns[j].disabled) { btn = btns[j]; break; }
                      }
                    }
                    if (btn) {
                      btn.click();
                    } else {
                      input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true}));
                    }
                  }, 300);

                  return 'sent';
                } catch (e) { return 'error:' + e.message; }
              };

              window.__roamAnswer = function(){
                try {
                  var p = window.__roamLastPrompt || '';
                  var list = document.querySelector('[class*="message-list"]');
                  var text = '';

                  // 首选：整段消息列表文本，从用户问题之后截断，取助手回复正文
                  if (list) {
                    // 用 textContent：折叠卡片里的正文不在 innerText 里，但仍在 DOM 中
                    text = (list.textContent || '').trim();
                    var q = window.__roamQuestion || '';
                    var idx = q ? text.lastIndexOf(q) : -1;
                    if (idx >= 0) {
                      text = text.slice(idx + q.length).trim();
                    } else if (p && text.indexOf(p) >= 0) {
                      text = text.slice(text.indexOf(p) + p.length).trim();
                    }
                  }

                  // 兜底：按候选类名取最长文本块
                  if (!text) {
                    var scope = list || document;
                    var nodes = Array.prototype.slice.call(
                      scope.querySelectorAll('[class*="flowing"], [class*="text-content"], [class*="markdown"]')
                    ).filter(function(n){
                      var t = (n.innerText || '').trim();
                      return t.length > 0 && t !== p;
                    });
                    nodes.sort(function(a, b){
                      return (b.innerText || '').length - (a.innerText || '').length;
                    });
                    text = nodes.length ? nodes[0].innerText.trim() : '';
                  }

                  // 去掉生成状态、时间行、搜索状态行与消息操作按钮文案
                  text = text.replace(/(正在搜索|找到\s*\d+\s*篇资料|正在生成)\s*/g, '');
                  text = text.replace(/(今天|昨天|前天)\s*\d{1,2}:\d{2}\s*/g, '');
                  text = text.replace(/搜索\s*\d+\s*个关键词[，,]\s*参考\s*\d+\s*篇资料\s*/g, '');
                  text = text.replace(/展开全部|收起|复制|分享|重新生成|点赞|点踩/g, '');
                  text = text.replace(/内容由\s*AI\s*生成[^\n]*/g, '');
                  if (p) text = text.replace(p, '');
                  return text.trim();
                } catch (e) { return ''; }
              };

              window.__roamBlocked = function(){
                var t = document.body ? document.body.innerText : '';
                if (/拖动下方滑块|请完成验证|安全验证|完成验证/.test(t)) return 'captcha';
                if (/登录后(继续|使用)|请先登录|扫码登录/.test(t) && !/退出登录/.test(t)) return 'login';
                return '';
              };
            })();
        """.trimIndent()
    }
}
