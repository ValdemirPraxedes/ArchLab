import type { SimulationResponse } from '../../api/archlabClient';

type InitialState = SimulationResponse['initialState'];
type Trace = SimulationResponse['trace'][number];
export type StateSnapshot = InitialState;

/** Reconstitui somente transições informadas pelo backend; não reproduz regras do motor. */
export function reconstructState(
  initial: InitialState,
  trace: Trace[],
  timestampMs: number,
  sequence = Number.POSITIVE_INFINITY,
): StateSnapshot {
  const snapshot: StateSnapshot = {
    components: { ...initial.components },
    requests: { ...initial.requests },
    exchanges: { ...initial.exchanges },
  };
  const sorted = [...trace].sort((a, b) => a.timestampMs - b.timestampMs || a.sequence - b.sequence);
  for (const entry of sorted) {
    if (entry.timestampMs > timestampMs || (entry.timestampMs === timestampMs && entry.sequence > sequence)) break;
    if (entry.type !== 'STATE_TRANSITION') continue;
    switch (entry.entityKind) {
      case 'COMPONENT':
        snapshot.components[entry.entityId] = entry.newState as typeof snapshot.components[string];
        break;
      case 'REQUEST':
        snapshot.requests[entry.entityId] = entry.newState as typeof snapshot.requests[string];
        break;
      case 'EXCHANGE':
        snapshot.exchanges[entry.entityId] = entry.newState as typeof snapshot.exchanges[string];
        break;
    }
  }
  return snapshot;
}

export function nextTracePoint(trace: Trace[], timestampMs: number, sequence: number) {
  return [...trace].sort((a, b) => a.timestampMs - b.timestampMs || a.sequence - b.sequence)
    .find((event) => event.timestampMs > timestampMs || (event.timestampMs === timestampMs && event.sequence > sequence));
}
export function previousTracePoint(trace: Trace[], timestampMs: number, sequence: number) {
  return [...trace].sort((a, b) => b.timestampMs - a.timestampMs || b.sequence - a.sequence)
    .find((event) => event.timestampMs < timestampMs || (event.timestampMs === timestampMs && event.sequence < sequence));
}
