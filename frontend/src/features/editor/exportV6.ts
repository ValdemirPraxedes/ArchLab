import type { ArchitectureEdge, ArchitectureNode } from './model';
import { toArchitectureV1 } from './architectureV1';

/** Stable, importable architecture JSON; legacy types require explicit migration first. */
export function exportArchitectureV6(nodes:ArchitectureNode[],edges:ArchitectureEdge[]):string {
  if (nodes.some(node=>node.data.componentType==='DATABASE' || node.data.componentType==='API'))
    throw new Error('Migre os componentes históricos antes de exportar a arquitetura canônica.');
  const architecture=toArchitectureV1(nodes,edges);
  return JSON.stringify(architecture,null,2)+'\n';
}
