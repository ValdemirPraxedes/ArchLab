import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const base = process.env.ARCHLAB_API_URL ?? 'http://localhost:8080';
const input = JSON.parse(await readFile(new URL('../../docs/examples/v3/ce-ref-01-request.json', import.meta.url), 'utf8'));
async function post(path, payload, status = 200) {
  const response = await fetch(new URL('/api/v3/' + path, base), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: AbortSignal.timeout(10000) });
  const result = await response.json(); assert.equal(response.status, status, JSON.stringify(result)); return result;
}
assert.equal((await post('architectures/validate', input.architecture)).valid, true);
assert.equal((await post('execution-plans/validate', { architecture: input.architecture, executionPlan: input.architecture.executionPlans[0] })).valid, true);
assert.equal((await post('scenarios/validate', input)).valid, true);
const result = await post('simulations', input); assert.equal(result.principal.responseDurationMs, 120); assert.equal(result.global.completionDurationMs, 210); assert.equal(result.metrics.backgroundDurationAfterResponseMs, 90);
assert.equal(result.integrity.publicSnapshotReconstructible, true); assert.equal(result.integrity.playbackReconstructible, false); assert.deepEqual(await post('simulations', input), result);
const partial = await post('simulations', { ...input, options: { seed: 7, completionMode: 'UNTIL_HORIZON', horizonAtMs: 150 } }); assert.equal(partial.global.status, 'HORIZON_REACHED'); assert.equal(partial.global.completionAtMs, null);
const invalid = structuredClone(input); invalid.scenario.entry.componentId = 'missing'; assert.equal((await post('scenarios/validate', invalid)).valid, false); assert.ok((await post('simulations', invalid, 422)).violations.length);
const failed = structuredClone(input); failed.scenario.initialStates = [{ kind: 'COMPONENT', componentId: 'db', available: false }]; assert.equal((await post('simulations', failed)).principal.status, 'FAILED');
console.log('PASS v3 real HTTP: four endpoints, CE-REF-01 120/210/90, deterministic result, horizon150, invalid422 and simulated failure200.');
