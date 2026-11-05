import type { ArchitectureV1, SimulationRequestV1, UnifiedScenarioV41 } from '../../api/archlabV1Client';
import type { ArchitectureEdge, ArchitectureNode } from './model';

export type EntryDraftV41 = { componentId: string; connectionId: string; operation: string };
export type SessionDraftV41 = { connectionId: string; sessionAlias: string; autoCloseAtEnd: boolean };
export const emptyEntryV41 = (): EntryDraftV41 => ({ componentId: '', connectionId: '', operation: '' });

export function operationForEntry(edge: ArchitectureEdge): string {
  const integration = edge.data?.integration;
  if (!integration) return '';
  switch (integration.kind) {
    case 'HTTP_REST': return `${integration.request?.method ?? 'GET'} ${integration.request?.path ?? '/'}`;
    case 'HTTP_GENERIC': return `${integration.method} ${integration.path}`;
    case 'GRAPHQL_HTTP': return integration.operationName;
    case 'GRPC_UNARY': return `${integration.service}/${integration.method}`;
    case 'COAP': return `${integration.method} ${integration.resource}`;
    default: return '';
  }
}

export function toUnifiedRequestV41(architecture: ArchitectureV1, entry: EntryDraftV41,
  sessionSetup: SessionDraftV41[], nodes: ArchitectureNode[], edges: ArchitectureEdge[]): SimulationRequestV1 {
  const source = nodes.find(node => node.id === entry.componentId);
  const selected = edges.find(edge => edge.id === entry.connectionId);
  if (!source || !selected || selected.source !== source.id) throw new Error('Selecione uma conexão de saída do componente inicial.');
  const scenario: UnifiedScenarioV41 = {
    entry: { componentId: source.id, connectionId: selected.id,
      ...(entry.operation.trim() ? {operation: entry.operation.trim()} : {}) },
    sessionSetup,
  };
  return {architecture, scenario};
}
