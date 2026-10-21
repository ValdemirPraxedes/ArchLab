import type { Node } from '@xyflow/react';

export const COMPONENT_TYPES = ['CLIENT', 'API', 'DATABASE'] as const;
export type ComponentType = (typeof COMPONENT_TYPES)[number];
export type ArchitectureNode = Node<{ componentType: ComponentType; label: string }>;

export const COMPONENT_LABELS: Record<ComponentType, string> = {
  CLIENT: 'Client',
  API: 'API',
  DATABASE: 'Database',
};

export function isComponentType(value: string): value is ComponentType {
  return COMPONENT_TYPES.some((type) => type === value);
}

export function createArchitectureNode(type: ComponentType, position: { x: number; y: number }): ArchitectureNode {
  return {
    id: crypto.randomUUID(),
    type: 'architecture',
    position,
    data: { componentType: type, label: COMPONENT_LABELS[type] },
  };
}
