import type { ArchitectureV1, SimulationRequestV1 } from '../../api/archlabV1Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';
import type { ScenarioDraft } from './scenario';
import { allowedIntegrationKinds, defaultIntegration, integrationError } from './integration';

export function toArchitectureV1(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ArchitectureV1 {
  const components = nodes.map(node => {
    const base = {id:node.id, displayName:node.data.displayName ?? node.data.label};
    switch (node.data.componentType) {
      case 'API': return {...base,type:'API' as const,preProcessingMs:node.data.preProcessingMs ?? 0,postProcessingMs:node.data.postProcessingMs ?? 0,behaviors:node.data.behaviors ?? []};
      case 'DATABASE': return {...base,type:'DATABASE' as const,processingMs:node.data.processingMs ?? 0};
      case 'BROKER': return {...base,type:'BROKER' as const,destinations:node.data.destinations ?? [],subscriptions:node.data.subscriptions ?? []};
      default: return {...base,type:node.data.componentType};
    }
  }) as ArchitectureV1['components'];
  const connections = edges.map(edge => {
    const source = nodes.find(node => node.id === edge.source);
    const target = nodes.find(node => node.id === edge.target);
    if (!source || !target) throw new Error('Conexão referencia componente inexistente.');
    const kind = allowedIntegrationKinds(source.data.componentType,target.data.componentType)[0];
    if (!kind) throw new Error('Conexão incompatível.');
    const integration = edge.data?.integration ?? defaultIntegration(kind);
    const error = integrationError(integration,source.data.componentType,target.data.componentType);
    if (error) throw new Error(error);
    const override = edge.data?.apiProcessingOverride;
    if (override && target.data.componentType !== 'API') throw new Error('Override permitido somente para API de destino.');
    return {id:edge.id,sourceId:edge.source,targetId:edge.target,latencyMs:edge.data?.latencyMs ?? 0,
      integration,...(override ? {apiProcessingOverride:override} : {})};
  });
  return {components,connections};
}

export function toRequestV1(architecture: ArchitectureV1, scenario: ScenarioDraft): SimulationRequestV1 {
  return {architecture, scenario: {...scenario, kind: 'REQUEST_RESPONSE'}} as SimulationRequestV1;
}

export type AsyncScenarioKind = 'WEBSOCKET_SESSION' | 'MESSAGE_PUBLISH';
export type AsyncScenarioDraft = {entryComponentId:string;entryConnectionId:string;destinationName:string;publishCount:number};
export const emptyAsyncScenario = (): AsyncScenarioDraft => ({entryComponentId:'',entryConnectionId:'',destinationName:'',publishCount:1});
export function toAsyncRequestV1(architecture: ArchitectureV1, kind: AsyncScenarioKind, draft: AsyncScenarioDraft): SimulationRequestV1 {
  const base = {kind,entryComponentId:draft.entryComponentId,entryConnectionId:draft.entryConnectionId};
  return {architecture,scenario:kind === 'WEBSOCKET_SESSION' ? base : {...base,destinationName:draft.destinationName,publishCount:draft.publishCount}} as SimulationRequestV1;
}
