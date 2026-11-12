package roam.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import roam.core.assistant.AssistantDriver
import roam.core.assistant.DriverStatus

/**
 * 豆包网页通道（Android）。
 *
 * 做法：在应用内保持一个 WebView 打开豆包，复用用户手动登录后的 Cookie；
 * 发送问题时注入脚本写输入框并点发送，之后轮询最后一条回答文本，按增量回调。
 *
 * 这类 DOM 自动化天然脆弱：站点改版、风控验证、页面未加载都会失败，
 * 因此每个失败路径都返回明确错误，由上层降级到本地骨架，而不是卡住会话。
 */
@SuppressLint("SetJavaScriptEnabled")
class DoubaoWebDriver(context: Context, private val startUrl: String = HOME) : AssistantDriver {

    override val id: String = "doubao-web"
    override val label: String = "豆包网页"

    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "page finished: $url")
                probe()
            }
        }
        loadUrl(startUrl)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var lastText = ""
    private var idleTicks = 0

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        listOf(3000L, 8000L, 15000L).forEach { delay -> handler.postDelayed({ probe() }, delay) }
        handler.postDelayed({ listText() }, 12000)
        handler.postDelayed({ listText() }, 20000)
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
            Log.d(TAG, "ask len=${prompt.length} url=${webView.url}")

            webView.evaluateJavascript(INSTALL_JS, null)
            webView.evaluateJavascript(
                "window.__roamSend(${JSONObject.quote(prompt)}, ${JSONObject.quote(question)})",
            ) { raw ->
                val result = unquote(raw)
                Log.d(TAG, "send result=$result")
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

    private fun poll(
        my: Int,
        attempt: Int,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (my != generation) return
        if (attempt == 8) debugDump()
        if (attempt == 10 || attempt == 20) listText()
        if (attempt > 240) {
            onError("等待回答超时")
            return
        }

        handler.postDelayed({
            if (my != generation) return@postDelayed
            // 页面可能在提问后发生路由/刷新，注入脚本会丢失；每次都幂等补注入。
            webView.evaluateJavascript(INSTALL_JS, null)
            webView.evaluateJavascript("window.__roamBlocked?window.__roamBlocked():''") { blockedRaw ->
                val blocked = unquote(blockedRaw)
                if (blocked.isNotEmpty()) {
                    Log.d(TAG, "blocked=$blocked")
                    onError(
                        if (blocked == "captcha") "页面要求验证码，请在豆包页面里手动完成验证" else "页面要求登录",
                    )
                    return@evaluateJavascript
                }

                webView.evaluateJavascript("window.__roamAnswer?window.__roamAnswer():''") { raw ->
                    val text = unquote(raw)
                    // onDelta 传的是「完整快照」，不是增量；上层直接替换展示文本。
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

    private fun debugDump() {
        webView.evaluateJavascript(
            "JSON.stringify({url:location.href, hasDebug: !!window.__roamDebug, hasSend: !!window.__roamSend})",
        ) { raw -> Log.d(TAG, "state=${unquote(raw)}") }
        webView.evaluateJavascript("window.__roamDebug?window.__roamDebug():''") { raw ->
            Log.d(TAG, "debug=${unquote(raw)}")
        }
        webView.evaluateJavascript("window.__roamDom?window.__roamDom():''") { raw ->
            Log.d(TAG, "dom=${unquote(raw)}")
        }
        webView.evaluateJavascript("window.__roamList?window.__roamList():''") { raw ->
            Log.d(TAG, "list=${unquote(raw)}")
        }
    }

    private fun listText() {
        webView.evaluateJavascript("window.__roamListText?window.__roamListText():''") { raw ->
            Log.d(TAG, "listText=${unquote(raw).take(400)}")
        }
    }

    private fun probe() {
        webView.evaluateJavascript(PROBE_JS) { raw -> Log.d(TAG, "probe=${unquote(raw)}") }
    }

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
        const val TAG = "RoamDriver"
        const val HOME = "https://www.doubao.com/chat/"

        /** 注入一次：发送、读取最后一条回答、识别验证码/登录墙。 */
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

                  // 等 React 状态更新后再点发送；豆包的发送键是带 send-btn-wrapper 的容器。
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

                  // 首选：整段消息列表文本，从「用户问题」之后截断，拿到助手回复正文。
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

                  // 兜底：按候选类名取最长文本块。
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

                  text = text.replace(/(正在搜索|找到\s*\d+\s*篇资料|正在生成)\s*/g, '');
                  // 去掉时间行、搜索状态行与消息操作按钮文案
                  text = text.replace(/(今天|昨天|前天)\s*\d{1,2}:\d{2}\s*/g, '');
                  text = text.replace(/搜索\s*\d+\s*个关键词[，,]\s*参考\s*\d+\s*篇资料\s*/g, '');
                  text = text.replace(/展开全部|收起|复制|分享|重新生成|点赞|点踩/g, '');
                  text = text.replace(/内容由\s*AI\s*生成[^\n]*/g, '');
                  if (p) text = text.replace(p, '');
                  return text.trim();
                } catch (e) { return ''; }
              };

              window.__roamList = function(){                try {
                  var list = document.querySelector('[class*="message-list"]');
                  if (!list) return 'no-list';
                  var nodes = Array.prototype.slice.call(list.querySelectorAll('*')).filter(function(n){
                    return (n.children.length === 0) && ((n.innerText || '').trim().length > 0);
                  });
                  return JSON.stringify(nodes.slice(-6).map(function(n){
                    return { c: (n.className || '').toString().slice(0, 60), t: (n.innerText || '').slice(0, 60) };
                  }));
                } catch (e) { return 'list-error:' + e.message; }
              };

              window.__roamListText = function(){
                try {
                  var list = document.querySelector('[class*="message-list"]');
                  return list ? (list.innerText || '').slice(-800) : 'no-list';
                } catch (e) { return 'list-text-error:' + e.message; }
              };

              window.__roamBlocked = function(){
                var t = document.body ? document.body.innerText : '';
                if (/拖动下方滑块|请完成验证|安全验证|完成验证/.test(t)) return 'captcha';
                if (/登录后(继续|使用)|请先登录|扫码登录/.test(t) && !/退出登录/.test(t)) return 'login';
                return '';
              };

              window.__roamDom = function(){
                try {
                  var out = { testids: [], classes: [] };
                  var els = document.querySelectorAll('[data-testid]');
                  for (var i = 0; i < els.length && out.testids.length < 40; i++) {
                    var id = els[i].getAttribute('data-testid') || '';
                    if (/message|chat|conversation|answer|reply|turn|bubble|markdown/i.test(id)) out.testids.push(id);
                  }
                  var seen = {};
                  var all = document.querySelectorAll('div');
                  for (var j = 0; j < all.length && out.classes.length < 40; j++) {
                    var c = (all[j].className || '').toString();
                    if (!c || seen[c]) continue;
                    if (/markdown|message|answer|reply|bubble|chat-content|flow/i.test(c)) {
                      seen[c] = 1;
                      out.classes.push(c.slice(0, 80));
                    }
                  }
                  return JSON.stringify(out);
                } catch (e) { return 'dom-error:' + e.message; }
              };

              window.__roamDebug = function(){                try {
                  var bodyText = document.body ? document.body.innerText : '';
                  var prompt = window.__roamLastPrompt || '';
                  var pick = function(sel){
                    return Array.prototype.slice.call(document.querySelectorAll(sel))
                      .filter(function(n){ return (n.innerText || '').trim().length > 0; })
                      .slice(-5)
                      .map(function(n){
                        return { c: (n.className || '').toString().slice(0, 60), l: (n.innerText || '').length };
                      });
                  };
                  return JSON.stringify({
                    url: location.href,
                    promptInBody: prompt.length > 0 && bodyText.indexOf(prompt) >= 0,
                    markdown: pick('[class*="markdown"]'),
                    message: pick('[class*="message"]'),
                    answer: pick('[class*="answer"]')
                  });
                } catch (e) { return 'debug-error:' + e.message; }
              };
            })();
        """.trimIndent()

        /** 只用于诊断：把页面结构计数打到 logcat。 */
        val PROBE_JS = """
            (function(){
              var vis = function(sel){
                return Array.prototype.slice.call(document.querySelectorAll(sel)).filter(function(n){
                  var r = n.getBoundingClientRect();
                  return n.offsetParent !== null && r.width > 80 && r.height > 10;
                }).length;
              };
              return JSON.stringify({
                url: location.href,
                editable: vis('[contenteditable="true"]'),
                textarea: vis('textarea'),
                buttons: document.querySelectorAll('button').length,
                loginBtn: Array.prototype.slice.call(document.querySelectorAll('button')).filter(function(b){
                  return /登录/.test(b.innerText || '');
                }).length,
                body: (document.body ? document.body.innerText : '').slice(0, 120)
              });
            })();
        """.trimIndent()
    }
}
