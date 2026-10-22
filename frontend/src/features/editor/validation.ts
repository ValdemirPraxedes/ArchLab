import type { ArchitectureRequest } from '../../api/archlabClient';
import type { ArchitectureEdge, ArchitectureNode, ComponentType } from './model';
import { allowedProtocols, validDuration } from './model';

export type ValidationIssue = { code: string; path: string; message: string };

export function toArchitectureRequest(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ArchitectureRequest {
  return {
    components: nodes.map((node) => {
      const { id, data } = node;
      if (data.componentType === 'API') return { id, type: 'API' as const, preProcessingMs: data.preProcessingMs ?? 0, postProcessingMs: data.postProcessingMs ?? 0 };
      if (data.componentType === 'DATABASE') return { id, type: 'DATABASE' as const, processingMs: data.processingMs ?? 0 };
      return { id, type: 'CLIENT' as const };
    }),
    connections: edges.map((edge) => ({
      id: edge.id, sourceId: edge.source, targetId: edge.target,
      protocol: edge.data!.protocol, latencyMs: edge.data!.latencyMs,
    })),
  };
}

export function validateArchitectureLocally(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const issue = (code: string, path: string, message: string) => issues.push({ code, path, message });
  for (const type of ['CLIENT', 'API', 'DATABASE'] as ComponentType[]) {
    const count = nodes.filter((node) => node.data.componentType === type).length;
    if (count !== 1) issue(count === 0 ? 'MISSING_COMPONENT' : 'INVALID_COMPONENT_COUNT', 'components', `É necessário exatamente um ${type}; encontrado(s): ${count}.`);
  }
  const ids = new Set<string>();
  nodes.forEach((node, i) => {
    if (!node.id.trim() || ids.has(node.id)) issue('DUPLICATE_COMPONENT_ID', `components[${i}].id`, 'Identificador de componente vazio ou duplicado.');
    ids.add(node.id);
    const d = node.data;
    const fields = d.componentType === 'API' ? ['preProcessingMs', 'postProcessingMs'] as const : d.componentType === 'DATABASE' ? ['processingMs'] as const : [] as const;
    for (const field of fields) if (!validDuration(d[field] ?? 0)) issue('INVALID_DURATION', `components[${i}].${field}`, 'Tempo deve ser inteiro não negativo.');
  });
  const edgeIds = new Set<string>();
  const pairs = new Set<string>();
  edges.forEach((edge, i) => {
    if (!edge.id.trim() || edgeIds.has(edge.id)) issue('DUPLICATE_CONNECTION_ID', `connections[${i}].id`, 'Identificador de conexão vazio ou duplicado.');
    edgeIds.add(edge.id);
    const source = nodes.find((node) => node.id === edge.source);
    const target = nodes.find((node) => node.id === edge.target);
    if (!source || !target || edge.source === edge.target) {
      issue('INVALID_CONNECTION', `connections[${i}]`, 'Conexão tem origem ou destino inválido.'); return;
    }
    const pair = [edge.source, edge.target].sort().join('|');
    if (pairs.has(pair)) issue('DUPLICATE_CONNECTION', `connections[${i}]`, 'Conexão duplicada entre componentes.');
    pairs.add(pair);
    if (!allowedProtocols(source.data.componentType, target.data.componentType).includes(edge.data?.protocol ?? 'REST')) {
      issue('INVALID_PROTOCOL', `connections[${i}].protocol`, 'Direção ou protocolo incompatível.');
    }
    if (!validDuration(edge.data?.latencyMs ?? NaN)) issue('INVALID_DURATION', `connections[${i}].latencyMs`, 'Latência deve ser inteiro não negativo.');
  });
  for (const [from, to] of [['CLIENT', 'API'], ['API', 'DATABASE']] as const) {
    if (!edges.some((edge) => nodes.some((node) => node.id === edge.source && node.data.componentType === from) && nodes.some((node) => node.id === edge.target && node.data.componentType === to))) {
      issue('MISSING_CONNECTION', 'connections', `Conexão obrigatória ausente: ${from} → ${to}.`);
    }
  }
  return issues;
}
