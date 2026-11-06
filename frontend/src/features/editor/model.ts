import type { Edge, Node } from '@xyflow/react';
import type { IntegrationV1, BrokerDestinationV1, BrokerSubscriptionV1 } from '../../api/archlabV1Client';
import type { BehaviorV41 } from '../../api/archlabV1Client';
import type { components } from '../../api/generated/schema';
import type { ArchitectureV1 } from '../../api/archlabV1Client';
import type { ApiProcessingOverrideV2 } from '../../api/archlabV2Client';

export const VISUAL_CLIENT_TYPES = ['WEB_BROWSER', 'MOBILE_APP', 'DESKTOP_APP', 'CLI', 'BOT', 'EMBEDDED_SYSTEM'] as const;
export const V5_SERVICE_TYPES = ['SERVICE','API_GATEWAY','BFF','WORKER','SCHEDULER','AUTHENTICATION_SERVICE','WEBHOOK_RECEIVER','ORCHESTRATOR_SERVICE','INTEGRATION_BUS','CACHE','OBJECT_STORAGE','NOTIFICATION_SERVICE','SEARCH_ENGINE','SERVICE_REGISTRY','RULES_ENGINE'] as const;
export const COMPONENT_TYPES = ['CLIENT', ...VISUAL_CLIENT_TYPES, 'API', 'DATABASE', 'BROKER', ...V5_SERVICE_TYPES] as const;
export type ComponentType = (typeof COMPONENT_TYPES)[number];
export type VisualClientType = (typeof VISUAL_CLIENT_TYPES)[number];
export type Protocol = 'REST' | 'SOAP' | 'TCP' | 'HTTP_GENERIC' | 'GRAPHQL_HTTP' | 'GRPC_UNARY' | 'COAP' | 'WEBSOCKET' | 'QUEUE' | 'TOPIC' | 'MQTT';
export type ArchitectureNode = Node<{
  componentType: ComponentType;
  label: string;
  displayName?: string;
  stateLabel?: 'IDLE' | 'PROCESSING' | 'WAITING' | 'COMPLETED';
  preProcessingMs?: number;
  postProcessingMs?: number;
  processingMs?: number;
  destinations?: BrokerDestinationV1[];
  subscriptions?: BrokerSubscriptionV1[];
  behaviors?: BehaviorV41[];
  routes?: components['schemas']['ServiceRouteV5'][];
  schedules?: components['schemas']['VirtualScheduleV5'][];
  rules?: components['schemas']['DecisionRuleV5'][];
  defaultDecision?: 'ALLOW' | 'DENY' | 'REVIEW';
  defaultTtlMs?: number;
  importedComponent?: ArchitectureV1['components'][number];
}>;
export type ArchitectureEdge = Edge<{ protocol: Protocol; latencyMs: number; integration?: IntegrationV1; apiProcessingOverride?: ApiProcessingOverrideV2 }>;
export const COMPONENT_LABELS: Record<ComponentType, string> = {
  CLIENT: 'Client', WEB_BROWSER: 'Web Browser', MOBILE_APP: 'Mobile App', DESKTOP_APP: 'Desktop App',
  CLI: 'CLI', BOT: 'Bot', EMBEDDED_SYSTEM: 'Embedded System', API: 'API', DATABASE: 'Database', BROKER: 'Broker',
  SERVICE:'Service', API_GATEWAY:'API Gateway', BFF:'BFF', WORKER:'Worker', SCHEDULER:'Scheduler',
  AUTHENTICATION_SERVICE:'Authentication Service', WEBHOOK_RECEIVER:'Webhook Receiver', ORCHESTRATOR_SERVICE:'Orchestrator Service',
  INTEGRATION_BUS:'Integration Bus / ESB', CACHE:'Cache', OBJECT_STORAGE:'Object Storage', NOTIFICATION_SERVICE:'Notification Service',
  SEARCH_ENGINE:'Search Engine', SERVICE_REGISTRY:'Service Registry', RULES_ENGINE:'Rules Engine',
};
export const isV5ServiceType = (type: ComponentType): type is typeof V5_SERVICE_TYPES[number] =>
  V5_SERVICE_TYPES.some(value => value === type);
export const isProcessorType = (type: ComponentType): boolean =>
  ['API','SERVICE','API_GATEWAY','BFF','WORKER','SCHEDULER','AUTHENTICATION_SERVICE','WEBHOOK_RECEIVER','ORCHESTRATOR_SERVICE','INTEGRATION_BUS','NOTIFICATION_SERVICE'].includes(type);

export function isVisualClientType(type: ComponentType): type is VisualClientType {
  return VISUAL_CLIENT_TYPES.some((clientType) => clientType === type);
}
export function isClientType(type: ComponentType): type is VisualClientType | 'CLIENT' {
  return type === 'CLIENT' || isVisualClientType(type);
}

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
      ...(type === 'API' ? { preProcessingMs: 0, postProcessingMs: 0, behaviors: [] } : {}),
      ...(isV5ServiceType(type) && isProcessorType(type) ? {preProcessingMs:0,postProcessingMs:0,behaviors:[]} : {}),
      ...(type === 'API_GATEWAY' ? {routes:[]} : {}),
      ...(type === 'SCHEDULER' ? {schedules:[]} : {}),
      ...(type === 'RULES_ENGINE' ? {rules:[],defaultDecision:'DENY' as const} : {}),
      ...(type === 'CACHE' ? {defaultTtlMs:0} : {}),
      ...(type === 'DATABASE' ? { processingMs: 0 } : {}),
      ...(type === 'BROKER' ? { destinations: [], subscriptions: [] } : {}),
    },
  };
}
export function validDuration(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 0;
}
export function allowedProtocols(source: ComponentType, target: ComponentType): Protocol[] {
  const initiates = isClientType(source) || ['API','SERVICE','API_GATEWAY','BFF','WORKER','SCHEDULER','ORCHESTRATOR_SERVICE','WEBHOOK_RECEIVER','INTEGRATION_BUS','AUTHENTICATION_SERVICE'].includes(source);
  const receives = ['API','SERVICE','API_GATEWAY','BFF','SCHEDULER','AUTHENTICATION_SERVICE','WEBHOOK_RECEIVER','ORCHESTRATOR_SERVICE','INTEGRATION_BUS','NOTIFICATION_SERVICE','CACHE','OBJECT_STORAGE','SEARCH_ENGINE','SERVICE_REGISTRY','RULES_ENGINE'].includes(target);
  if (initiates && receives && (!isClientType(source) || isProcessorType(target)))
    return (['API','SERVICE'].includes(source) && ['API','SERVICE'].includes(target)) || (isClientType(source) && ['API','SERVICE'].includes(target))
      ? ['REST','SOAP','HTTP_GENERIC','GRAPHQL_HTTP','GRPC_UNARY','COAP','WEBSOCKET']
      : ['REST','SOAP','HTTP_GENERIC','GRAPHQL_HTTP','GRPC_UNARY','COAP'];
  if (initiates && !isClientType(source) && target === 'DATABASE') return ['TCP'];
  if ((isClientType(source) || ['API','SERVICE','WORKER','SCHEDULER','ORCHESTRATOR_SERVICE','WEBHOOK_RECEIVER','INTEGRATION_BUS','NOTIFICATION_SERVICE'].includes(source)) && target === 'BROKER') return ['QUEUE','TOPIC','MQTT'];
  if (source === 'BROKER' && ['API','SERVICE','WORKER','NOTIFICATION_SERVICE','ORCHESTRATOR_SERVICE','INTEGRATION_BUS'].includes(target)) return ['QUEUE','TOPIC','MQTT'];
  return [];
}
export function validateConnection(nodes: ArchitectureNode[], edges: ArchitectureEdge[], sourceId: string, targetId: string): string | null {
  if (sourceId === targetId) return 'Origem e destino devem ser diferentes.';
  const source = nodes.find((node) => node.id === sourceId);
  const target = nodes.find((node) => node.id === targetId);
  if (!source || !target) return 'Selecione componentes existentes.';
  if (!allowedProtocols(source.data.componentType, target.data.componentType).length) return 'Conexão incompatível com a matriz v0.4.';
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
