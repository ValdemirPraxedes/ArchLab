import { describe, expect, it } from 'vitest';
import { createArchitectureEdge, createArchitectureNode } from './model';
import { toArchitectureRequest, validateArchitectureLocally } from './validation';

function scenario() {
  const client = createArchitectureNode('CLIENT', { x: 5, y: 10 });
  const api = createArchitectureNode('API', { x: 50, y: 60 });
  const db = createArchitectureNode('DATABASE', { x: 500, y: 600 });
  api.data.preProcessingMs = 30;
  api.data.postProcessingMs = 20;
  db.data.processingMs = 150;
  return {
    nodes: [client, api, db],
    edges: [createArchitectureEdge(client.id, api.id, 'REST', 20), createArchitectureEdge(api.id, db.id, 'TCP', 10)],
  };
}
describe('validação do editor', () => {
  it('gera contrato sem coordenadas e com tempos explícitos', () => {
    const { nodes, edges } = scenario();
    const result = toArchitectureRequest(nodes, edges);
    expect(result.components).toEqual([
      { id: nodes[0].id, type: 'CLIENT' },
      { id: nodes[1].id, type: 'API', preProcessingMs: 30, postProcessingMs: 20 },
      { id: nodes[2].id, type: 'DATABASE', processingMs: 150 },
    ]);
    expect(result.connections[0]).toEqual({ id: edges[0].id, sourceId: nodes[0].id, targetId: nodes[1].id, protocol: 'REST', latencyMs: 20 });
    expect(JSON.stringify(result)).not.toMatch(/position|\\"x\\"|\\"y\\"/);
    expect(validateArchitectureLocally(nodes, edges)).toEqual([]);
  });
  it('rejeita multiplicidade inclusive componente excedente desconectado', () => {
    const { nodes, edges } = scenario();
    nodes.push(createArchitectureNode('CLIENT', { x: 0, y: 0 }));
    expect(validateArchitectureLocally(nodes, edges).some((v) => v.code === 'INVALID_COMPONENT_COUNT')).toBe(true);
  });
  it('identifica ligações ausentes, protocolo e duração inválidos', () => {
    const { nodes, edges } = scenario();
    edges[0].data!.protocol = 'TCP';
    edges[1].data!.latencyMs = -1;
    nodes[1].data.preProcessingMs = -2;
    const issues = validateArchitectureLocally(nodes, edges);
    expect(issues.filter((v) => v.code === 'INVALID_DURATION')).toHaveLength(2);
    expect(issues.some((v) => v.code === 'INVALID_PROTOCOL')).toBe(true);
    expect(validateArchitectureLocally(nodes, []).filter((v) => v.code === 'MISSING_CONNECTION')).toHaveLength(2);
  });
});
