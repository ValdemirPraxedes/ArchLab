import type { ArchitectureV2 } from '../../api/archlabV2Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';
import { allowedIntegrationKinds, defaultIntegration, integrationError } from './integration';

/** Produces a contract-compliant illustrative architecture without sending any requests. */
export function toArchitectureV2(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ArchitectureV2 {
  return {
    components: nodes.map(n => {
      const base = { id: n.id, displayName: n.data.displayName ?? n.data.label };
      switch (n.data.componentType) {
        case 'CLIENT': return { ...base, type: 'CLIENT' as const };
        case 'API': return { ...base, type: 'API' as const, preProcessingMs: n.data.preProcessingMs ?? 0, postProcessingMs: n.data.postProcessingMs ?? 0 };
        case 'DATABASE': return { ...base, type: 'DATABASE' as const, processingMs: n.data.processingMs ?? 0 };
      }
    }),
    connections: edges.map(e => {
      const source = nodes.find(n => n.id === e.source);
      const target = nodes.find(n => n.id === e.target);
      if (!source || !target) throw new Error('Conexão referencia componente inexistente.');
      const kind = allowedIntegrationKinds(source.data.componentType, target.data.componentType)[0];
      if (!kind) throw new Error('Conexão incompatível.');
      const integration = e.data?.integration ?? defaultIntegration(kind);
      const error = integrationError(integration, source.data.componentType, target.data.componentType);
      if (error) throw new Error(error);
      const override = e.data?.apiProcessingOverride;
      if (override && target.data.componentType !== 'API') throw new Error('Override de processamento permitido somente para API de destino.');
      if (override && (![override.preProcessingMs, override.postProcessingMs].every(ms => Number.isSafeInteger(ms) && ms >= 0 && ms <= 1_000_000_000))) throw new Error('Tempos personalizados devem estar entre 0 e 1000000000 ms.');
      return { id: e.id, sourceId: e.source, targetId: e.target, latencyMs: e.data?.latencyMs ?? 0, integration, ...(override ? { apiProcessingOverride: override } : {}) };
    }),
  };
}
