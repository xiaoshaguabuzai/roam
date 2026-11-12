// E1 补充：不登录（访客态）能否直接问答？这是「零成本」最强的证据，也决定 MVP 是否需要登录流程。
import { chromium } from 'playwright-core';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(here, 'out');
const site = process.argv[2] ?? 'doubao';
const URLS = { doubao: 'https://www.doubao.com/chat/', qianwen: 'https://www.qianwen.com/' };
const question = '1+1 等于几？只回答一个阿拉伯数字。';

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1280, height: 860 }, locale: 'zh-CN' });
await page.goto(URLS[site], { waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(5000);

// 关掉推广弹窗（如果有）
for (const sel of ['button[aria-label="关闭"]', '[class*="close"]', 'svg[class*="close"]']) {
  const loc = page.locator(sel).first();
  if (await loc.count().catch(() => 0)) { await loc.click({ timeout: 2000 }).catch(() => {}); break; }
}
await page.keyboard.press('Escape').catch(() => {});
await page.waitForTimeout(800);

const input = page.locator('[contenteditable="true"], textarea').first();
if (!(await input.count())) { console.log(JSON.stringify({ site, error: 'no input' })); process.exit(0); }

const before = await page.evaluate(() => document.body.innerText.length);
const t0 = Date.now();
await input.click();
await input.type(question, { delay: 20 });
await page.keyboard.press('Enter');

let firstMs = null;
let answer = '';
let loginWall = false;
for (let i = 0; i < 80; i++) {
  await page.waitForTimeout(300);
  const s = await page.evaluate(() => ({
    text: document.body.innerText,
    dialog: (document.querySelector('[role="dialog"], [class*="login"], [class*="modal"]')?.innerText || '').slice(0, 200),
  }));
  if (/登录|扫码|手机号/.test(s.dialog)) loginWall = true;
  if (s.text.length > before + 20) {
    if (firstMs === null) firstMs = Date.now() - t0;
    answer = s.text.slice(before).trim();
  }
  if (answer && answer.length > 4 && i > 6) break;
}
const totalMs = Date.now() - t0;
await fs.mkdir(OUT, { recursive: true });
const shot = path.join(OUT, `${site}-guest-${Date.now()}.png`);
await page.screenshot({ path: shot });
await browser.close();

const result = { site, question, loginWall, firstMs, totalMs, answer: answer.slice(0, 800), screenshot: path.relative(here, shot) };
console.log(JSON.stringify(result, null, 2));
