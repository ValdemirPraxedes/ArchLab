import type { ArchitectureV1, SimulationRequestV1 } from '../../api/archlabV1Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';
import { isProcessorType, isV5ServiceType, isV6DataType } from './model';
import type { ScenarioDraft } from './scenario';
import { allowedIntegrationKinds, defaultIntegration, integrationError } from './integration';

export function toArchitectureV1(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ArchitectureV1 {
  const components = nodes.map(node => {
    const base = {...node.data.importedComponent,id:node.id, displayName:node.data.displayName ?? node.data.label};
    switch (node.data.componentType) {
      case 'API': return {...base,type:'API' as const,preProcessingMs:node.data.preProcessingMs ?? 0,postProcessingMs:node.data.postProcessingMs ?? 0,behaviors:node.data.behaviors ?? []};
      case 'DATABASE': return {...base,type:'DATABASE' as const,processingMs:node.data.processingMs ?? 0};
      case 'BROKER': return {...base,type:'BROKER' as const,destinations:node.data.destinations ?? [],subscriptions:node.data.subscriptions ?? []};
      case 'ETL_PIPELINE': return {...base,type:'ETL_PIPELINE' as const,batchSize:node.data.batchSize ?? 100,
        maxBatches:node.data.maxBatches ?? 1,behaviors:node.data.behaviors ?? []};
      case 'DATA_TRANSFORMER': return {...base,type:'DATA_TRANSFORMER' as const,
        transformCostPerRecordMs:node.data.transformCostPerRecordMs ?? 0,behaviors:node.data.behaviors ?? []};
      default:
        if (isV6DataType(node.data.componentType)) return {...base,type:node.data.componentType,
          dataProperties:node.data.dataProperties!};
        if (isV5ServiceType(node.data.componentType)) return {
          ...base,type:node.data.componentType,
          ...(isProcessorType(node.data.componentType) ? {behaviors:node.data.behaviors ?? [],
            ...(node.data.preProcessingMs!==undefined?{preProcessingMs:node.data.preProcessingMs}:{}),
            ...(node.data.postProcessingMs!==undefined?{postProcessingMs:node.data.postProcessingMs}:{})} : {}),
          ...(node.data.componentType==='API_GATEWAY' && node.data.routes!==undefined ? {routes:node.data.routes} : {}),
          ...(node.data.componentType==='SCHEDULER' && node.data.schedules!==undefined ? {schedules:node.data.schedules} : {}),
          ...(node.data.componentType==='RULES_ENGINE' ? {
            ...(node.data.rules!==undefined?{rules:node.data.rules}:{}),
            ...(node.data.defaultDecision!==undefined?{defaultDecision:node.data.defaultDecision}:{})} : {}),
          ...(node.data.componentType==='CACHE' && node.data.defaultTtlMs!==undefined ? {defaultTtlMs:node.data.defaultTtlMs} : {}),
        };
        return {...base,type:node.data.componentType};
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
    if (override && !['API','SERVICE'].includes(target.data.componentType)) throw new Error('Override permitido somente para API ou SERVICE de destino.');
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
