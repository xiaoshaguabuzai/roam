// E2 spike: 无原生 Function Calling 时的工具调用协议解析器。
// 目标：给定网页助手/免费模型的原始输出，稳定判定它想调用工具还是直接回答，
// 并对「接近合法但有小瑕疵」的 JSON 做可解释的修复，而不是一律失败。
// 生产实现会按同一规则移植到 Kotlin（android/core-agent/ToolCallParser.kt），此处先验证规则集。

const TOOL_NAMES = new Set([
  'clipboard.read',
  'notifications.list',
  'calendar.events',
  'files.list',
  'files.read',
  'apps.list',
  'share.send',
  'memory.search',
  'memory.summary',
  'memory.remember',
]);

/** 从任意文本里扫描平衡的 JSON 对象（正确处理字符串与转义）。 */
export function scanJsonObjects(text) {
  const out = [];
  for (let i = 0; i < text.length; i++) {
    if (text[i] !== '{') continue;
    let depth = 0;
    let inStr = false;
    let esc = false;
    for (let j = i; j < text.length; j++) {
      const c = text[j];
      if (inStr) {
        if (esc) esc = false;
        else if (c === '\\') esc = true;
        else if (c === '"') inStr = false;
        continue;
      }
      if (c === '"') inStr = true;
      else if (c === '{') depth++;
      else if (c === '}') {
        depth--;
        if (depth === 0) {
          out.push(text.slice(i, j + 1));
          i = j;
          break;
        }
      }
    }
  }
  return out;
}

/** 宽容修复：尾逗号、中文引号、全角冒号、BOM、Markdown 强调符。 */
export function repairJson(raw) {
  let s = raw.replace(/^\uFEFF/, '').trim();
  s = s.replace(/“[^”]*”/g, (m) => '"' + m.slice(1, -1) + '"');
  s = s.replace(/[“”]/g, '"');
  s = s.replace(/：/g, ':');
  s = s.replace(/,\s*([}\]])/g, '$1'); // 尾逗号
  s = s.replace(/\*\*/g, '');
  return s;
}

function asArgs(v) {
  if (v && typeof v === 'object' && !Array.isArray(v)) return v;
  if (typeof v === 'string') {
    const objs = scanJsonObjects(v);
    if (objs.length) {
      try {
        const p = JSON.parse(repairJson(objs[0]));
        if (p && typeof p === 'object' && !Array.isArray(p)) return p;
      } catch { /* ignore */ }
    }
  }
  return null;
}

/**
 * @returns {{kind:'tool'|'final'|'prose'|'invalid', name?:string, args?:object,
 *            text?:string, reason?:string, repaired?:boolean}}
 */
export function parseAgentOutput(text) {
  if (typeof text !== 'string' || !text.trim()) {
    return { kind: 'invalid', reason: 'empty' };
  }
  const stripped = text
    .replace(/```(?:json|JSON)?/g, '')
    .replace(/```/g, '');

  // 根节点是数组：协议要求单轮单调用。只含 1 个调用可以宽容接受，
  // 含多个不同调用则判无效——静默只执行第一个会悄悄丢掉模型的另一半意图。
  const trimmed = stripped.trim();
  if (trimmed.startsWith('[')) {
    try {
      const arr = JSON.parse(repairJson(trimmed));
      if (Array.isArray(arr) && arr.length > 1) {
        return { kind: 'invalid', reason: 'multiple_tool_calls' };
      }
    } catch { /* 交给下面的扫描处理 */ }
  }

  const candidates = scanJsonObjects(stripped);
  let sawMalformedTool = false;

  for (const cand of candidates) {
    let obj;
    let repaired = false;
    try {
      obj = JSON.parse(cand);
    } catch {
      try {
        obj = JSON.parse(repairJson(cand));
        repaired = true;
      } catch {
        continue;
      }
    }
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) continue;

    // 兼容模型把工具名放在 name / function / tool_name 上的情况
    const rawName = obj.tool ?? obj.name ?? obj.function ?? obj.tool_name;
    const rawArgs = obj.args ?? obj.arguments ?? obj.parameters ?? {};

    if (typeof rawName === 'string' && rawName.trim()) {
      const name = rawName.trim();
      const args = asArgs(rawArgs);
      if (!TOOL_NAMES.has(name)) {
        sawMalformedTool = true;
        continue;
      }
      if (args === null) {
        sawMalformedTool = true;
        continue;
      }
      return { kind: 'tool', name, args, repaired };
    }

    if (typeof obj.final === 'string' || typeof obj.answer === 'string') {
      const t = (obj.final ?? obj.answer).trim();
      if (t) return { kind: 'final', text: t, repaired };
    }
  }

  // 没有任何可用 JSON：有正文就当作直接回答（优雅降级），否则才算失败
  const plain = stripped.trim();
  if (plain && !/^[{[]/.test(plain)) {
    return { kind: 'prose', text: plain };
  }
  return { kind: 'invalid', reason: sawMalformedTool ? 'bad_tool_shape' : 'no_json' };
}

/** 单轮里工具调用失败时的回喂文本（生产实现同款）。 */
export function repairPrompt(err, allowedTools) {
  return [
    '你的上一条输出无法被解析为工具调用。',
    `错误：${err}`,
    '请只输出一个 JSON 对象，不要解释、不要 Markdown 代码块。',
    '调用工具格式：{"tool":"<工具名>","args":{...}}',
    '直接回答格式：{"final":"<回答>"}',
    `可用工具：${allowedTools.join(', ')}`,
  ].join('\n');
}
