// V6-22: freezes the historical request corpus of a checkout (default: the v0.5 release, origin/main).
// Usage: node harvest-corpus.cjs <repo-root> > corpus-v05.json
// Sources: request examples of openapi.yaml / openapi-v2.yaml, docs/api/examples/**.json requests and the
// JSON text blocks embedded in backend tests. Responses and templates (%s) are skipped; bodies are deduplicated.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const YAML = require(path.join(__dirname, '..', 'v0.6', 'node_modules', 'yaml'));

const root = path.resolve(process.argv[2] || '.');
const entries = new Map();
const isRequest = (v) => v && typeof v === 'object' && !Array.isArray(v) && !('execution' in v) && !('valid' in v)
  && ('architecture' in v || 'components' in v);
function add(source, body) {
  if (!isRequest(body)) return;
  const text = JSON.stringify(body);
  const id = crypto.createHash('sha256').update(text).digest('hex').slice(0, 12);
  if (!entries.has(id)) entries.set(id, {id, source, body});
}
for (const [file, prefix] of [['docs/api/openapi.yaml', 'openapi'], ['docs/api/openapi-v2.yaml', 'openapi-v2']]) {
  const doc = YAML.parse(fs.readFileSync(path.join(root, file), 'utf8'));
  for (const [p, ops] of Object.entries(doc.paths || {})) for (const [m, op] of Object.entries(ops))
    for (const [name, ex] of Object.entries(op?.requestBody?.content?.['application/json']?.examples || {}))
      add(`${prefix}:${m.toUpperCase()} ${p}#${name}`, ex.value);
  for (const [name, ex] of Object.entries(doc.components?.examples || {})) add(`${prefix}:components.examples.${name}`, ex.value);
}
(function walk(dir) {
  for (const f of fs.readdirSync(dir, {withFileTypes: true})) {
    const full = path.join(dir, f.name);
    if (f.isDirectory()) { if (f.name !== 'node_modules') walk(full); }
    else if (f.name.endsWith('.json') && !f.name.startsWith('package')) {
      try { add(path.relative(root, full), JSON.parse(fs.readFileSync(full, 'utf8'))); } catch { /* not JSON */ }
    }
  }
})(path.join(root, 'docs', 'api', 'examples'));
(function walk(dir) {
  for (const f of fs.readdirSync(dir, {withFileTypes: true})) {
    const full = path.join(dir, f.name);
    if (f.isDirectory()) walk(full);
    else if (f.name.endsWith('Test.java')) {
      const src = fs.readFileSync(full, 'utf8');
      let n = 0;
      for (const m of src.matchAll(/"""\s*\n([\s\S]*?)"""/g)) {
        n++;
        if (/%[sd]/.test(m[1])) continue;
        try { add(`${path.relative(root, full)}#block${n}`, JSON.parse(m[1])); } catch { /* not standalone JSON */ }
      }
    }
  }
})(path.join(root, 'backend'));
const list = [...entries.values()].sort((a, b) => a.source.localeCompare(b.source));
process.stdout.write(JSON.stringify({id: 'CORPUS-V05', baseline: process.env.CORPUS_BASELINE || null,
  description: 'Historical request corpus frozen from the v0.5 release (origin/main). Each body is posted to every /api/v1 and /api/v2 endpoint by diff-baseline.cjs and HistoricalRegressionV6Test.',
  entries: list}, null, 1) + '\n');
