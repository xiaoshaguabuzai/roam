// E4：记忆策略对比模拟（结构性验证，不调用模型）。
//
// 问题：用户原方案是「每轮把全部会话拿出来总结压缩入库」。
// 本实验量化三件事：
//   1. 全量重放的 token 成本是不是 O(n²)；
//   2. 分层摘要（L1 分段 + L2 滚动）能把成本压到多少；
//   3. 分层 + 检索在「早期植入的事实」上会不会丢（漂移）。
//
// 注意：这里用确定性的抽取式摘要器代替真实模型，衡量的是「信息在管线中能否被保留」，
// 不是最终回答准确率。真实准确率必须在接入登录态后按 docs/feasibility-report.md 的 E4 补测。

const CHARS_PER_TOKEN = 1.5; // 中文粗估
const tokens = (s) => Math.max(1, Math.ceil(s.length / CHARS_PER_TOKEN));

const SEG_TOKEN_BUDGET = 4000; // 未压缩消息达到该值触发 L1
const SUMMARY_MAX = 400;
const L2_MAX = 800;
const RECENT_TURNS = 8;
const TOP_K = 5;
const CTX_BUDGET = 12000;

// ---- 构造 200 轮语料，并在固定轮次植入「事实」----
const FACTS = [
  { turn: 10, text: '我家 WiFi 密码是 roam-2026-wifi', key: 'WiFi 密码' },
  { turn: 40, text: '发布计划定在 12 月 18 日，负责人是李工', key: '发布日期' },
  { turn: 90, text: '我偏好回答先给结论再给三条理由', key: '回答偏好' },
  { turn: 150, text: '我的工位在 3 号楼 502', key: '工位' },
];

const convo = [];
for (let i = 1; i <= 200; i++) {
  const fact = FACTS.find((f) => f.turn === i);
  const user = fact
    ? `记一下：${fact.text}。另外第 ${i} 轮我想聊聊怎么把周报写得更短。`
    : `第 ${i} 轮：帮我把这段会议纪要压缩成三条要点，主题是第 ${i} 个项目迭代的进度同步与风险。`;
  const assistant = fact
    ? `好的，已记住：${fact.text}。第 ${i} 轮的周报建议：先结论、再数据、最后待办。`
    : `第 ${i} 轮要点：进度正常；风险是依赖未就绪；下一步补测试。`;
  convo.push({ seq: i, user, assistant });
}

// ---- 确定性「摘要器」：保留含事实/关键词的句子，其余按预算截断 ----
function summarize(texts, max = SUMMARY_MAX) {
  const KEY = /密码|计划|偏好|工位|负责人|日期|发布|结论|风险|待办/;
  const sentences = texts.flatMap((t) => t.split(/[。；\n]/)).map((s) => s.trim()).filter(Boolean);
  const keep = [];
  for (const s of sentences) {
    if (KEY.test(s) && !keep.some((k) => k.includes(s.slice(0, 18)))) keep.push(s);
  }
  let out = keep.join('；');
  if (!out) out = sentences.slice(0, 3).join('；');
  while (tokens(out) > max && out.length > 0) out = out.slice(0, Math.floor(out.length * 0.8));
  return out;
}

function extractFacts(texts) {
  const KEY = /密码|计划|偏好|工位|负责人|日期|发布|结论/;
  return texts.flatMap((t) => t.split(/[。；\n]/)).map((s) => s.trim())
    .filter((s) => KEY.test(s) && s.length < 80);
}

function keywordScore(query, text) {
  const q = new Set(query.replace(/[，。？、\s]/g, '').split('').filter(Boolean));
  let hit = 0;
  for (const ch of new Set(text.replace(/[，。？、\s]/g, ''))) if (q.has(ch)) hit++;
  return hit;
}

// ---- 策略 A：全量重放 ----
function strategyFullReplay(upto) {
  return convo.slice(0, upto).map((t) => `${t.user}\n${t.assistant}`).join('\n');
}

// ---- 策略 B/C：分层摘要（B 不检索，C 检索）----
class LayeredMemory {
  constructor({ retrieval }) {
    this.retrieval = retrieval;
    this.msgs = [];       // L0 已入库消息
    this.pending = [];    // 待压缩
    this.pendingTokens = 0;
    this.segments = [];   // L1
    this.rolling = '';    // L2
    this.facts = new Map(); // L3
    this.ops = { segments: 0, consolidations: 0, compactionMs: 0 };
  }

  add(user, assistant) {
    const t = `${user}\n${assistant}`;
    this.msgs.push(t);
    this.pending.push(t);
    this.pendingTokens += tokens(t);
    if (this.pendingTokens >= SEG_TOKEN_BUDGET) this.segment();
  }

  segment() {
    if (!this.pending.length) return;
    const t0 = performance.now();
    const text = summarize(this.pending);
    this.segments.push(text);
    for (const f of extractFacts(this.pending)) this.facts.set(f.slice(0, 24), f);
    this.pending = [];
    this.pendingTokens = 0;
    this.ops.segments++;
    if (this.segments.length >= 4) this.consolidate();
    this.ops.compactionMs += performance.now() - t0;
  }

  consolidate() {
    const t0 = performance.now();
    const merged = summarize([...this.segments, this.rolling].filter(Boolean), L2_MAX);
    this.rolling = merged;
    this.segments = [];
    this.ops.consolidations++;
    this.ops.compactionMs += performance.now() - t0;
  }

  assemble(query) {
    const parts = [];
    parts.push('【长期事实】' + [...this.facts.values()].join('；'));

    parts.push('【会话摘要】' + this.rolling);
    if (this.retrieval) {
      const scored = this.msgs
        .map((m, i) => ({ m, i, s: keywordScore(query, m) }))
        .filter((x) => x.s > 0)
        .sort((a, b) => b.s - a.s)
        .slice(0, TOP_K);
      if (scored.length) parts.push('【检索片段】\n' + scored.map((x) => x.m).join('\n---\n'));
    }

    parts.push('【最近对话】\n' + this.msgs.slice(-RECENT_TURNS).join('\n'));
    let ctx = parts.filter(Boolean).join('\n\n');
    while (tokens(ctx) > CTX_BUDGET) ctx = ctx.slice(0, Math.floor(ctx.length * 0.9));
    return ctx;
  }
}

// ---- 逐轮跑三条策略，统计 token 成本 ----
function run(memory, question) {
  let total = 0;
  let peak = 0;
  const perTurn = [];
  for (let i = 1; i <= 200; i++) {
    const t = convo[i - 1];
    if (memory) {
      memory.add(t.user, t.assistant);
      const ctx = memory.assemble(question);
      const n = tokens(ctx);
      total += n;
      peak = Math.max(peak, n);
      perTurn.push(n);
    } else {
      const n = tokens(strategyFullReplay(i));
      total += n;
      peak = Math.max(peak, n);
      perTurn.push(n);
    }
  }
  return { total, peak, perTurn };
}

const question = '我的 WiFi 密码、发布日期、回答偏好和工位分别是什么？';

const full = run(null, question);
const layeredB = new LayeredMemory({ retrieval: false });
const b = run(layeredB, question);
const layeredC = new LayeredMemory({ retrieval: true });
const c = run(layeredC, question);

const ctxB = layeredB.assemble(question);
const ctxC = layeredC.assemble(question);
const hitB = FACTS.filter((f) => ctxB.includes(f.text)).length;
const hitC = FACTS.filter((f) => ctxC.includes(f.text)).length;

const fmt = (n) => n.toLocaleString('en-US');
console.log('E4 记忆策略对比（200 轮，结构性模拟）');
console.log('');
console.log('策略                  总 token      均值/轮    峰值/轮   早期事实命中(4)');
console.log(`A 全量重放          ${fmt(full.total).padStart(12)} ${fmt(Math.round(full.total / 200)).padStart(9)} ${fmt(full.peak).padStart(9)}        4/4（必然全命中）`);
console.log(`B 分层摘要          ${fmt(b.total).padStart(12)} ${fmt(Math.round(b.total / 200)).padStart(9)} ${fmt(b.peak).padStart(9)}        ${hitB}/4`);
console.log(`C 分层+检索         ${fmt(c.total).padStart(12)} ${fmt(Math.round(c.total / 200)).padStart(9)} ${fmt(c.peak).padStart(9)}        ${hitC}/4`);
console.log('');
console.log(`token 成本：B 是 A 的 ${(b.total / full.total * 100).toFixed(1)}%，C 是 A 的 ${(c.total / full.total * 100).toFixed(1)}%`);
console.log(`压缩作业：B 分段 ${layeredB.ops.segments} 次 / 合并 ${layeredB.ops.consolidations} 次，累计 ${layeredB.ops.compactionMs.toFixed(1)}ms（模拟器，真实模型另计）`);
console.log(`A 峰值上下文 ${fmt(full.peak)} token ${full.peak > 32000 ? '（已超出多数免费通道上下文，必然截断）' : ''}`);
console.log('');
console.log('结论：全量重放的累计成本随轮次平方增长；分层方案把成本压到线性量级，' +
  '且通过 L3 事实与检索保住早期信息。真实回答准确率需要接入登录态后补测。');
