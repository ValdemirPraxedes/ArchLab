import { memo, useMemo } from 'react';
import { Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, Handle, Position, useReactFlow } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { ComponentIconV3 } from './ComponentIconV3';
import { componentLabelV3, componentTypeV3 } from './catalog';
import type { ComponentTypeV3 } from './catalog';
import type { ProjectV3 } from './project';
export const DRAG_V3 = 'application/x-archlab-v3-component';
type ComponentNode = Node<{ label: string; typeId: string; state?: string }, 'componentV3'>;
const ComponentNodeV3 = memo(function ComponentNodeV3({ data, selected }: NodeProps<ComponentNode>) {
  return <div className={'architecture-node v3-node v3-node--' + data.typeId + (selected ? ' architecture-node--selected' : '')}>
    <Handle type="target" position={Position.Left} isConnectable={false} /><strong><ComponentIconV3 typeId={data.typeId} /> {data.label}</strong><small>{componentLabelV3(data.typeId)}</small><small>{data.state ?? 'Sem estado observado'}</small><Handle type="source" position={Position.Right} isConnectable={false} />
  </div>;
});
const nodeTypes = { componentV3: ComponentNodeV3 };
interface Props { project: ProjectV3; selected: string | null; locked?: boolean; select: (id: string | null) => void; add: (typeId: ComponentTypeV3, position?: { x: number; y: number }) => void; move: (id: string, position: { x: number; y: number }) => void }
function Canvas({ project, selected, locked = false, select, add, move }: Props) {
  const { screenToFlowPosition } = useReactFlow();
  const nodes = useMemo<ComponentNode[]>(() => project.request.architecture.components.map(component => ({ id: component.id, type: 'componentV3', selected: selected === component.id, position: project.positions[component.id] ?? { x: 0, y: 0 }, data: { label: component.displayName, typeId: component.typeId } })), [project, selected]);
  return <div className="editor-canvas" role="region" aria-label="Canvas v3" onDragOver={event => { if (!locked) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }} onDrop={event => { event.preventDefault(); const type = event.dataTransfer.getData(DRAG_V3); if (!locked && componentTypeV3(type)) add(type, screenToFlowPosition({ x: event.clientX, y: event.clientY })); }}>
    <ReactFlow<ComponentNode> nodes={nodes} edges={[]} nodeTypes={nodeTypes} nodesDraggable={!locked} nodesConnectable={false} deleteKeyCode={null} onNodeClick={(_, node) => select(node.id)} onPaneClick={() => select(null)} onNodeDragStop={(_, node) => { if (!locked) move(node.id, node.position); }} fitView aria-label="Grafo da arquitetura v3"><Background /><Controls /><MiniMap /></ReactFlow>
  </div>;
}
export function CanvasV3(props: Props) { return <ReactFlowProvider><Canvas {...props} /></ReactFlowProvider>; }
