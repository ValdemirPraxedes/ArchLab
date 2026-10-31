import type { SimulationResponseV2 } from '../../api/archlabV2Client';

export type TraceV2 = SimulationResponseV2['trace'];
export type TraceCursor = { time: number; sequence: number };
export const orderedTrace = (trace: TraceV2) => [...trace].sort((a,b) => a.timestampMs-b.timestampMs || a.sequence-b.sequence);
export function traceStateAt(result: SimulationResponseV2, cursor: TraceCursor) {
  const state = {
    components: { ...result.initialState.components } as Record<string,string>,
    requests: { ...result.initialState.requests } as Record<string,string>,
    exchanges: { ...result.initialState.exchanges } as Record<string,string>,
  };
  for (const event of orderedTrace(result.trace)) {
    if (event.timestampMs > cursor.time || (event.timestampMs === cursor.time && event.sequence > cursor.sequence)) break;
    if (event.type !== 'STATE_TRANSITION') continue;
    const bucket = event.entityKind === 'COMPONENT' ? state.components : event.entityKind === 'REQUEST' ? state.requests : state.exchanges;
    bucket[event.entityId] = event.newState;
  }
  return state;
}
export function cursorEvents(trace: TraceV2, cursor: TraceCursor) {
  return orderedTrace(trace).filter(e => e.timestampMs < cursor.time || (e.timestampMs === cursor.time && e.sequence <= cursor.sequence));
}
export function nextCursor(trace: TraceV2, cursor: TraceCursor, total: number): TraceCursor {
  const event = orderedTrace(trace).find(e => e.timestampMs > cursor.time || (e.timestampMs === cursor.time && e.sequence > cursor.sequence));
  return event ? {time:event.timestampMs,sequence:event.sequence} : {time:total,sequence:Number.POSITIVE_INFINITY};
}
export function previousCursor(trace: TraceV2, cursor: TraceCursor): TraceCursor {
  const event = orderedTrace(trace).reverse().find(e => e.timestampMs < cursor.time || (e.timestampMs === cursor.time && e.sequence < cursor.sequence));
  return event ? {time:event.timestampMs,sequence:event.sequence} : {time:0,sequence:0};
}
export function inFlightMessages(trace: TraceV2, cursor: TraceCursor) {
  const active = new Map<string, Extract<TraceV2[number], {type:'MESSAGE_EVENT'}>>();
  for (const event of cursorEvents(trace,cursor)) if (event.type === 'MESSAGE_EVENT') {
    if (event.action === 'SENT') active.set(event.messageId,event);
    else active.delete(event.messageId);
  }
  return [...active.values()];
}
