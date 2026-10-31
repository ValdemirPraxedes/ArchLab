import type { Edge, Node } from '@xyflow/react';
import type { IntegrationV2 } from '../../api/archlabV2Client';

export const COMPONENT_TYPES = ['CLIENT', 'API', 'DATABASE'] as const;
export type ComponentType = (typeof COMPONENT_TYPES)[number];
export type Protocol = 'REST' | 'SOAP' | 'TCP';
export type ArchitectureNode = Node<{
  componentType: ComponentType;
  label: string;
  displayName?: string;
  stateLabel?: 'IDLE' | 'PROCESSING' | 'WAITING' | 'COMPLETED';
  preProcessingMs?: number;
  postProcessingMs?: number;
  processingMs?: number;
}>;
export type ArchitectureEdge = Edge<{ protocol: Protocol; latencyMs: number; integration?: IntegrationV2 }>;
export const COMPONENT_LABELS: Record<ComponentType, string> = { CLIENT: 'Client', API: 'API', DATABASE: 'Database' };

export function isComponentType(value: string): value is ComponentType {
  return COMPONENT_TYPES.some((type) => type === value);
}
export function validDisplayName(name: string): boolean { return name.trim().length > 0 && Array.from(name.trim()).length <= 80; }
export function nextDisplayName(type: ComponentType, nodes: ArchitectureNode[]): string {
  const names = new Set(nodes.map((n) => n.data.displayName ?? n.data.label));
  let number = 1;
  while (names.has(`${COMPONENT_LABELS[type]} ${number}`)) number++;
  return `${COMPONENT_LABELS[type]} ${number}`;
}
export function createArchitectureNode(type: ComponentType, position: { x: number; y: number }, displayName = COMPONENT_LABELS[type]): ArchitectureNode {
  return {
    id: crypto.randomUUID(), type: 'architecture', position,
    data: {
      componentType: type, label: displayName, displayName,
      ...(type === 'API' ? { preProcessingMs: 0, postProcessingMs: 0 } : {}),
      ...(type === 'DATABASE' ? { processingMs: 0 } : {}),
    },
  };
}
export function validDuration(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 0;
}
export function allowedProtocols(source: ComponentType, target: ComponentType): Protocol[] {
  if (source === 'CLIENT' && target === 'API') return ['REST', 'SOAP'];
  if (source === 'API' && target === 'API') return ['REST', 'SOAP'];
  if (source === 'API' && target === 'DATABASE') return ['TCP'];
  return [];
}
export function validateConnection(nodes: ArchitectureNode[], edges: ArchitectureEdge[], sourceId: string, targetId: string): string | null {
  if (sourceId === targetId) return 'Origem e destino devem ser diferentes.';
  const source = nodes.find((node) => node.id === sourceId);
  const target = nodes.find((node) => node.id === targetId);
  if (!source || !target) return 'Selecione componentes existentes.';
  if (!allowedProtocols(source.data.componentType, target.data.componentType).length) return 'Conexão incompatível: use Client → API ou API → Database.';
  if (edges.some((edge) => (edge.source === sourceId && edge.target === targetId) || (edge.source === targetId && edge.target === sourceId))) {
    return 'Já existe uma conexão entre esses componentes.';
  }
  if (wouldCreateCycle(edges, sourceId, targetId)) return 'Esta conexão criaria um ciclo no grafo.';
  return null;
}
export function wouldCreateCycle(edges: ArchitectureEdge[], sourceId: string, targetId: string): boolean {
  const visited = new Set<string>();
  const pending = [targetId];
  while (pending.length) {
    const current = pending.pop()!;
    if (current === sourceId) return true;
    if (visited.has(current)) continue;
    visited.add(current);
    for (const edge of edges) if (edge.source === current) pending.push(edge.target);
  }
  return false;
}
export function createArchitectureEdge(sourceId: string, targetId: string, protocol: Protocol, latencyMs: number): ArchitectureEdge {
  return {
    id: crypto.randomUUID(), source: sourceId, target: targetId,
    type: 'smoothstep', label: protocol, animated: false,
    data: { protocol, latencyMs },
  };
}
export function removeComponent(nodes: ArchitectureNode[], edges: ArchitectureEdge[], id: string) {
  return { nodes: nodes.filter((node) => node.id !== id), edges: edges.filter((edge) => edge.source !== id && edge.target !== id) };
}
