import assert from 'node:assert/strict';

const base = process.env.ARCHLAB_API_URL ?? 'http://localhost:8080';
const architecture = {
  components: [
    { id: 'client-1', type: 'CLIENT' },
    { id: 'api-1', type: 'API', preProcessingMs: 30, postProcessingMs: 20 },
    { id: 'db-1', type: 'DATABASE', processingMs: 150 },
  ],
  connections: [
    { id: 'connection-1', sourceId: 'client-1', targetId: 'api-1', protocol: 'REST', latencyMs: 20 },
    { id: 'connection-2', sourceId: 'api-1', targetId: 'db-1', protocol: 'TCP', latencyMs: 10 },
  ],
};
async function request(path, payload, expected = 200) {
  const response = await fetch(new URL(path, base), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(10_000),
  });
  const body = await response.json();
  assert.equal(response.status, expected, `${path}: esperado HTTP ${expected}; recebido ${response.status}: ${JSON.stringify(body)}`);
  return body;
}
function snapshot(result, time, sequence = Infinity) {
  const components = { ...result.initialState.components };
  const requests = { ...result.initialState.requests };
  const exchanges = { ...result.initialState.exchanges };
  for (const event of [...result.trace].sort((a, b) => a.timestampMs - b.timestampMs || a.sequence - b.sequence)) {
    if (event.timestampMs > time || (event.timestampMs === time && event.sequence > sequence)) break;
    if (event.type !== 'STATE_TRANSITION') continue;
    const target = { COMPONENT: components, REQUEST: requests, EXCHANGE: exchanges }[event.entityKind];
    assert.ok(target, 'Tipo de entidade desconhecido');
    target[event.entityId] = event.newState;
  }
  return { components, requests, exchanges };
}
try {
  const validation = await request('/api/v1/architectures/validate', architecture);
  assert.equal(validation.valid, true);
  assert.deepEqual(validation.violations, []);
  console.log('✓ Validação real: arquitetura válida');

  const result = await request('/api/v1/simulations', architecture);
  assert.equal(result.execution.status, 'COMPLETED');
  assert.equal(result.execution.totalDurationMs, 260);
  assert.equal(result.metrics.apiWaitingMs, 170);
  assert.equal(result.metrics.apiPreProcessingMs, 30);
  assert.equal(result.metrics.apiPostProcessingMs, 20);
  assert.equal(result.metrics.databaseProcessingMs, 150);
  assert.equal(result.metrics.communicationMs, 60);
  assert.equal(result.timeline.length, 7);
  assert.ok(result.trace.length >= 7, 'Trace deve conter os eventos necessários à execução');
  assert.deepEqual(result.trace.map((event) => event.sequence), result.trace.map((_, i) => i + 1),
    'Sequências do trace devem ser consecutivas');
  assert.ok(result.trace.every((event, i, all) => i === 0 || event.timestampMs >= all[i - 1].timestampMs),
    'Trace deve estar em ordem cronológica');
  for (const [timestampMs, newState] of [[50, 'WAITING'], [220, 'PROCESSING']]) {
    assert.ok(result.trace.some((event) => event.type === 'STATE_TRANSITION'
      && event.entityKind === 'COMPONENT' && event.entityId === 'api-1'
      && event.timestampMs === timestampMs && event.newState === newState),
    `Falta transição API→${newState} em ${timestampMs} ms`);
  }
  console.log(`Trace retornado: ${result.trace.length} eventos (quantidade não fixa pelo contrato)`);
  assert.equal(snapshot(result, 50).components['api-1'], 'WAITING');
  assert.equal(snapshot(result, 219).components['api-1'], 'WAITING');
  assert.equal(snapshot(result, 220).components['api-1'], 'PROCESSING');
  assert.equal(snapshot(result, 260).components['client-1'], 'COMPLETED');
  console.log('✓ Simulação real: 260 ms, 170 ms em WAITING, 7 atividades e trace válido');

  const invalid = { ...architecture, components: architecture.components.filter((c) => c.type !== 'DATABASE') };
  const rejected = await request('/api/v1/architectures/validate', invalid);
  assert.equal(rejected.valid, false);
  assert.ok(rejected.violations.length > 0);
  console.log('✓ Validação real: arquitetura incompleta rejeitada');

  const duplicate = { ...architecture, connections: [...architecture.connections, { ...architecture.connections[0], id: 'connection-duplicate' }] };
  const duplicateResult = await request('/api/v1/architectures/validate', duplicate);
  assert.equal(duplicateResult.valid, false);
  console.log('✓ Validação real: conexão duplicada rejeitada');

  const zero = { components: architecture.components.map((c) => c.type === 'DATABASE' ? { ...c, processingMs: 0 } : c.type === 'API' ? { ...c, preProcessingMs: 0, postProcessingMs: 0 } : c), connections: architecture.connections.map((c) => ({ ...c, latencyMs: 0 })) };
  const zeroResult = await request('/api/v1/simulations', zero);
  assert.equal(zeroResult.execution.totalDurationMs, 0);
  console.log('✓ Simulação real: durações zero');

  console.log('PASS: todos os cenários reais passaram');
} catch (error) {
  console.error('FAIL:', error);
  process.exitCode = 1;
}
