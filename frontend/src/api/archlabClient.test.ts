import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { ArchlabClient, ArchlabHttpError } from './archlabClient';
import type { ArchitectureRequest } from './archlabClient';
import { server } from '../test/server';

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
} as ArchitectureRequest;

describe('ArchlabClient', () => {
  const client = new ArchlabClient();

  it('valida pelo endpoint REST', async () => {
    await expect(client.validate(architecture)).resolves.toEqual({ valid: true, violations: [] });
  });

  it('trata domínio inválido retornado com 200', async () => {
    server.use(http.post('/api/v1/architectures/validate', () => HttpResponse.json({ valid: false, violations: [{ code: 'MISSING_COMPONENT', path: 'components', message: 'Database ausente' }] })));
    const result = await client.validate(architecture);
    expect(result.valid).toBe(false);
    expect(result.violations).toHaveLength(1);
  });

  it('consome resposta da simulação sem calcular métricas', async () => {
    const result = await client.simulate(architecture);
    expect(result.execution.totalDurationMs).toBe(260);
    expect(result.metrics.apiWaitingMs).toBe(170);
  });

  it('preserva status e Problem Details no erro 422', async () => {
    server.use(http.post('/api/v1/simulations', () => HttpResponse.json({ title: 'Arquitetura inválida', status: 422, code: 'INVALID_ARCHITECTURE', violations: [] }, { status: 422 })));
    await expect(client.simulate(architecture)).rejects.toMatchObject({ status: 422, name: 'ArchlabHttpError' });
    try { await client.simulate(architecture); } catch (error) {
      expect(error).toBeInstanceOf(ArchlabHttpError);
      expect((error as ArchlabHttpError).problem).toMatchObject({ code: 'INVALID_ARCHITECTURE' });
    }
  });
});
