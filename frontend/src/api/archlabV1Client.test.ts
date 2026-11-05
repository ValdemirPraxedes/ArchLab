import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { ArchlabV1Client, ArchlabV1HttpError } from './archlabV1Client';
import type { ArchitectureV1, SimulationRequestV1 } from './archlabV1Client';

const architecture: ArchitectureV1 = {
  components: [{id: 'c', type: 'WEB_BROWSER', displayName: 'Browser'},
    {id: 'a', type: 'API', displayName: 'API', behaviors: [], preProcessingMs: 0, postProcessingMs: 0}],
  connections: [{id: 'ca', sourceId: 'c', targetId: 'a', latencyMs: 0, integration: {kind: 'HTTP_REST'}}],
};
const request: SimulationRequestV1 = {architecture,
  scenario: {kind: 'REQUEST_RESPONSE', entryComponentId: 'c', entryConnectionId: 'ca', apiExecutions: []}};
const client = new ArchlabV1Client();

describe('cliente v1 canônico', () => {
  it('usa as três rotas e preserva o tipo visual', async () => {
    for (const route of ['architectures/validate', 'scenarios/validate', 'simulations']) {
      server.use(http.post(`/api/v1/${route}`, async ({request: incoming}) => {
        const body = await incoming.json() as {architecture?: ArchitectureV1; components?: ArchitectureV1['components']};
        expect(body.architecture?.components[0].type ?? body.components?.[0].type).toBe('WEB_BROWSER');
        return HttpResponse.json({valid: true, violations: []});
      }));
    }
    await client.validateArchitecture(architecture);
    await client.validateScenario(request);
    await client.simulate(request);
  });
  it.each([400, 413, 415, 500])('preserva Problem no HTTP %i', async status => {
    server.use(http.post('/api/v1/architectures/validate', () => HttpResponse.json({
      type: 'about:blank', title: 'Erro', status, code: 'INVALID_REQUEST', detail: `HTTP ${status}`,
    }, {status})));
    await expect(client.validateArchitecture(architecture)).rejects.toMatchObject({status, problem: {code: 'INVALID_REQUEST'}});
  });
  it('expõe violações do HTTP 422', async () => {
    const violations = [{code: 'INVALID_ENTRY', path: 'scenario.entryConnectionId', message: 'Inválida'}];
    server.use(http.post('/api/v1/simulations', () => HttpResponse.json({
      type: 'about:blank', title: 'Inválido', status: 422, code: 'SIMULATION_INVALID', violations,
    }, {status: 422})));
    try { await client.simulate(request); expect.fail('Esperado HTTP 422'); }
    catch (cause) {
      expect(cause).toBeInstanceOf(ArchlabV1HttpError);
      expect((cause as ArchlabV1HttpError).violations).toEqual(violations);
    }
  });
});
