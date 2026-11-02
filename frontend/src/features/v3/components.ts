import { componentDefaultsV3, componentLabelV3 } from './catalog';
import type { ComponentTypeV3 } from './catalog';
import type { ProjectV3 } from './project';
export function addComponentV3(project: ProjectV3, typeId: ComponentTypeV3, position?: { x: number; y: number }): ProjectV3 {
  const a = project.request.architecture; let next = project.nextId;
  const ids = new Set([...a.components, ...a.resources, ...a.endpoints, ...a.operations, ...a.relations, ...a.executionPlans].map(item => item.id));
  while (ids.has('component-' + next)) next++;
  const id = 'component-' + next; const count = a.components.filter(c => c.typeId === typeId).length + 1;
  const component = componentDefaultsV3(id, typeId, componentLabelV3(typeId) + ' ' + count);
  return { ...project, nextId: next + 1, positions: { ...project.positions, [id]: position ?? { x: 80 + a.components.length % 3 * 250, y: 80 + Math.floor(a.components.length / 3) * 150 } }, request: { ...project.request, architecture: { ...a, components: [...a.components, component] } } };
}
/** Never silently leave dangling references after deletion. */
export function removeComponentV3(project: ProjectV3, id: string): ProjectV3 {
  const a = project.request.architecture;
  if ([...a.resources, ...a.endpoints, ...a.operations].some(item => item.ownerId === id) || project.request.scenario.entry.componentId === id) throw new Error('Remova os recursos, endpoints, operações e a entrada que referenciam este componente antes de excluí-lo.');
  const positions = { ...project.positions }; delete positions[id];
  return { ...project, positions, request: { ...project.request, architecture: { ...a, components: a.components.filter(c => c.id !== id) } } };
}
export function moveComponentV3(project: ProjectV3, id: string, position: { x: number; y: number }): ProjectV3 { if (!project.request.architecture.components.some(c => c.id === id)) return project; return { ...project, positions: { ...project.positions, [id]: position } }; }
