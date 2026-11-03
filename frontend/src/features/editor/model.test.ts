import { describe, expect, it, vi } from 'vitest';
import { allowedProtocols, COMPONENT_LABELS, createArchitectureEdge, createArchitectureNode, isComponentType, isVisualClientType, removeComponent, validDuration, validateConnection, validDisplayName, nextDisplayName, VISUAL_CLIENT_TYPES } from './model';

describe('modelo do editor', () => {
  it('aceita somente tipos conhecidos', () => {
    expect(isComponentType('CLIENT')).toBe(true);
    expect(isComponentType('API')).toBe(true);
    expect(isComponentType('DATABASE')).toBe(true);
    expect(isComponentType('CACHE')).toBe(false);
  });
  it.each(VISUAL_CLIENT_TYPES)('cria instâncias independentes de %s com nome, tipo e posição', (type) => {
    expect(isComponentType(type)).toBe(true);
    expect(isVisualClientType(type)).toBe(true);
    const first = createArchitectureNode(type, { x: 10, y: 20 }, nextDisplayName(type, []));
    const second = createArchitectureNode(type, { x: 30, y: 40 }, nextDisplayName(type, [first]));
    expect(first.id).not.toBe(second.id);
    expect(first.data).toMatchObject({ componentType: type, label: `${COMPONENT_LABELS[type]} 1`, displayName: `${COMPONENT_LABELS[type]} 1` });
    expect(second.data.displayName).toBe(`${COMPONENT_LABELS[type]} 2`);
    expect(second.position).toEqual({ x: 30, y: 40 });
  });
  it('cria instâncias distintas com posição e tempos iniciais', () => {
    const uuid = vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222');
    try {
      const a = createArchitectureNode('CLIENT', { x: 10, y: 20 });
      const b = createArchitectureNode('API', { x: 30, y: 40 });
      expect(a.id).not.toBe(b.id);
      expect(a.position).toEqual({ x: 10, y: 20 });
      expect(b.data.preProcessingMs).toBe(0);
      expect(b.data.postProcessingMs).toBe(0);
    } finally { uuid.mockRestore(); }
  });
  it('limita protocolos e pares permitidos', () => {
    expect(allowedProtocols('CLIENT', 'API')).toEqual(['REST', 'SOAP']);
    expect(allowedProtocols('API', 'DATABASE')).toEqual(['TCP']);
    expect(allowedProtocols('DATABASE', 'API')).toEqual([]);
  });
  it.each(VISUAL_CLIENT_TYPES)('permite %s → API e bloqueia pares incompatíveis', (type) => {
    const visual = createArchitectureNode(type, { x: 0, y: 0 });
    const api = createArchitectureNode('API', { x: 1, y: 1 });
    const db = createArchitectureNode('DATABASE', { x: 2, y: 2 });
    expect(allowedProtocols(type, 'API')).toEqual(['REST', 'SOAP']);
    expect(validateConnection([visual, api, db], [], visual.id, api.id)).toBeNull();
    expect(validateConnection([visual, api, db], [], visual.id, db.id)).toMatch(/incompatível/);
    expect(validateConnection([visual, api, db], [], api.id, visual.id)).toMatch(/incompatível/);
  });
  it('rejeita duração negativa, fracionária ou não finita', () => {
    expect(validDuration(0)).toBe(true);
    for (const value of [-1, 2.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) expect(validDuration(value)).toBe(false);
  });
  it('impede arestas incompatíveis e repetidas no par não ordenado', () => {
    const client = createArchitectureNode('CLIENT', { x: 0, y: 0 });
    const api = createArchitectureNode('API', { x: 0, y: 0 });
    const db = createArchitectureNode('DATABASE', { x: 0, y: 0 });
    const nodes = [client, api, db];
    expect(validateConnection(nodes, [], client.id, api.id)).toBeNull();
    expect(validateConnection(nodes, [], client.id, db.id)).toMatch(/incompatível/);
    expect(validateConnection(nodes, [], api.id, api.id)).toMatch(/diferentes/);
    const edge = createArchitectureEdge(client.id, api.id, 'REST', 20);
    expect(validateConnection(nodes, [edge], client.id, api.id)).toMatch(/existe/);
    expect(validateConnection(nodes, [edge], api.id, client.id)).not.toBeNull();
  });
  it('remover componente remove somente conexões incidentes', () => {
    const client = createArchitectureNode('CLIENT', { x: 0, y: 0 });
    const api = createArchitectureNode('API', { x: 0, y: 0 });
    const db = createArchitectureNode('DATABASE', { x: 0, y: 0 });
    const first = createArchitectureEdge(client.id, api.id, 'SOAP', 2);
    const second = createArchitectureEdge(api.id, db.id, 'TCP', 4);
    const result = removeComponent([client, api, db], [first, second], client.id);
    expect(result.nodes).toHaveLength(2);
    expect(result.edges).toEqual([second]);
  });
  it('valida displayName em code points e gera nomes padrão sem colisão', () => {
    expect(validDisplayName(' Nome ')).toBe(true);
    expect(validDisplayName('  ')).toBe(false);
    expect(validDisplayName('a'.repeat(81))).toBe(false);
    expect(validDisplayName('😀'.repeat(80))).toBe(true);
    const first = createArchitectureNode('API', { x: 0, y: 0 }, 'API 1');
    expect(nextDisplayName('API', [first])).toBe('API 2');
    const duplicate = createArchitectureNode('API', { x: 1, y: 1 }, 'API 1');
    expect(first.id).not.toBe(duplicate.id);
  });

});
