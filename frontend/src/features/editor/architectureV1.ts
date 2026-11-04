import type { ArchitectureV1, SimulationRequestV1 } from '../../api/archlabV1Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';
import type { ScenarioDraft } from './scenario';
import { toArchitectureV2 } from './architectureV2';

export function toArchitectureV1(nodes: ArchitectureNode[], edges: ArchitectureEdge[]): ArchitectureV1 {
  const architecture = toArchitectureV2(nodes, edges);
  return {
    ...architecture,
    components: architecture.components.map((component, index) => {
      const visualType = nodes[index].data.componentType;
      return component.type === 'CLIENT' && visualType !== 'CLIENT'
        ? {...component, type: visualType} : component;
    }),
  } as unknown as ArchitectureV1;
}

export function toRequestV1(architecture: ArchitectureV1, scenario: ScenarioDraft): SimulationRequestV1 {
  return {architecture, scenario: {...scenario, kind: 'REQUEST_RESPONSE'}} as SimulationRequestV1;
}
