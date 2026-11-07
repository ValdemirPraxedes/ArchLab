// V6-23 / decision 4: replays the worst-case envelopes in performance/P6-*.json against a running API
// (start it with -Xmx256m), checks each controlled result, the maxWallMs ceiling and byte-identical
// responses, and reports wall-time percentiles per case. Exits non-zero on any violation.
const fs = require('node:fs');
const path = require('node:path');
const {performance} = require('node:perf_hooks');

const base = process.env.ARCHLAB_API_URL || 'http://127.0.0.1:18081';
const repeats = Number(process.env.ARCHLAB_LIMITS_REPEATS || 5);
const concurrency = Number(process.env.ARCHLAB_LIMITS_CONCURRENCY || 4);
if (!Number.isInteger(repeats) || repeats < 1 || repeats > 50 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 8)
  throw new Error('ARCHLAB_LIMITS_REPEATS must be 1..50 and ARCHLAB_LIMITS_CONCURRENCY 1..8');

const dir = path.join(__dirname, 'performance');
const cases = fs.readdirSync(dir).filter(f => /^P6-\d\d\.json$/.test(f)).sort()
  .flatMap(file => { const f = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8')); return f.cases.map(c => ({id: f.id, ...c})); });

async function call(c) {
  const start = performance.now();
  const response = await fetch(new URL(c.endpoint, base), {method: 'POST', headers: {'content-type': 'application/json'},
    body: JSON.stringify(c.request), signal: AbortSignal.timeout(c.maxWallMs * 5)});
  const text = await response.text();
  const ms = performance.now() - start;
  if (response.status !== c.expect.status) throw new Error(`${c.id}/${c.name}: HTTP ${response.status} (expected ${c.expect.status}) ${text.slice(0, 300)}`);
  for (const needle of c.expect.bodyContains || []) if (!text.includes(needle)) throw new Error(`${c.id}/${c.name}: missing ${needle}`);
  if (ms > c.maxWallMs) throw new Error(`${c.id}/${c.name}: ${ms.toFixed(1)} ms exceeds ${c.maxWallMs} ms`);
  return {ms, text};
}

(async () => {
  const report = [];
  for (const c of cases) {
    const reference = (await call(c)).text; // warm-up, also the determinism reference
    const samples = [];
    let next = 0;
    await Promise.all(Array.from({length: Math.min(concurrency, repeats)}, async () => {
      while (next < repeats) { next++; const r = await call(c); if (r.text !== reference) throw new Error(`${c.id}/${c.name}: response changed`); samples.push(r.ms); }
    }));
    samples.sort((a, b) => a - b);
    const pct = p => Number(samples[Math.ceil(samples.length * p) - 1].toFixed(1));
    report.push({case: `${c.id}/${c.name}`, status: c.expect.status, runs: repeats, concurrency, p50Ms: pct(.5), maxMs: pct(1), limitMs: c.maxWallMs, responseBytes: reference.length});
  }
  console.log(JSON.stringify(report, null, 2));
})().catch(error => { console.error(error.message || error); process.exitCode = 1; });
