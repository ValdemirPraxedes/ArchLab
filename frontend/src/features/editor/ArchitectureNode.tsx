import { memo } from 'react';
import type { NodeProps } from '@xyflow/react';
import type { ArchitectureNode as ArchitectureNodeType } from './model';

function ArchitectureNodeView({ data, selected }: NodeProps<ArchitectureNodeType>) {
  return (
    <div className={`architecture-node architecture-node--${data.componentType.toLowerCase()}${selected ? ' architecture-node--selected' : ''}`}>
      <strong>{data.label}</strong>
      <small>{data.componentType}</small>
    </div>
  );
}

export const ArchitectureNode = memo(ArchitectureNodeView);
