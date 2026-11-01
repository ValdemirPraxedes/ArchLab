import { describe, expect, it } from 'vitest';
import { allowedProtocols, createArchitectureEdge, createArchitectureNode, validateConnection } from './model';
import { validateGraphV2 } from './graphValidation';

describe('canvas v2 DAG', () => {
  const setup = () => {
    const client = createArchitectureNode('CLIENT', { x: 0, y: 0 }, 'Client 1');
    const apiA = createArchitectureNode('API', { x: 100, y: 0 }, 'API A');
    const apiB = createArchitectureNode('API', { x: 200, y: 0 }, 'API B');
    const db = createArchitectureNode('DATABASE', { x: 300, y: 0 }, 'DB');
    return { client, apiA, apiB, db };
  };
  it('permite Clients e APIs múltiplas e Database opcional/desconectado', () => {
    const { client, apiA, apiB, db } = setup();
    const otherClient = createArchitectureNode('CLIENT', { x: 0, y: 100 });
    const edges = [createArchitectureEdge(client.id, apiA.id, 'REST', 1), createArchitectureEdge(apiA.id, apiB.id, 'SOAP', 2)];
    expect(validateGraphV2([client, otherClient, apiA, apiB], edges)).toEqual([]);
    expect(validateGraphV2([client, otherClient, apiA, apiB, db], edges)).toEqual([]);
  });
  it('permite API→Database, impede direção inversa e Client→Database', () => {
    const { client, apiA, db } = setup();
    expect(allowedProtocols('API', 'API')).toEqual(['REST', 'SOAP']);
    expect(allowedProtocols('API', 'DATABASE')).toEqual(['TCP']);
    expect(validateConnection([client, apiA, db], [], client.id, db.id)).toMatch(/incompatível/);
    expect(validateConnection([client, apiA, db], [], db.id, apiA.id)).toMatch(/incompatível/);
  });
  it('bloqueia ciclo indireto, duplicatas e autochamada', () => {
    const { client, apiA, apiB } = setup();
    const nodes = [client, apiA, apiB];
    const edges = [createArchitectureEdge(client.id, apiA.id, 'REST', 1), createArchitectureEdge(apiA.id, apiB.id, 'SOAP', 2)];
    expect(validateConnection(nodes, edges, apiB.id, apiA.id)).toMatch(/existe/);
    expect(validateConnection(nodes, edges, apiA.id, apiA.id)).toMatch(/diferentes/);
    const c = createArchitectureNode('API', { x: 500, y: 0 });
    const chain = [...edges, createArchitectureEdge(apiB.id, c.id, 'REST', 0)];
    expect(validateConnection([...nodes, c], chain, c.id, apiA.id)).toMatch(/ciclo/);
    expect(validateGraphV2([...nodes, c], [...chain, createArchitectureEdge(c.id, apiA.id, 'REST', 0)]).some(i => i.code === 'CYCLIC_DEPENDENCY')).toBe(true);
  });
  it('não inclui cardinalidade obrigatória na validação do grafo', () => {
    expect(validateGraphV2([], [])).toEqual([]);
  });
});
