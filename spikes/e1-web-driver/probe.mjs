// E1：验证「网页版免费助手 + 登录态」能否被程序化驱动。
//
// 三种模式：
//   probe   无登录探测：站点可达性、登录墙、输入框/发送键/消息容器候选、控制台错误、截图
//   login   有头浏览器打开站点，人工登录一次，把 storageState 存到 .auth/<site>.json
//   ask     用已保存的登录态发一条问题，测量首字/整答时延与回答提取成功率
//
// 注意：这不是官方 API，是站点 UI 自动化。选择器会随改版失效，生产实现必须版本化 + 可热更新。

import { chromium } from 'playwright-core';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(here, 'out');
const AUTH = path.join(here, '.auth');

const SITES = {
  doubao: {
    name: '豆包',
    url: 'https://www.doubao.com/chat/',
    input: ['textarea', '[contenteditable="true"]', '[data-testid="chat_input_input"]'],
    send: ['button[type="button"]:has-text("发送")', '[class*="send"]'],
  },
  tongyi: {
    name: '通义',
    url: 'https://www.tongyi.com/',
    input: ['textarea', '[contenteditable="true"]'],
    send: ['button:has-text("发送")', '[class*="send"]'],
  },
};

const args = process.argv.slice(2);
const opt = (k, d) => {
  const i = args.indexOf(`--${k}`);
  return i >= 0 ? args[i + 1] : d;
};
const mode = opt('mode', 'probe');
const siteKey = opt('site', 'doubao');
const site = SITES[siteKey] ?? SITES.doubao;

function stamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

async function snapshot(page, tag) {
  const file = path.join(OUT, `${siteKey}-${tag}-${stamp()}.png`);
  await page.screenshot({ path: file, fullPage: false }).catch(() => {});
  return file;
}

async function loginFlow() {
  await fs.mkdir(AUTH, { recursive: true });
  const dir = path.join(AUTH, siteKey);
  const ctx = await chromium.launchPersistentContext(dir, {
    channel: 'msedge',
    headless: false,
    viewport: { width: 1280, height: 860 },
    locale: 'zh-CN',
  });
  const page = ctx.pages()[0] ?? await ctx.newPage();
  await page.goto(site.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  console.log(`\n请在打开的浏览器里手动登录 ${site.name}，登录完成并看到对话输入框后，回到终端按回车。`);
  await new Promise((resolve) => process.stdin.once('data', resolve));
  await ctx.storageState({ path: path.join(AUTH, `${siteKey}.json`) });
  await ctx.close();
  console.log(`已保存登录态: .auth/${siteKey}.json（请勿提交，已在 .gitignore 中排除）`);
}

async function probeFlow() {
  await fs.mkdir(OUT, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const ctx = await browser.newContext({
    viewport: { width: 1280, height: 860 },
    locale: 'zh-CN',
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0',
  });
  const page = await ctx.newPage();

  const consoleErrors = [];
  const failed = [];
  page.on('console', (m) => m.type() === 'error' && consoleErrors.push(m.text().slice(0, 200)));
  page.on('requestfailed', (r) => failed.push(`${r.method()} ${r.url().slice(0, 120)} ${r.failure()?.errorText}`));

  const t0 = Date.now();
  const resp = await page.goto(site.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  const loadMs = Date.now() - t0;
  await page.waitForTimeout(6000); // 等 SPA 渲染与登录弹窗出现

  const info = await page.evaluate(() => {
    const txt = (el) => (el?.innerText || '').trim().slice(0, 40);
    const pick = (sel) => Array.from(document.querySelectorAll(sel)).slice(0, 5)
      .map((el) => ({ tag: el.tagName.toLowerCase(), cls: (el.className || '').toString().slice(0, 80), text: txt(el) }));
    return {
      title: document.title,
      url: location.href,
      bodyTextHead: document.body.innerText.slice(0, 600),
      textareas: document.querySelectorAll('textarea').length,
      editables: document.querySelectorAll('[contenteditable="true"]').length,
      buttons: pick('button').slice(0, 8),
      loginHints: ['登录', '扫码', '手机号', '同意'].filter((k) => document.body.innerText.includes(k)),
      hasChatInput: !!document.querySelector('textarea, [contenteditable="true"]'),
      dialogText: (document.querySelector('[role="dialog"], .login-modal, [class*="login"]')?.innerText || '').slice(0, 300),
    };
  });

  const shot = await snapshot(page, 'probe');
  const out = {
    site: siteKey,
    status: resp?.status(),
    loadMs,
    finalUrl: page.url(),
    info,
    consoleErrors: consoleErrors.slice(0, 10),
    failedRequests: failed.slice(0, 10),
    screenshot: path.relative(here, shot),
  };
  await fs.writeFile(path.join(OUT, `${siteKey}-probe.json`), JSON.stringify(out, null, 2), 'utf8');
  await browser.close();

  console.log(JSON.stringify(out, null, 2));
}

async function askFlow() {
  const state = path.join(AUTH, `${siteKey}.json`);
  await fs.access(state).catch(() => {
    throw new Error(`缺少登录态 ${state}，先运行 npm run login -- --site ${siteKey}`);
  });
  const question = opt('q', '用一句话说明今天是几号，并列出你看到的第一个按钮文字。');
  const browser = await chromium.launch({ channel: 'msedge', headless: false });
  const ctx = await browser.newContext({ storageState: state, viewport: { width: 1280, height: 860 }, locale: 'zh-CN' });
  const page = await ctx.newPage();
  await page.goto(site.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(4000);

  let input = null;
  for (const sel of site.input) {
    const loc = page.locator(sel).first();
    if (await loc.count() && await loc.isVisible().catch(() => false)) { input = loc; break; }
  }
  if (!input) throw new Error('找不到输入框，站点可能改版或需要登录');

  const t0 = Date.now();
  await input.click();
  await input.fill(question).catch(async () => { await input.type(question, { delay: 15 }); });
  await page.keyboard.press('Enter');

  // 轮询回答区：取最后一个可见消息块文本，记录首次出现非空内容的时间
  let firstTextAt = null;
  let answer = '';
  for (let i = 0; i < 120; i++) {
    await page.waitForTimeout(250);
    const tail = await page.evaluate(() => {
      const nodes = Array.from(document.querySelectorAll('[class*="message"], [class*="answer"], [class*="markdown"], article'))
        .filter((n) => n.offsetParent && (n.innerText || '').trim().length > 0);
      return nodes.length ? nodes[nodes.length - 1].innerText.trim() : '';
    });
    if (tail && firstTextAt === null) firstTextAt = Date.now() - t0;
    if (tail && tail.length >= answer.length) answer = tail;
    if (answer.length > 20 && i > 8 && tail === answer) { await page.waitForTimeout(1500); break; }
  }
  const totalMs = Date.now() - t0;
  const shot = await snapshot(page, 'ask');
  await browser.close();

  const result = { site: siteKey, question, firstTextMs: firstTextAt, totalMs, answer: answer.slice(0, 2000), screenshot: path.relative(here, shot) };
  await fs.mkdir(OUT, { recursive: true });
  await fs.writeFile(path.join(OUT, `${siteKey}-ask.json`), JSON.stringify(result, null, 2), 'utf8');
  console.log(JSON.stringify(result, null, 2));
}

if (mode === 'login') await loginFlow();
else if (mode === 'ask') await askFlow();
else await probeFlow();
