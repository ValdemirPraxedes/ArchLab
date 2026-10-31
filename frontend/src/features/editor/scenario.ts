import type { ScenarioV2 } from '../../api/archlabV2Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';

export type ScenarioDraft = ScenarioV2;
export const emptyScenario = (): ScenarioDraft => ({ entryComponentId: '', entryConnectionId: '', apiExecutions: [] });

export function reconcileScenario(scenario: ScenarioDraft, nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ScenarioDraft {
  const clients = new Set(nodes.filter(n => n.data.componentType === 'CLIENT').map(n => n.id));
  const apis = new Set(nodes.filter(n => n.data.componentType === 'API').map(n => n.id));
  const entryComponentId = clients.has(scenario.entryComponentId) ? scenario.entryComponentId : '';
  const entryEdge = edges.find(e => e.id === scenario.entryConnectionId && e.source === entryComponentId && apis.has(e.target));
  const entryConnectionId = entryEdge?.id ?? '';
  const apiExecutions = scenario.apiExecutions.filter(e =>
    apis.has(e.componentId) && edges.some(edge =>
      edge.id === e.dependencyConnectionId && edge.source === e.componentId &&
      nodes.some(n => n.id === edge.target && (n.data.componentType === 'API' || n.data.componentType === 'DATABASE'))));
  const reachable = new Set<string>();
  let current = entryEdge?.target;
  while (current && !reachable.has(current)) {
    reachable.add(current);
    const dependency = apiExecutions.find(e => e.componentId === current);
    current = dependency ? edges.find(e => e.id === dependency.dependencyConnectionId)?.target : undefined;
  }
  return { entryComponentId, entryConnectionId, apiExecutions: apiExecutions.filter(e => reachable.has(e.componentId)) };
}

/** Selection is explicit; nothing in the graph automatically becomes an execution. */
export function selectedPath(scenario: ScenarioDraft, nodes: ArchitectureNode[], edges: ArchitectureEdge[]) {
  const valid = reconcileScenario(scenario, nodes, edges);
  const components = new Set<string>();
  const connections = new Set<string>();
  if (!valid.entryConnectionId) return { components, connections };
  components.add(valid.entryComponentId);
  let edge = edges.find(e => e.id === valid.entryConnectionId);
  const seen = new Set<string>();
  while (edge && !seen.has(edge.id)) {
    seen.add(edge.id); connections.add(edge.id); components.add(edge.target);
    const next = valid.apiExecutions.find(e => e.componentId === edge?.target);
    edge = next ? edges.find(e => e.id === next.dependencyConnectionId && e.source === next.componentId) : undefined;
  }
  return { components, connections };
}

export function scenarioIssues(scenario: ScenarioDraft, nodes: ArchitectureNode[], edges: ArchitectureEdge[]): string[] {
  const issues: string[] = [];
  const reconciled = reconcileScenario(scenario, nodes, edges);
  if (!reconciled.entryComponentId) issues.push('Selecione um Client inicial.');
  if (!reconciled.entryConnectionId) issues.push('Selecione uma conexão Client → API.');
  if (reconciled.apiExecutions.length !== scenario.apiExecutions.length) issues.push('Há dependências inválidas ou removidas.');
  const chosen = selectedPath(reconciled, nodes, edges);
  for (const e of reconciled.apiExecutions) if (!chosen.components.has(e.componentId)) issues.push('Uma API inativa possui dependência configurada.');
  return issues;
}
