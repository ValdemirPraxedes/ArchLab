import { memo } from 'react';
import { ComponentIcon } from './ComponentIcon';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import type { ArchitectureNode as ArchitectureNodeType } from './model';

function ArchitectureNodeView({ data, selected }: NodeProps<ArchitectureNodeType>) {
  return (
    <div className={`architecture-node architecture-node--${data.componentType.toLowerCase()}${selected ? ' architecture-node--selected' : ''}`}>
      <Handle type="target" position={Position.Left} isConnectable={false} />
      <strong><ComponentIcon type={data.componentType} /> {data.displayName ?? data.label}</strong>
      <small>{data.componentType}</small>
      {data.stateLabel && <small aria-label="Estado do componente">{data.stateLabel}</small>}
      <Handle type="source" position={Position.Right} isConnectable={false} />
    </div>
  );
}
export const ArchitectureNode = memo(ArchitectureNodeView);
