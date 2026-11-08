import { describe, expect, it } from 'vitest';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import styles from '../../style.css?raw';
import { edgePlayback, messageDirection, playbackEdgeAppearance, PLAYBACK_EDGE_COLORS } from './playbackEdges';

type Message = { sequence: number; timestampMs: number; action: 'SENT' | 'ARRIVED'; messageId: string; exchangeId: string;
  connectionId: string; sourceId: string; targetId: string; direction: 'REQUEST' | 'RESPONSE' | 'MESSAGE' };
const message = (m: Message) => ({ type: 'MESSAGE_EVENT', requestId: 'request-1', parentExchangeId: null, intent: 'DB_INSERT', ...m });
const result = (trace: unknown[], extra: Record<string, unknown> = {}) => ({
  execution: { id: 'exec', status: 'COMPLETED', mainStatus: 'COMPLETED', requestId: 'request-1', entryComponentId: 'client',
    entryConnectionId: 'entry', totalDurationMs: 20, mode: 'UNIFIED' },
  initialState: { components: {}, requests: {}, exchanges: {} }, trace, timeline: [], messageRepresentations: [],
  metrics: { totalDurationMs: 20, processingByComponent: [], communicationByConnection: [], waitingByComponent: [], exchanges: [] },
  ...extra,
}) as unknown as SimulationResponseV2;
const at = (time: number, sequence = Number.POSITIVE_INFINITY) => ({ time, sequence });

// Same shape as CT-6-01 (service -> db request, db -> service response) emitted by the backend.
const requestThenResponse = [
  message({ sequence: 1, timestampMs: 1, action: 'SENT', messageId: 'm-req', exchangeId: 'exchange-2', connectionId: 'data', sourceId: 'service', targetId: 'db', direction: 'REQUEST' }),
  message({ sequence: 2, timestampMs: 2, action: 'ARRIVED', messageId: 'm-req', exchangeId: 'exchange-2', connectionId: 'data', sourceId: 'service', targetId: 'db', direction: 'REQUEST' }),
  message({ sequence: 3, timestampMs: 5, action: 'SENT', messageId: 'm-res', exchangeId: 'exchange-2', connectionId: 'data', sourceId: 'db', targetId: 'service', direction: 'RESPONSE' }),
  message({ sequence: 4, timestampMs: 6, action: 'ARRIVED', messageId: 'm-res', exchangeId: 'exchange-2', connectionId: 'data', sourceId: 'db', targetId: 'service', direction: 'RESPONSE' }),
];
const serviceToDb = [{ id: 'data', source: 'service', target: 'db' }];

describe('direção da animação das arestas no playback', () => {
  it('anima a requisição no sentido source → target da aresta', () => {
    const state = edgePlayback(result(requestThenResponse), at(1), serviceToDb).get('data');
    expect(state).toEqual({ direction: 'forward', failure: false, messageIds: ['m-req'] });
  });
  it('anima a resposta no sentido inverso (target → source)', () => {
    const state = edgePlayback(result(requestThenResponse), at(5), serviceToDb).get('data');
    expect(state).toEqual({ direction: 'reverse', failure: false, messageIds: ['m-res'] });
  });
  it('não anima a aresta quando nenhuma mensagem está em voo', () => {
    expect(edgePlayback(result(requestThenResponse), at(3), serviceToDb).size).toBe(0);
    expect(edgePlayback(result(requestThenResponse), at(6), serviceToDb).size).toBe(0);
  });
  it('usa as pontas da aresta, não a posição no canvas: a mesma mensagem inverte numa aresta desenhada ao contrário', () => {
    const drawnFromDb = [{ id: 'data', source: 'db', target: 'service' }];
    expect(edgePlayback(result(requestThenResponse), at(1), drawnFromDb).get('data')?.direction).toBe('reverse');
    expect(edgePlayback(result(requestThenResponse), at(5), drawnFromDb).get('data')?.direction).toBe('forward');
    // Pure function of the trace and the edge ends: no node coordinates are involved at all.
    expect(messageDirection({ source: 'service', target: 'db' }, { sourceId: 'service', targetId: 'db', direction: 'RESPONSE' })).toBe('forward');
  });
  it('mensagem assíncrona segue quem a enviou no evento causal (broker → consumidor)', () => {
    const delivery = [message({ sequence: 1, timestampMs: 3, action: 'SENT', messageId: 'd-1', exchangeId: 'delivery-1', connectionId: 'sub', sourceId: 'broker', targetId: 'worker', direction: 'MESSAGE' })];
    expect(edgePlayback(result(delivery), at(3), [{ id: 'sub', source: 'broker', target: 'worker' }]).get('sub')?.direction).toBe('forward');
    expect(edgePlayback(result(delivery), at(3), [{ id: 'sub', source: 'worker', target: 'broker' }]).get('sub')?.direction).toBe('reverse');
  });
  it('mensagens em sentidos opostos na mesma aresta e instante ficam estáticas (both)', () => {
    const crossing = [
      message({ sequence: 1, timestampMs: 1, action: 'SENT', messageId: 'a', exchangeId: 'exchange-2', connectionId: 'data', sourceId: 'service', targetId: 'db', direction: 'REQUEST' }),
      message({ sequence: 2, timestampMs: 1, action: 'SENT', messageId: 'b', exchangeId: 'exchange-3', connectionId: 'data', sourceId: 'db', targetId: 'service', direction: 'RESPONSE' }),
    ];
    expect(edgePlayback(result(crossing), at(1), serviceToDb).get('data')?.direction).toBe('both');
  });
  it('resposta de operação com falha (DUPLICATE_KEY) não aparece como resposta bem-sucedida', () => {
    const failed = result(requestThenResponse, { dataOutcomes: [{ operationId: 'exchange-2', actionId: 'dup', status: 'FAILED', timestampMs: 5 }] });
    const state = edgePlayback(failed, at(5), serviceToDb).get('data')!;
    expect(state).toMatchObject({ direction: 'reverse', failure: true });
    expect(playbackEdgeAppearance(state).style.stroke).toBe(PLAYBACK_EDGE_COLORS.failure);
    // The request that preceded the failure was legitimate and keeps the normal style.
    expect(edgePlayback(failed, at(1), serviceToDb).get('data')!.failure).toBe(false);
  });
  it('resposta após timeout de processamento é marcada como falha', () => {
    const timedOut = result(requestThenResponse, { dataOutcomes: [{ operationId: 'exchange-2', actionId: 'slow', status: 'TIMED_OUT', timestampMs: 5 }] });
    expect(edgePlayback(timedOut, at(5), serviceToDb).get('data')).toMatchObject({ direction: 'reverse', failure: true });
  });
  it('timeout na fila não inventa resposta: só a requisição existiu', () => {
    const queueTimeout = result(requestThenResponse.slice(0, 2), { dataOutcomes: [{ operationId: 'exchange-2', actionId: 'waiter', status: 'TIMED_OUT', timestampMs: 3 }] });
    expect(edgePlayback(queueTimeout, at(1), serviceToDb).get('data')?.direction).toBe('forward');
    for (const time of [2, 3, 5, 6]) expect(edgePlayback(queueTimeout, at(time), serviceToDb).size).toBe(0);
  });
  it('troca de estado do exchange para TIMED_OUT também marca a resposta como falha', () => {
    const viaState = result([...requestThenResponse, { sequence: 5, timestampMs: 4, type: 'STATE_TRANSITION', entityKind: 'EXCHANGE',
      entityId: 'exchange-2', previousState: 'IN_PROGRESS', newState: 'TIMED_OUT', cause: 'CALL_TIMEOUT' }]);
    expect(edgePlayback(viaState, at(5), serviceToDb).get('data')?.failure).toBe(true);
  });
});

describe('aparência da aresta ativa', () => {
  it('desliga o animated nativo do React Flow e aplica classes de direção', () => {
    expect(playbackEdgeAppearance({ direction: 'forward', failure: false, messageIds: ['m'] }))
      .toEqual({ className: 'playback-edge-active playback-edge--forward', animated: false, style: { stroke: PLAYBACK_EDGE_COLORS.message, strokeWidth: 3 } });
    expect(playbackEdgeAppearance({ direction: 'reverse', failure: true, messageIds: ['m'] }).className)
      .toBe('playback-edge-active playback-edge--reverse playback-edge--failure');
  });
  it('o CSS anima forward e reverse em sentidos opostos do path SVG', () => {
    const compact = styles.replace(/\s+/g, '');
    expect(compact).toContain('@keyframesarchlab-dash-forward{from{stroke-dashoffset:10}to{stroke-dashoffset:0}}');
    expect(compact).toContain('@keyframesarchlab-dash-reverse{from{stroke-dashoffset:0}to{stroke-dashoffset:10}}');
    expect(compact).toContain('.react-flow__edge.playback-edge--forwardpath.react-flow__edge-path{animation:archlab-dash-forward');
    expect(compact).toContain('.react-flow__edge.playback-edge--reversepath.react-flow__edge-path{animation:archlab-dash-reverse');
    expect(compact).toContain('@media(prefers-reduced-motion:reduce)');
  });
});
