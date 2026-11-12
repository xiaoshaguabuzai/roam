// 登录辅助：打开有头 Edge 复用同一个 profile，人工登录一次即可。
// 注意：先停掉 server.mjs（同一 profile 不能同时被两个 Chromium 打开）。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PROFILE = path.join(ROOT, '.profile');
const HOME = 'https://www.doubao.com/chat/';

fs.mkdirSync(PROFILE, { recursive: true });

const context = await chromium.launchPersistentContext(PROFILE, {
  channel: 'msedge',
  headless: false,
  viewport: { width: 1280, height: 900 },
  locale: 'zh-CN',
  args: ['--disable-blink-features=AutomationControlled'],
});

const page = context.pages()[0] || (await context.newPage());
await page.goto(HOME, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => {});

console.log('请在弹出的 Edge 窗口里登录豆包（扫码或手机号）。');
console.log('登录完成、能看到对话输入框后，回到终端按回车保存并退出。');

process.stdin.once('data', async () => {
  await context.close();
  console.log('登录态已保存到 bridge/.profile，重新运行 npm start 即可。');
  process.exit(0);
});
