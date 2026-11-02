import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { ArchlabV3Client, ArchlabV3HttpError } from './archlabV3Client';
import type { ArchitectureV3, ExecutionPlanValidationRequestV3, SimulationRequestV3 } from './archlabV3Client';

const architecture: ArchitectureV3 = { id: 'a', schemaVersion: '3', components: [], resources: [], endpoints: [], operations: [], relations: [], executionPlans: [] };
const request: SimulationRequestV3 = { architecture, scenario: { id: 's', entry: { componentId: 'client', operationId: 'root' }, initialStates: [], parameters: {} }, options: { seed: 7, completionMode: 'DRAIN_ALL' } };
const plan: ExecutionPlanValidationRequestV3 = { architecture, executionPlan: { id: 'p', operationId: 'root', entryStepId: 'done', limits: { maxStepExecutions: 10, maxIterations: 10 }, steps: [{ id: 'done', kind: 'COMPLETE', scope: 'ACTIVATION', resultProfileId: 'ok' }] } };
const client = new ArchlabV3Client();
describe('Contrato do cliente HTTP v3', () => {
  it.each(['architectures', 'execution-plans', 'scenarios'] as const)('preserva valid:false e payload na validação %s', async (kind) => {
    const payload = kind === 'architectures' ? architecture : kind === 'execution-plans' ? plan : request;
    server.use(http.post('/api/v3/' + kind + '/validate', async ({ request: incoming }) => {
      expect(await incoming.json()).toEqual(payload);
      return HttpResponse.json({ valid: false, violations: [{ code: 'INVALID', path: '/entry', message: 'inválido', severity: 'ERROR', relatedIds: ['root'] }] });
    }));
    const result = kind === 'architectures' ? await client.validateArchitecture(architecture) : kind === 'execution-plans' ? await client.validateExecutionPlan(plan) : await client.validateScenario(request);
    expect(result.valid).toBe(false); expect(result.violations[0].relatedIds).toEqual(['root']);
  });
  it('preserva falha simulada HTTP200 e separação principal/global', async () => {
    const result = { principal: { status: 'FAILED', terminalAtMs: 120 }, global: { status: 'COMPLETED', completionAtMs: 210 } };
    server.use(http.post('/api/v3/simulations', async ({ request: incoming }) => { expect(await incoming.json()).toEqual(request); return HttpResponse.json(result); }));
    expect(await client.simulate(request)).toEqual(result);
  });
  it.each([400, 413, 415, 422, 500])('preserva Problem HTTP%i e violações', async (status) => {
    const problem = { type: '/problems/test', title: 'Falha', status, code: 'TEST', violations: [{ code: 'INVALID', path: '/entry', message: 'inválido', severity: 'ERROR', relatedIds: [] }] };
    server.use(http.post('/api/v3/simulations', () => HttpResponse.json(problem, { status })));
    try { await client.simulate(request); throw new Error('Expected HTTP error'); } catch (error) { expect(error).toBeInstanceOf(ArchlabV3HttpError); const actual = error as ArchlabV3HttpError; expect(actual.status).toBe(status); expect(actual.problem).toEqual(problem); expect(actual.violations).toEqual(problem.violations); }
  });
  it('trata erro HTTP sem JSON sem ocultar o status', async () => { server.use(http.post('/api/v3/simulations', () => new HttpResponse('proxy failure', { status: 502 }))); await expect(client.simulate(request)).rejects.toMatchObject({ status: 502, problem: null }); });
  it('distingue falha de rede de Problem HTTP', async () => { server.use(http.post('/api/v3/simulations', () => HttpResponse.error())); await expect(client.simulate(request)).rejects.toMatchObject({ name: 'ArchlabV3NetworkError' }); });
  it('distingue JSON HTTP200 inválido de erro de rede e de falha simulada',async()=>{server.use(http.post('/api/v3/simulations',()=>new HttpResponse('{invalid',{status:200,headers:{'Content-Type':'application/json'}})));await expect(client.simulate(request)).rejects.toMatchObject({name:'ArchlabV3ResponseError'});});
});
