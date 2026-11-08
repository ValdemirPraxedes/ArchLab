import type { SimulationResponseV1 } from '../../api/archlabV1Client';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { inFlightMessages, traceStateAt, type TraceCursor } from './traceV2';

/**
 * Logical direction of a message relative to the edge it travels on. React Flow always draws an
 * edge path from edge.source to edge.target, so "forward" animates along the path and "reverse"
 * against it. The direction comes from the message semantics in the trace (who sent it to whom),
 * never from where the nodes happen to sit on the canvas.
 */
export type EdgeFlowDirection = 'forward' | 'reverse';
export type EdgePlayback = {
  /** 'both' when messages travel both ways on the same edge at the same instant (no misleading single direction). */
  direction: EdgeFlowDirection | 'both';
  /** A response that belongs to a failed / timed-out / rejected operation: never shown as a successful reply. */
  failure: boolean;
  messageIds: string[];
};
type InFlight = ReturnType<typeof inFlightMessages>[number];
type EdgeEnds = { id: string; source: string; target: string };

export function messageDirection(edge: Omit<EdgeEnds, 'id'>, message: Pick<InFlight, 'sourceId' | 'targetId' | 'direction'>): EdgeFlowDirection {
  if (message.sourceId === edge.source && message.targetId === edge.target) return 'forward';
  if (message.sourceId === edge.target && message.targetId === edge.source) return 'reverse';
  // Endpoints not on this edge (should not happen): fall back to the causal direction of the event.
  return message.direction === 'RESPONSE' ? 'reverse' : 'forward';
}

const UNSUCCESSFUL = new Set(['FAILED', 'TIMED_OUT', 'REJECTED']);

/** Operations (exchange ids) that ended unsuccessfully at or before the cursor. */
export function unsuccessfulOperations(result: SimulationResponseV2, cursor: TraceCursor): Set<string> {
  const canonical = result as unknown as Partial<SimulationResponseV1>;
  const failed = new Set<string>();
  for (const outcome of [...(canonical.dataOutcomes ?? []), ...(canonical.resourceOutcomes ?? [])])
    if (outcome.operationId && (outcome.timestampMs ?? 0) <= cursor.time && UNSUCCESSFUL.has(outcome.status)) failed.add(outcome.operationId);
  for (const [exchangeId, state] of Object.entries(traceStateAt(result, cursor).exchanges))
    if (state === 'FAILED' || state === 'TIMED_OUT') failed.add(exchangeId);
  return failed;
}

/** Animation of every edge that carries at least one message in flight at the cursor. */
export function edgePlayback(result: SimulationResponseV2, cursor: TraceCursor, edges: readonly EdgeEnds[]): Map<string, EdgePlayback> {
  const byId = new Map(edges.map(edge => [edge.id, edge]));
  const failed = unsuccessfulOperations(result, cursor);
  const playback = new Map<string, EdgePlayback>();
  for (const message of inFlightMessages(result.trace, cursor)) {
    const edge = byId.get(message.connectionId);
    if (!edge) continue;
    const direction = messageDirection(edge, message);
    const failure = message.direction === 'RESPONSE' && failed.has(message.exchangeId);
    const current = playback.get(edge.id);
    playback.set(edge.id, current
      ? { direction: current.direction === direction ? direction : 'both', failure: current.failure || failure,
          messageIds: [...current.messageIds, message.messageId] }
      : { direction, failure, messageIds: [message.messageId] });
  }
  return playback;
}

/** CSS classes for an active edge; the dash animation itself lives in style.css. */
export function edgePlaybackClass(state: EdgePlayback): string {
  return ['playback-edge-active', `playback-edge--${state.direction}`, state.failure ? 'playback-edge--failure' : ''].filter(Boolean).join(' ');
}

export const PLAYBACK_EDGE_COLORS = { message: '#329eff', failure: '#d92d20' } as const;

/**
 * React Flow props of an edge carrying messages. React Flow's built-in `animated` is always off here:
 * it can only run source → target, so the direction-aware classes drive the dashes instead.
 */
export function playbackEdgeAppearance(state: EdgePlayback) {
  return {
    className: edgePlaybackClass(state),
    animated: false,
    style: { stroke: state.failure ? PLAYBACK_EDGE_COLORS.failure : PLAYBACK_EDGE_COLORS.message, strokeWidth: 3 },
  };
}
