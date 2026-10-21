import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

export const server = setupServer(
  http.post('/api/v1/architectures/validate', () => HttpResponse.json({ valid: true, violations: [] })),
  http.post('/api/v1/simulations', () => HttpResponse.json({
    execution: { id: 'exec-001', status: 'COMPLETED', totalDurationMs: 260 },
    initialState: { components: { 'client-1': 'IDLE', 'api-1': 'IDLE', 'db-1': 'IDLE' }, requests: { 'req-001': 'CREATED' }, exchanges: {} },
    trace: [], timeline: [], metrics: { apiPreProcessingMs: 30, apiPostProcessingMs: 20, databaseProcessingMs: 150, communicationMs: 60, apiWaitingMs: 170 },
  })),
);
