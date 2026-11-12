// E2 运行器：统计解析器在「真实脏输出」上的通过率，并按类目给出失败细节。
import { cases } from './cases.mjs';
import { parseAgentOutput, repairJson } from './protocol.mjs';

function check(expect, actual) {
  if (expect.kind !== actual.kind) return `kind 期望 ${expect.kind} 实际 ${actual.kind}`;
  if (expect.name && expect.name !== actual.name) return `name 期望 ${expect.name} 实际 ${actual.name}`;
  return null;
}

const rows = [];
for (const c of cases) {
  const t0 = process.hrtime.bigint();
  const actual = parseAgentOutput(c.input);
  const us = Number(process.hrtime.bigint() - t0) / 1000;
  const err = check(c.expect, actual);
  rows.push({ c, actual, err, us });
}

const pass = rows.filter((r) => !r.err);
const byCat = {};
for (const r of rows) {
  const cat = r.c.cat;
  byCat[cat] ??= { total: 0, pass: 0 };
  byCat[cat].total++;
  if (!r.err) byCat[cat].pass++;
}

console.log(`E2 工具协议解析器 —— 用例 ${rows.length}，通过 ${pass.length}，通过率 ${(pass.length / rows.length * 100).toFixed(1)}%`);
console.log('');
console.log('类目            通过/总数');
for (const [cat, s] of Object.entries(byCat)) {
  console.log(`${cat.padEnd(14)} ${s.pass}/${s.total}`);
}
if (pass.length !== rows.length) {
  console.log('\n失败用例：');
  for (const r of rows.filter((x) => x.err)) {
    console.log(`- [${r.c.id}] ${r.err}`);
    console.log(`  输入: ${JSON.stringify(r.c.input).slice(0, 160)}`);
    console.log(`  实际: ${JSON.stringify(r.actual).slice(0, 160)}`);
  }
}

const repaired = rows.filter((r) => r.actual.repaired).length;
console.log(`\n其中经修复后成功: ${repaired} 例；平均解析耗时 ${(rows.reduce((a, r) => a + r.us, 0) / rows.length).toFixed(1)}µs`);

// 附带自检：repairJson 幂等性
const sample = '{"a":1,}';
console.log(`repairJson 幂等: ${repairJson(repairJson(sample)) === repairJson(sample)}`);

process.exitCode = pass.length === rows.length ? 0 : 1;
