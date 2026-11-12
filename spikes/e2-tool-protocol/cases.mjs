// E2 用例集：模拟网页助手/免费模型在「文本工具协议」下真实会出现的不完美输出。
// expect.kind: tool | final | prose | invalid

export const cases = [
  // ---- 理想情况 ----
  { id: 'clean-tool', cat: 'clean', input: '{"tool":"clipboard.read","args":{"maxChars":2000}}',
    expect: { kind: 'tool', name: 'clipboard.read' } },
  { id: 'clean-final', cat: 'clean', input: '{"final":"今天有 3 个日程，最早 9:30 评审。"}',
    expect: { kind: 'final' } },
  { id: 'clean-thought', cat: 'clean',
    input: '{"thought":"需要看通知","tool":"notifications.list","args":{"sinceMinutes":60}}',
    expect: { kind: 'tool', name: 'notifications.list' } },

  // ---- Markdown 包裹 ----
  { id: 'fenced', cat: 'format', input: '```json\n{"tool":"calendar.events","args":{"from":"2026-11-13","to":"2026-11-14"}}\n```',
    expect: { kind: 'tool', name: 'calendar.events' } },
  { id: 'fenced-bare', cat: 'format', input: '```\n{"final":"好的"}\n```',
    expect: { kind: 'final' } },
  { id: 'prose-prefix', cat: 'format', input: '好的，我来查一下：\n{"tool":"apps.list","args":{}}',
    expect: { kind: 'tool', name: 'apps.list' } },
  { id: 'prose-suffix', cat: 'format', input: '{"tool":"files.list","args":{"maxEntries":50}}\n以上是本次调用。',
    expect: { kind: 'tool', name: 'files.list' } },
  { id: 'bold-markers', cat: 'format', input: '**{"tool":"apps.list","args":{}}**',
    expect: { kind: 'tool', name: 'apps.list' } },

  // ---- 小瑕疵 ----
  { id: 'trailing-comma', cat: 'repair', input: '{"tool":"clipboard.read","args":{"maxChars":500,}}',
    expect: { kind: 'tool', name: 'clipboard.read' } },
  { id: 'smart-quotes', cat: 'repair', input: '{"tool":“clipboard.read”,"args":{}}',
    expect: { kind: 'tool', name: 'clipboard.read' } },
  { id: 'fullwidth-colon', cat: 'repair', input: '{"tool"："apps.list","args"：{}}',
    expect: { kind: 'tool', name: 'apps.list' } },
  { id: 'bom', cat: 'repair', input: '\uFEFF{"final":"带 BOM 的输出"}',
    expect: { kind: 'final' } },
  { id: 'args-as-string', cat: 'repair', input: '{"tool":"files.read","args":"{\\"path\\":\\"a.txt\\",\\"maxBytes\\":1024}"}',
    expect: { kind: 'tool', name: 'files.read' } },
  { id: 'escaped-quotes', cat: 'repair', input: '{"final":"他说：\\"明天见\\"，然后走了"}',
    expect: { kind: 'final' } },
  { id: 'nested-json-in-string', cat: 'repair', input: '{"tool":"memory.search","args":{"query":"{\\"a\\":{\\"b\\":1}} 这段怎么理解","k":5}}',
    expect: { kind: 'tool', name: 'memory.search' } },

  // ---- 字段别名（不同模型的习惯） ----
  { id: 'alias-name', cat: 'alias', input: '{"name":"apps.list","arguments":{}}',
    expect: { kind: 'tool', name: 'apps.list' } },
  { id: 'alias-function', cat: 'alias', input: '{"function":"clipboard.read","parameters":{"maxChars":100}}',
    expect: { kind: 'tool', name: 'clipboard.read' } },
  { id: 'alias-answer', cat: 'alias', input: '{"answer":"这是回答"}',
    expect: { kind: 'final' } },

  // ---- 多对象 / 干扰 ----
  { id: 'two-objects-first-invalid', cat: 'multi',
    input: '{"tool":"unknown.tool","args":{}}\n{"tool":"apps.list","args":{}}',
    expect: { kind: 'tool', name: 'apps.list' } },
  { id: 'example-then-real', cat: 'multi',
    input: '例如可以这样：{"tool":"x","args":{}}\n现在执行：{"final":"完成"}',
    expect: { kind: 'final' } },

  // ---- 降级：纯文本 ----
  { id: 'prose-only', cat: 'degrade', input: '今天你有 3 个日程：9:30 评审、14:00 面试、18:00 健身。',
    expect: { kind: 'prose' } },
  { id: 'prose-with-braces', cat: 'degrade', input: '我不太确定 {这个} 是否算工具调用，直接回答你：没有日程。',
    expect: { kind: 'prose' } },
  { id: 'refusal', cat: 'degrade', input: '抱歉，我无法协助处理这类涉及个人隐私的请求。',
    expect: { kind: 'prose' } },

  // ---- 失败 ----
  { id: 'empty', cat: 'invalid', input: '', expect: { kind: 'invalid' } },
  { id: 'truncated', cat: 'invalid', input: '{"tool":"calendar.events","args":{"from":"2026-11-13"',
    expect: { kind: 'invalid' } },
  // 数组包裹单个调用：宽容接受（模型常见习惯，语义无歧义）
  { id: 'array-root-single', cat: 'repair', input: '[{"tool":"apps.list","args":{}}]',
    expect: { kind: 'tool', name: 'apps.list' } },
  // 数组里两个不同工具：协议要求单轮单调用，判为无效交由上层重试
  { id: 'array-root-multi', cat: 'invalid', input: '[{"tool":"apps.list","args":{}},{"tool":"clipboard.read","args":{}}]',
    expect: { kind: 'invalid' } },
  { id: 'tool-no-args', cat: 'invalid', input: '{"tool":"apps.list"}',
    expect: { kind: 'tool', name: 'apps.list' } }, // args 缺省按 {} 处理
  { id: 'tool-unknown-name', cat: 'invalid', input: '{"tool":"system.rm_rf","args":{}}',
    expect: { kind: 'invalid' } },
  { id: 'tool-null-args', cat: 'invalid', input: '{"tool":"apps.list","args":null}',
    expect: { kind: 'tool', name: 'apps.list' } },

  // ---- 中文与长内容 ----
  { id: 'unicode-args', cat: 'i18n', input: '{"tool":"memory.search","args":{"query":"上周和产品经理聊的发布计划","k":3}}',
    expect: { kind: 'tool', name: 'memory.search' } },
  { id: 'long-final', cat: 'i18n',
    input: JSON.stringify({ final: '日程汇总：' + '上午与供应商确认交付时间；'.repeat(40) }),
    expect: { kind: 'final' } },
  { id: 'crlf', cat: 'format', input: '第一行\r\n{"final":"第二行是 JSON"}\r\n',
    expect: { kind: 'final' } },
  { id: 'leading-space', cat: 'format', input: '   \n\t {"tool":"apps.list","args":{}}\n  ',
    expect: { kind: 'tool', name: 'apps.list' } },

  // ---- 危险动作：必须被识别为 tool，交给上层做二次确认 ----
  { id: 'dangerous-share', cat: 'danger', input: '{"tool":"share.send","args":{"text":"周报已发送","target":"wechat"}}',
    expect: { kind: 'tool', name: 'share.send' } },
  { id: 'dangerous-remember', cat: 'danger', input: '{"tool":"memory.remember","args":{"key":"偏好","value":"喜欢简洁回答"}}',
    expect: { kind: 'tool', name: 'memory.remember' } },
];
