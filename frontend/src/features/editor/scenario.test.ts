import { describe, expect, it } from 'vitest';
import { createArchitectureEdge, createArchitectureNode } from './model';
import { emptyScenario, reconcileScenario, scenarioIssues, selectedPath } from './scenario';

function fixture() {
  const client = createArchitectureNode('CLIENT', { x: 0, y: 0 });
  const otherClient = createArchitectureNode('CLIENT', { x: 0, y: 0 });
  const a = createArchitectureNode('API', { x: 0, y: 0 });
  const b = createArchitectureNode('API', { x: 0, y: 0 });
  const db = createArchitectureNode('DATABASE', { x: 0, y: 0 });
  const nodes = [client, otherClient, a, b, db];
  const input = createArchitectureEdge(client.id, a.id, 'REST', 1);
  const api = createArchitectureEdge(a.id, b.id, 'REST', 2);
  const database = createArchitectureEdge(b.id, db.id, 'TCP', 3);
  return { client, otherClient, a, b, db, nodes, edges: [input, api, database], input, api, database };
}

describe('seleção explícita de cenário v2', () => {
  it('não escolhe automaticamente Client, arestas ou dependências', () => {
    const f = fixture();
    const empty = emptyScenario();
    expect(empty).toEqual({ entryComponentId: '', entryConnectionId: '', apiExecutions: [] });
    expect(selectedPath(empty, f.nodes, f.edges).components.size).toBe(0);
    expect(scenarioIssues(empty, f.nodes, f.edges)).toHaveLength(2);
  });
  it('API sem dependência responde diretamente e não ativa DB', () => {
    const f = fixture();
    const scenario = { entryComponentId: f.client.id, entryConnectionId: f.input.id, apiExecutions: [] };
    expect(scenarioIssues(scenario, f.nodes, f.edges)).toEqual([]);
    expect(selectedPath(scenario, f.nodes, f.edges).components).toEqual(new Set([f.client.id, f.a.id]));
  });
  it('mantém apenas uma dependência por API e segue cadeia selecionada', () => {
    const f = fixture();
    const s = { entryComponentId: f.client.id, entryConnectionId: f.input.id,
      apiExecutions: [{ componentId: f.a.id, dependencyConnectionId: f.api.id }, { componentId: f.b.id, dependencyConnectionId: f.database.id }] };
    expect(selectedPath(s, f.nodes, f.edges).components.size).toBe(4);
    expect(selectedPath(s, f.nodes, f.edges).connections.size).toBe(3);
  });
  it('remove entradas e dependências obsoletas sem substituí-las automaticamente', () => {
    const f = fixture();
    const s = { entryComponentId: f.client.id, entryConnectionId: f.input.id,
      apiExecutions: [{ componentId: f.a.id, dependencyConnectionId: f.api.id }, { componentId: f.b.id, dependencyConnectionId: f.database.id }] };
    expect(reconcileScenario(s, f.nodes, [f.input, f.api]).apiExecutions).toHaveLength(1);
    expect(reconcileScenario(s, f.nodes, [f.api, f.database]).entryConnectionId).toBe('');
    expect(reconcileScenario(s, f.nodes.filter(n => n.id !== f.client.id), f.edges).entryComponentId).toBe('');
    const direct = { ...s, apiExecutions: [] };
    expect(reconcileScenario(direct, f.nodes, f.edges).apiExecutions).toEqual([]);
  });
});
