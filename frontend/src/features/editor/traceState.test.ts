import { describe, it, expect } from 'vitest';
import { reconstructState, nextTracePoint, previousTracePoint } from './traceState';
import type { SimulationResponse } from '../../api/archlabClient';
const initial = { components: { 'api-1': 'IDLE' }, requests: { 'req-1': 'CREATED' }, exchanges: {} } as SimulationResponse['initialState'];
const trace = [
 { type: 'STATE_TRANSITION', timestampMs: 50, sequence: 2, entityKind: 'COMPONENT', entityId: 'api-1', previousState: 'PROCESSING', newState: 'WAITING', cause: 'DATABASE_QUERY_SENT' },
 { type: 'STATE_TRANSITION', timestampMs: 20, sequence: 1, entityKind: 'COMPONENT', entityId: 'api-1', previousState: 'IDLE', newState: 'PROCESSING', cause: 'API_REQUEST_RECEIVED' },
 { type: 'STATE_TRANSITION', timestampMs: 220, sequence: 4, entityKind: 'COMPONENT', entityId: 'api-1', previousState: 'WAITING', newState: 'PROCESSING', cause: 'DATABASE_RESPONSE_RECEIVED' },
 { type: 'STATE_TRANSITION', timestampMs: 220, sequence: 3, entityKind: 'REQUEST', entityId: 'req-1', previousState: 'CREATED', newState: 'IN_PROGRESS', cause: 'DATABASE_RESPONSE_RECEIVED' },
] as SimulationResponse['trace'];
describe('reconstrução de estados', () => {
 it('API fica aguardando dos 50 até antes dos 220 ms', () => {
  expect(reconstructState(initial,trace,49).components['api-1']).toBe('PROCESSING');
  expect(reconstructState(initial,trace,50).components['api-1']).toBe('WAITING');
  expect(reconstructState(initial,trace,219).components['api-1']).toBe('WAITING');
  expect(reconstructState(initial,trace,220).components['api-1']).toBe('PROCESSING');
 });
 it('respeita sequence no mesmo timestamp e permite voltar', () => {
  expect(reconstructState(initial,trace,220,3).components['api-1']).toBe('WAITING');
  expect(reconstructState(initial,trace,220,4).components['api-1']).toBe('PROCESSING');
  expect(reconstructState(initial,trace,0).components['api-1']).toBe('IDLE');
  expect(nextTracePoint(trace,220,3)?.sequence).toBe(4);
  expect(previousTracePoint(trace,220,4)?.sequence).toBe(3);
 });
 it('não modifica o estado inicial', () => {
  reconstructState(initial,trace,220);
  expect(initial.components['api-1']).toBe('IDLE');
 });
});
