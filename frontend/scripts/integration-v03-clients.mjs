import assert from 'node:assert/strict';

const base = process.env.ARCHLAB_API_URL ?? 'http://localhost:8080';
const clientTypes = ['WEB_BROWSER', 'MOBILE_APP', 'DESKTOP_APP', 'CLI', 'BOT', 'EMBEDDED_SYSTEM'];

async function post(path, payload) {
  const response = await fetch(new URL(path, base), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(10_000),
  });
  const body = await response.json();
  assert.equal(response.status, 200, `${path}: HTTP ${response.status}: ${JSON.stringify(body)}`);
  return body;
}

for (const type of clientTypes) {
  for (const kind of ['HTTP_REST', 'SOAP_HTTP']) {
    for (const withDatabase of [false, true]) {
      const id = `${type.toLowerCase()}-${kind.toLowerCase()}-${withDatabase ? 'db' : 'direct'}`;
      // Mirrors the editor's v2 adapter: only the wire type changes; IDs and names remain specific.
      const architecture = {
        components: [
          { id, type: 'CLIENT', displayName: `${type} 1` },
          { id: `${id}-api`, type: 'API', displayName: 'API 1', preProcessingMs: 1, postProcessingMs: 1 },
          ...(withDatabase ? [{ id: `${id}-db`, type: 'DATABASE', displayName: 'Database 1', processingMs: 1 }] : []),
        ],
        connections: [
          { id: `${id}-entry`, sourceId: id, targetId: `${id}-api`, latencyMs: 1,
            integration: kind === 'SOAP_HTTP' ? { kind, soapVersion: '1.2', httpStatusCode: 200 } : { kind, request: { method: 'GET', path: '/' }, response: { statusCode: 200 } } },
          ...(withDatabase ? [{ id: `${id}-dependency`, sourceId: `${id}-api`, targetId: `${id}-db`, latencyMs: 1,
            integration: { kind: 'DATABASE_QUERY', queryText: '', responseText: '' } }] : []),
        ],
      };
      const scenario = { entryComponentId: id, entryConnectionId: `${id}-entry`,
        apiExecutions: withDatabase ? [{ componentId: `${id}-api`, dependencyConnectionId: `${id}-dependency` }] : [] };
      const request = { architecture, scenario };
      const validation = await post('/api/v2/scenarios/validate', request);
      assert.equal(validation.valid, true, `${id}: ${JSON.stringify(validation.violations)}`);
      const result = await post('/api/v2/simulations', request);
      assert.equal(result.execution.status, 'COMPLETED');
      assert.equal(result.execution.entryComponentId, id);
      assert.equal(result.execution.entryConnectionId, `${id}-entry`);
      assert.ok(result.trace.length > 0, `${id}: trace vazio`);
      assert.ok(result.timeline.length > 0, `${id}: timeline vazia`);
      assert.ok(result.trace.some(event => event.entityId === id || event.sourceId === id || event.targetId === id), `${id}: cliente ausente do trace`);
      assert.ok(result.metrics.totalDurationMs >= 0, `${id}: métrica ausente`);
      console.log(`✓ ${type} ${kind} ${withDatabase ? 'com' : 'sem'} Database`);
    }
  }
}
console.log('PASS: 24 cenários v2 reais');
