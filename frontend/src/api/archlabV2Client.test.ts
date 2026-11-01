import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { ArchlabV2Client, ArchlabV2HttpError } from './archlabV2Client';
import type { ArchitectureV2, SimulationRequestV2 } from './archlabV2Client';

const architecture: ArchitectureV2 = {
  components: [
    { id: 'client-1', type: 'CLIENT', displayName: 'Client' },
    { id: 'api-1', type: 'API', displayName: 'API', preProcessingMs: 30, postProcessingMs: 20 },
  ],
  connections: [{
    id: 'edge-1', sourceId: 'client-1', targetId: 'api-1',
    latencyMs: 10, integration: { kind: 'HTTP_REST' },
  }],
};
const request: SimulationRequestV2 = {
  architecture,
  scenario: { entryComponentId: 'client-1', entryConnectionId: 'edge-1', apiExecutions: [] },
};
const client = new ArchlabV2Client();

const problem = (status: number) => ({
  type: 'about:blank', title: 'Erro de contrato', status, code: 'INVALID_REQUEST', detail: `Erro HTTP ${status}`,
});
describe('contrato HTTP v2 isolado da v1', () => {
  it('envia a arquitetura para o endpoint v2 correto e aceita valid:false como domínio', async () => {
    server.use(http.post('/api/v2/architectures/validate', async ({ request: incoming }) => {
      expect(await incoming.json()).toEqual(architecture);
      return HttpResponse.json({ valid: false, violations: [{
        code: 'INVALID_CONNECTION', path: 'connections[0]', message: 'Conexão inválida',
      }] });
    }));
    const result = await client.validateArchitecture(architecture);
    expect(result.valid).toBe(false);
    expect(result.violations[0].path).toBe('connections[0]');
  });
  it('valida cenário separadamente e aceita valid:true', async () => {
    server.use(http.post('/api/v2/scenarios/validate', async ({ request: incoming }) => {
      expect(await incoming.json()).toEqual(request);
      return HttpResponse.json({ valid: true, violations: [] });
    }));
    await expect(client.validateScenario(request)).resolves.toEqual({ valid: true, violations: [] });
  });
  it('consome resultado v2 sem interpretar mensagens ou métricas', async () => {
    const result = { execution: { id: 'exec-1', status: 'COMPLETED', totalDurationMs: 20 },
      initialState: { components: {}, requests: {}, exchanges: {} }, trace: [], timeline: [],
      metrics: {}, messageRepresentations: [] };
    server.use(http.post('/api/v2/simulations', async ({ request: incoming }) => {
      expect(await incoming.json()).toEqual(request);
      return HttpResponse.json(result);
    }));
    expect(await client.simulate(request)).toEqual(result);
  });
  it.each([400, 413, 415, 500])('preserva Problem Details no HTTP %i', async (status) => {
    server.use(http.post('/api/v2/architectures/validate', () =>
      HttpResponse.json(problem(status), { status, headers: { 'Content-Type': 'application/problem+json' } })));
    await expect(client.validateArchitecture(architecture)).rejects.toMatchObject({
      name: 'ArchlabV2HttpError', status, problem: problem(status),
    });
  });
  it('preserva violações no erro HTTP 422 da simulação', async () => {
    const violations = [{ code: 'INVALID_SCENARIO_DEPENDENCY', path: 'scenario.apiExecutions[0]', message: 'Referência inválida' }];
    server.use(http.post('/api/v2/simulations', () => HttpResponse.json({
      ...problem(422), violations,
    }, { status: 422, headers: { 'Content-Type': 'application/problem+json' } })));
    try {
      await client.simulate(request);
      expect.fail('Era esperado erro 422');
    } catch (cause) {
      expect(cause).toBeInstanceOf(ArchlabV2HttpError);
      const error = cause as ArchlabV2HttpError;
      expect(error.status).toBe(422);
      expect(error.violations).toEqual(violations);
    }
  });
  it('trata corpo não JSON sem mascarar status HTTP', async () => {
    server.use(http.post('/api/v2/scenarios/validate', () =>
      new HttpResponse('Erro', { status: 500, headers: { 'Content-Type': 'text/plain' } })));
    await expect(client.validateScenario(request)).rejects.toMatchObject({ status: 500, problem: null });
  });
});
