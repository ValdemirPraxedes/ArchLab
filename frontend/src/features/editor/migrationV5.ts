import type { ArchitectureV1 } from '../../api/archlabV1Client';
import { COMPONENT_LABELS, isComponentType } from './model';
import type { ArchitectureNode, ArchitectureEdge, ComponentType, Protocol } from './model';

export type MigrationResultV5 = {architecture:ArchitectureV1; nodes:ArchitectureNode[]; edges:ArchitectureEdge[]; convertedIds:string[]};
const protocolForKind = (kind:string):Protocol => kind==='HTTP_REST'?'REST':kind==='SOAP_HTTP'?'SOAP':kind==='DATABASE_QUERY'?'TCP':kind as Protocol;

/** Import is copy-based. The caller applies the result only after backend validation succeeds. */
export function prepareArchitectureImportV5(input:unknown):MigrationResultV5 {
  if (!input || typeof input!=='object' || !('components' in input) || !('connections' in input))
    throw new Error('Documento deve conter components e connections.');
  const source=input as ArchitectureV1;
  if (!Array.isArray(source.components) || !Array.isArray(source.connections))
    throw new Error('components e connections devem ser listas.');
  const architecture=structuredClone(source);
  const convertedIds:string[]=[];
  architecture.components=architecture.components.map((component,index) => {
    if (!component || typeof component!=='object' || typeof component.id!=='string' ||
        typeof component.type!=='string' || !isComponentType(component.type))
      throw new Error(`components[${index}]: tipo ou ID inválido.`);
    if (component.type==='API') {
      convertedIds.push(component.id);
      return {...component,type:'SERVICE'} as ArchitectureV1['components'][number];
    }
    return component;
  });
  const ids=new Set(architecture.components.map(component=>component.id));
  const nodes:ArchitectureNode[]=architecture.components.map((component,index) => {
    const type=component.type as ComponentType;
    return {id:component.id,type:'architecture',position:{x:120+(index%4)*230,y:110+Math.floor(index/4)*140},data:{
      componentType:type,label:component.displayName || COMPONENT_LABELS[type],displayName:component.displayName,
      importedComponent:component,
      ...('preProcessingMs' in component ? {preProcessingMs:component.preProcessingMs} : {}),
      ...('postProcessingMs' in component ? {postProcessingMs:component.postProcessingMs} : {}),
      ...('processingMs' in component ? {processingMs:component.processingMs} : {}),
      ...('behaviors' in component ? {behaviors:component.behaviors} : {}),
      ...('destinations' in component ? {destinations:component.destinations} : {}),
      ...('subscriptions' in component ? {subscriptions:component.subscriptions} : {}),
      ...('routes' in component ? {routes:component.routes} : {}),
      ...('schedules' in component ? {schedules:component.schedules} : {}),
      ...('rules' in component ? {rules:component.rules} : {}),
      ...('defaultDecision' in component ? {defaultDecision:component.defaultDecision} : {}),
      ...('defaultTtlMs' in component ? {defaultTtlMs:component.defaultTtlMs} : {}),
    }};
  });
  const edges:ArchitectureEdge[]=architecture.connections.map((connection,index) => {
    if (!connection || !ids.has(connection.sourceId) || !ids.has(connection.targetId) || !connection.integration)
      throw new Error(`connections[${index}]: referência inválida.`);
    const protocol=protocolForKind(connection.integration.kind);
    return {id:connection.id,source:connection.sourceId,target:connection.targetId,type:'smoothstep',label:protocol,
      data:{protocol,latencyMs:connection.latencyMs,integration:connection.integration,
        apiProcessingOverride:connection.apiProcessingOverride}};
  });
  return {architecture,nodes,edges,convertedIds};
}
