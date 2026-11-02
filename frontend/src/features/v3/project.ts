import type { RequestV3 } from './types';
export interface ProjectV3 { request: RequestV3; positions: Record<string, { x: number; y: number }>; nextId: number }
/** Draft IDs may be blank until the user configures the principal; backend validates admission. */
export function newProjectV3(): ProjectV3 { return { request: { architecture: { id: 'architecture-v3', schemaVersion: '3', components: [], resources: [], endpoints: [], operations: [], relations: [], executionPlans: [] }, scenario: { id: 'scenario-v3', entry: { componentId: '', operationId: '' }, initialStates: [], parameters: {} }, options: { seed: 7, completionMode: 'DRAIN_ALL' } }, positions: {}, nextId: 1 }; }
export type ProjectAction = { type: 'replace'; project: ProjectV3 } | { type: 'rename'; name: string };
export function projectReducer(project: ProjectV3, action: ProjectAction): ProjectV3 {
  if (action.type === 'replace') return structuredClone(action.project);
  return { ...project, request: { ...project.request, architecture: { ...project.request.architecture, id: action.name } } };
}
/** HTTP always receives the contract alone, never coordinates or UI selection. */
export function toRequestV3(project: ProjectV3): RequestV3 { return structuredClone(project.request); }
