import type { ArchitectureEdge, ArchitectureNode } from './model';
import { allowedProtocols, validDisplayName, validDuration, wouldCreateCycle } from './model';
import type { ValidationIssue } from './validation';

/** UI preflight only; the v2 backend remains authoritative. */
export function validateGraphV2(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const add = (code: string, path: string, message: string) => issues.push({ code, path, message });
  const ids = new Set<string>();
  nodes.forEach((node, i) => {
    if (!node.id.trim() || ids.has(node.id)) add('DUPLICATE_COMPONENT_ID', `components[${i}].id`, 'ID duplicado ou vazio.');
    ids.add(node.id);
    if (!validDisplayName(node.data.displayName ?? node.data.label)) add('INVALID_DISPLAY_NAME', `components[${i}].displayName`, 'Nome inválido.');
    for (const field of (node.data.componentType === 'API' ? ['preProcessingMs', 'postProcessingMs'] : node.data.componentType === 'DATABASE' ? ['processingMs'] : []) as ('preProcessingMs' | 'postProcessingMs' | 'processingMs')[]) {
      if (!validDuration(node.data[field] ?? NaN)) add('INVALID_DURATION', `components[${i}].${field}`, 'Tempo inválido.');
    }
  });
  const edgeIds = new Set<string>();
  const pairs = new Set<string>();
  const accepted: ArchitectureEdge[] = [];
  edges.forEach((edge, i) => {
    const path = `connections[${i}]`;
    if (!edge.id.trim() || edgeIds.has(edge.id)) add('DUPLICATE_CONNECTION_ID', `${path}.id`, 'ID de conexão duplicado ou vazio.');
    edgeIds.add(edge.id);
    const source = nodes.find(n => n.id === edge.source);
    const target = nodes.find(n => n.id === edge.target);
    if (!source || !target || source.id === target.id) { add('INVALID_CONNECTION', path, 'Origem ou destino inválido.'); return; }
    const pair = [edge.source, edge.target].sort().join('|');
    if (pairs.has(pair)) add('DUPLICATE_CONNECTION', path, 'Par de componentes já conectado.');
    pairs.add(pair);
    if (!allowedProtocols(source.data.componentType, target.data.componentType).includes(edge.data?.protocol ?? 'TCP')) add('INVALID_CONNECTION', path, 'Direção ou protocolo incompatível.');
    if (!validDuration(edge.data?.latencyMs ?? NaN)) add('INVALID_DURATION', `${path}.latencyMs`, 'Latência inválida.');
    if (wouldCreateCycle(accepted, edge.source, edge.target)) add('CYCLIC_DEPENDENCY', path, 'Ciclo detectado.');
    accepted.push(edge);
  });
  return issues;
}
