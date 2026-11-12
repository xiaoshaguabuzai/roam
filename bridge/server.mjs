// FreeBridge：把「网页版豆包」包装成本地 HTTP 通道，供桌面端 GatewayDriver 调用。
//
// 设计：
//  - 持久化 Edge profile（bridge/.profile），登录一次即可复用；
//  - 默认有头（headless=false），方便用户直接在打开的窗口里扫码登录；
//  - POST /ask 用 NDJSON 流式返回：{"delta":"..."} ... {"done":true,"text":"..."}
//
// 启动：node server.mjs        （或 npm start）
// 登录：先停掉 server，再 node login.mjs，登录完回车，然后重新启动 server。

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PROFILE = path.join(ROOT, '.profile');
const HOME = 'https://www.doubao.com/chat/';
const PORT = Number(process.env.ROAM_BRIDGE_PORT || 8787);
const HEADLESS = process.env.ROAM_HEADLESS === '1';

let context = null;
let page = null;
let busy = false;

const INSTALL_JS = String.raw`
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
        if (btn) btn.click();
        else input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true}));
      }, 300);

      return 'sent';
    } catch (e) { return 'error:' + e.message; }
  };

  window.__roamAnswer = function(){
    try {
      var p = window.__roamLastPrompt || '';
      var q = window.__roamQuestion || '';
      var list = document.querySelector('[class*="message-list"]');
      var text = '';
      if (list) {
        text = (list.textContent || '').trim();
        var idx = q ? text.lastIndexOf(q) : -1;
        if (idx >= 0) text = text.slice(idx + q.length).trim();
        else if (p && text.indexOf(p) >= 0) text = text.slice(text.indexOf(p) + p.length).trim();
      }
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
`;

async function ensurePage() {
  if (page && !page.isClosed()) return page;
  fs.mkdirSync(PROFILE, { recursive: true });
  context = await chromium.launchPersistentContext(PROFILE, {
    channel: 'msedge',
    headless: HEADLESS,
    viewport: { width: 1280, height: 900 },
    locale: 'zh-CN',
    args: ['--disable-blink-features=AutomationControlled'],
  });
  page = context.pages()[0] || (await context.newPage());
  await page.goto(HOME, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(5000);
  await inject();
  return page;
}

async function inject() {
  if (!page || page.isClosed()) return;
  await page.addScriptTag({ content: INSTALL_JS }).catch(() => {});
}

async function loggedIn() {
  if (!context) return false;
  const cookies = await context.cookies('https://www.doubao.com').catch(() => []);
  const hasSession = cookies.some((c) => ['sessionid', 'sessionid_ss', 'sid_tt'].includes(c.name));
  // 豆包登出后 URL 会带 from_logout，即使 cookie 仍在也不能算就绪
  const loggedOut = page && !page.isClosed() && page.url().includes('from_logout');
  return hasSession && !loggedOut;
}

function withTimeout(promise, ms, message) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

async function ask(question, prompt, onDelta) {
  console.log('[bridge] ask start');
  const p = await ensurePage();
  console.log('[bridge] page ready');
  const logged = await loggedIn();
  console.log('[bridge] loggedIn', logged);
  if (!logged) {
    throw new Error('未登录：请先运行 node login.mjs 登录豆包（执行前先停掉 server）');
  }
  // 每次提问前刷新一次，避免上一次对话/弹窗把页面卡在异常状态
  await withTimeout(p.goto(HOME, { waitUntil: 'domcontentloaded', timeout: 30000 }), 35000, '页面刷新超时')
    .catch((e) => console.log('[bridge] reload skipped:', e.message));
  await p.waitForTimeout(3000);
  await inject();
  console.log('[bridge] injected');
  const sent = await withTimeout(
    p.evaluate(([text, q]) => window.__roamSend(text, q), [prompt, question]),
    15000,
    '页面无响应（发送超时）',
  );
  console.log('[bridge] sent', sent);
  if (sent !== 'sent') throw new Error(sent === 'no-input' ? '找不到输入框（页面未加载或改版）' : String(sent));

  if (process.env.ROAM_DEBUG === '1') {
    await p.waitForTimeout(4000);
    const debug = await p.evaluate(() => {
      const list = document.querySelector('[class*="message-list"]');
      const input = document.querySelector('[contenteditable="true"]');
      return JSON.stringify({
        url: location.href,
        hasList: !!list,
        listTail: list ? (list.textContent || '').slice(-200) : '',
        inputHead: input ? (input.innerText || '').slice(0, 60) : '',
      });
    }).catch((e) => 'debug-error:' + e.message);
    console.log('[bridge] debug', debug);
  }

  let last = '';
  let idle = 0;
  let sawList = false;
  const started = Date.now();
  while (Date.now() - started < 120000) {
    await p.waitForTimeout(500);
    const blocked = await p.evaluate(() => (window.__roamBlocked ? window.__roamBlocked() : '')).catch(() => '');
    if (blocked) throw new Error(blocked === 'captcha' ? '页面要求验证码' : '页面要求登录');

    const state = await p
      .evaluate(() => {
        const list = document.querySelector('[class*="message-list"]');
        return { hasList: !!list, text: window.__roamAnswer ? window.__roamAnswer() : '' };
      })
      .catch(() => ({ hasList: false, text: '' }));

    if (state.hasList) sawList = true;
    if (!sawList && Date.now() - started > 20000) {
      throw new Error('页面没有进入对话（登录态可能已失效）：请重新运行 node login.mjs 登录');
    }

    const text = state.text || '';
    if (text && text !== last) {
      last = text;
      idle = 0;
      onDelta(text);
    } else if (last) {
      idle += 1;
      if (idle >= 6) break;
    }
  }
  if (!last) throw new Error('等待回答超时');
  return last;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const startedAt = Date.now();
  res.on('finish', () => {
    console.log(`[bridge] ${req.method} ${req.url} -> ${res.statusCode} ${Date.now() - startedAt}ms`);
  });
  try {
    if (req.method === 'GET' && req.url === '/health') {
      const ready = !!(page && !page.isClosed());
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ready, busy, loggedIn: await loggedIn(), url: page ? page.url() : null }));
      return;
    }

    if (req.method === 'POST' && req.url === '/ask') {
      if (busy) {
        res.writeHead(409, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'busy' }));
        return;
      }
      busy = true;
      const body = JSON.parse((await readBody(req)) || '{}');
      const question = body.question || body.prompt || '';
      const prompt = body.prompt || question;
      res.writeHead(200, { 'Content-Type': 'application/x-ndjson', 'Cache-Control': 'no-cache' });
      try {
        const finalText = await ask(question, prompt, (text) => {
          res.write(JSON.stringify({ delta: text }) + '\n');
        });
        res.write(JSON.stringify({ done: true, text: finalText }) + '\n');
      } catch (e) {
        res.write(JSON.stringify({ error: e.message }) + '\n');
      } finally {
        busy = false;
        res.end();
      }
      return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'not found' }));
  } catch (e) {
    if (!res.headersSent) res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: e.message }));
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[bridge] listening on http://127.0.0.1:${PORT}`);
  console.log(`[bridge] profile: ${PROFILE}  headless=${HEADLESS}`);
  ensurePage().catch((e) => console.error('[bridge] browser start failed:', e.message));
});
