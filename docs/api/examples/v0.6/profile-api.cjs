const fs = require('node:fs');
const path = require('node:path');
const {performance} = require('node:perf_hooks');

const endpoint = new URL('/api/v1/simulations', process.env.ARCHLAB_API_URL || 'http://127.0.0.1:18081');
const iterations = Number(process.env.ARCHLAB_PROFILE_ITERATIONS || 200);
const concurrency = Number(process.env.ARCHLAB_PROFILE_CONCURRENCY || 8);
if (!Number.isInteger(iterations) || iterations < 1 || iterations > 1000 ||
    !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 32)
  throw new Error('Iterations must be 1..1000 and concurrency 1..32');

const profiles = {
  relational: {file: 'relational-insert-select.json', codes: ['INSERTED', 'FOUND']},
  etl: {file: 'etl-identity-simulation.json', codes: ['EXTRACTED', 'TRANSFORMED', 'LOADED']},
};
const profileName = process.env.ARCHLAB_PROFILE_KIND || 'relational';
const profile = profiles[profileName];
if (!profile) throw new Error('ARCHLAB_PROFILE_KIND must be relational or etl');
const body = fs.readFileSync(path.join(__dirname, profile.file), 'utf8');
const samples = [];
let reference;

async function runOne(record) {
  const start = performance.now();
  const response = await fetch(endpoint, {
    method: 'POST', headers: {'content-type': 'application/json'}, body,
    signal: AbortSignal.timeout(10_000),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 400)}`);
  const result = JSON.parse(text);
  const outcomes = result.dataOutcomes || [];
  if (JSON.stringify(outcomes.map(outcome => outcome.resultCode)) !== JSON.stringify(profile.codes) ||
      (profileName === 'etl' && outcomes.some(outcome =>
        outcome.readCount !== 100 && outcome.writtenCount !== 100)))
    throw new Error(`Unexpected data outcome: ${JSON.stringify(outcomes)}`);
  if (reference === undefined) reference = text;
  else if (text !== reference) throw new Error('Response changed across isolated runs');
  if (record) samples.push(performance.now() - start);
}

async function batch(count, record) {
  let cursor = 0;
  await Promise.all(Array.from({length: Math.min(concurrency, count)}, async () => {
    while (cursor < count) {
      cursor++;
      await runOne(record);
    }
  }));
}

(async () => {
  await batch(10, false);
  const start = performance.now();
  await batch(iterations, true);
  const elapsedMs = performance.now() - start;
  samples.sort((a, b) => a - b);
  const percentile = p => Number(samples[Math.ceil(samples.length * p) - 1].toFixed(2));
  console.log(JSON.stringify({profile: profileName, requests: iterations, concurrency,
    elapsedMs: Number(elapsedMs.toFixed(2)), requestsPerSecond: Number((iterations * 1000 / elapsedMs).toFixed(2)),
    latencyMs: {p50: percentile(.5), p95: percentile(.95), max: percentile(1)},
    deterministic: true, outcome: profile.codes}, null, 2));
})().catch(error => {console.error(error); process.exitCode = 1;});
