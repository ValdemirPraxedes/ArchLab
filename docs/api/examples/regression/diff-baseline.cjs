// V6-22: differential regression. Posts every corpus entry to every /api/v1 and /api/v2 endpoint of a
// baseline API (the v0.5 release) and of a candidate API, and compares status + canonical JSON body.
// Only one difference is allowed, and only when additive: a top-level "dataOutcomes": [] the baseline lacks.
// Env: BASELINE_URL, CANDIDATE_URL; GOLDEN_OUT=<file> writes baseline status + sha256 per call.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const ENDPOINTS = ['/api/v1/architectures/validate', '/api/v1/scenarios/validate', '/api/v1/simulations',
  '/api/v2/architectures/validate', '/api/v2/scenarios/validate', '/api/v2/simulations'];
const corpus = JSON.parse(fs.readFileSync(path.join(__dirname, 'corpus-v05.json'), 'utf8'));
const baseline = process.env.BASELINE_URL, candidate = process.env.CANDIDATE_URL;

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object')
    return '{' + Object.keys(value).sort().map((k) => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
  return JSON.stringify(value);
}
const sha = (text) => crypto.createHash('sha256').update(text).digest('hex');
function firstDiff(a, b, at = '') {
  if (canonical(a) === canonical(b)) return null;
  if (a && b && typeof a === 'object' && typeof b === 'object' && Array.isArray(a) === Array.isArray(b)) {
    for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
      const d = firstDiff(a[k], b[k], `${at}/${k}`);
      if (d) return d;
    }
  }
  return {path: at || '/', baseline: JSON.stringify(a)?.slice(0, 160), candidate: JSON.stringify(b)?.slice(0, 160)};
}
async function call(base, endpoint, body) {
  const response = await fetch(base + endpoint, {method: 'POST', headers: {'content-type': 'application/json'},
    body: JSON.stringify(body), signal: AbortSignal.timeout(15000)});
  const text = await response.text();
  let json; try { json = JSON.parse(text); } catch { json = text; }
  return {status: response.status, json};
}
/** The only tolerated change: v0.6 adds an empty top-level dataOutcomes array to unified simulation results. */
function normalize(candidateJson, baselineJson) {
  if (candidateJson && typeof candidateJson === 'object' && Array.isArray(candidateJson.dataOutcomes)
      && candidateJson.dataOutcomes.length === 0 && !(baselineJson && 'dataOutcomes' in baselineJson)) {
    const copy = {...candidateJson}; delete copy.dataOutcomes; return copy;
  }
  return candidateJson;
}
(async () => {
  const golden = [], diffs = [];
  let calls = 0, additive = 0;
  const statuses = {};
  for (const entry of corpus.entries) for (const endpoint of ENDPOINTS) {
    const b = await call(baseline, endpoint, entry.body);
    golden.push({entry: entry.id, endpoint, status: b.status, sha256: sha(canonical(b.json))});
    statuses[b.status] = (statuses[b.status] || 0) + 1;
    calls++;
    if (!candidate) continue;
    const c = await call(candidate, endpoint, entry.body);
    const normalized = normalize(c.json, b.json);
    if (normalized !== c.json) additive++;
    if (b.status !== c.status || canonical(b.json) !== canonical(normalized))
      diffs.push({entry: entry.id, source: entry.source, endpoint, status: [b.status, c.status], first: firstDiff(b.json, normalized)});
  }
  if (process.env.GOLDEN_OUT) fs.writeFileSync(process.env.GOLDEN_OUT, JSON.stringify({id: 'GOLDEN-V05',
    baseline: corpus.baseline, canonical: 'recursively sorted keys, no whitespace', calls: golden}, null, 1) + '\n');
  console.log(JSON.stringify({entries: corpus.entries.length, endpoints: ENDPOINTS.length, calls, baselineStatuses: statuses,
    toleratedAdditiveDataOutcomes: additive, differences: diffs.length, diffs}, null, 2));
  if (candidate && diffs.length) process.exitCode = 1;
})().catch((e) => { console.error(e); process.exitCode = 1; });
