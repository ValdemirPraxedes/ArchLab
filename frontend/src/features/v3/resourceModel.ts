import type { ProjectV3 } from './project';
import type { InitialStateV3, ResourceV3 } from './types';
import { RESOURCE_RULES } from './propertyModel';
export function addResourceV3(project: ProjectV3, ownerId: string, typeId: string): ProjectV3 {
  const a = project.request.architecture, owner = a.components.find(c => c.id === ownerId), rule = RESOURCE_RULES[typeId];
  if (!owner || !rule?.owners.includes(owner.typeId)) throw new Error('Recurso incompatível com o componente.');
  let next = project.nextId; const ids = new Set([...a.components, ...a.resources, ...a.endpoints, ...a.operations, ...a.relations, ...a.executionPlans].map(x => x.id)); while (ids.has('resource-' + next)) next++;
  const resource: ResourceV3 = { id: 'resource-' + next, ownerId, typeId, properties: typeId === 'PROCESSING_POOL' ? { capacity: { kind: 'INTEGER', value: 1 } } : typeId === 'MESSAGE_QUEUE' ? { capacity: { kind: 'INTEGER', value: 10 }, consumerConcurrency: { kind: 'INTEGER', value: 1 } } : typeId === 'TOPIC' ? { queueIds: { kind: 'LIST', values: [] } } : {} };
  return { ...project, nextId: next + 1, request: { ...project.request, architecture: { ...a, resources: [...a.resources, resource] } } };
}
export function initialKeyV3(state: InitialStateV3): string { return state.kind + ':' + (state.kind === 'COMPONENT' ? state.componentId : state.kind === 'RESOURCE' ? state.resourceId : state.kind === 'POLICY' ? state.policyInstanceId : state.queueId); }
export function setInitialV3(project: ProjectV3, state: InitialStateV3 | null, kind: InitialStateV3['kind'], id: string): ProjectV3 { const key = kind + ':' + id; const initialStates = project.request.scenario.initialStates.filter(x => initialKeyV3(x) !== key); if (state) initialStates.push(structuredClone(state)); return { ...project, request: { ...project.request, scenario: { ...project.request.scenario, initialStates } } }; }
export function removeResourceV3(project: ProjectV3, id: string): ProjectV3 {
  const { architecture: a, scenario } = project.request;
  const references = (v: unknown): boolean => { if (Array.isArray(v)) return v.some(references); if (!v || typeof v !== 'object') return false; return Object.entries(v).some(([key,value]) => ['targetId','targetResourceId','resourceProfileId','policyRef'].includes(key) && value === id || references(value)); };
  if (references(a.resources.filter(r => r.id !== id)) || references(a.executionPlans) || scenario.initialStates.some(s => initialKeyV3(s).endsWith(':' + id))) throw new Error('Remova as referências e o estado inicial deste recurso antes de excluí-lo.');
  return { ...project, request: { ...project.request, architecture: { ...a, resources: a.resources.filter(r => r.id !== id) } } };
}
