import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { orderedTrace } from './traceV2';

export type MessageEventV2 = Extract<SimulationResponseV2['trace'][number], { type: 'MESSAGE_EVENT' }>;

export function messageEvents(result: SimulationResponseV2) {
  return orderedTrace(result.trace).filter((event): event is MessageEventV2 => event.type === 'MESSAGE_EVENT');
}

export function exchangeTree(result: SimulationResponseV2) {
  const events = messageEvents(result);
  const byExchange = new Map<string, { id:string; parentId:string | null; events:MessageEventV2[]; children:string[] }>();
  for (const event of events) {
    let item = byExchange.get(event.exchangeId);
    if (!item) {
      item = { id:event.exchangeId, parentId:event.parentExchangeId, events:[], children:[] };
      byExchange.set(event.exchangeId,item);
    }
    item.events.push(event);
  }
  for (const item of byExchange.values()) {
    if (item.parentId && byExchange.has(item.parentId) && item.parentId !== item.id) byExchange.get(item.parentId)!.children.push(item.id);
  }
  return { byExchange, roots:[...byExchange.values()].filter(item => !item.parentId || !byExchange.has(item.parentId) || item.parentId === item.id).map(item => item.id) };
}

export function representationFor(result: SimulationResponseV2, event: MessageEventV2) {
  return result.messageRepresentations.find(item => item.connectionId === event.connectionId)?.integration;
}
