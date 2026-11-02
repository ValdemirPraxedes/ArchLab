import type { EndpointV3 } from './types';
import { relationEdgesV3 } from './graphModel';
import { memo, useMemo } from 'react';
import { Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, Handle, Position, useReactFlow } from '@xyflow/react';
import type { Edge, Node, NodeProps } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { ComponentIconV3 } from './ComponentIconV3';
import { componentLabelV3, componentTypeV3 } from './catalog';
import type { ComponentTypeV3 } from './catalog';
import type { ProjectV3 } from './project';
export const DRAG_V3 = 'application/x-archlab-v3-component';
type ComponentNode = Node<{ label: string; typeId: string; state?: string; ports: EndpointV3[]; locked: boolean; invalid: boolean }, 'componentV3'>;
const ComponentNodeV3 = memo(function ComponentNodeV3({ data, selected }: NodeProps<ComponentNode>) {
  return <div className={'architecture-node v3-node v3-node--' + data.typeId + (selected ? ' architecture-node--selected' : '')+(data.invalid?' v3-node--diagnostic':'')}>
    {data.ports.filter(e => e.direction !== 'OUTBOUND').map((e,index) => <Handle key={e.id} id={e.id} type="target" position={Position.Left} style={{ top: 28 + index * 18 }} title={e.id + ' · ' + e.protocolProfileId} isConnectable={!data.locked} />)}<Handle id="@structural-in" type="target" position={Position.Left} isConnectable={false} style={{opacity:0,pointerEvents:'none'}}/><Handle id="@structural-out" type="source" position={Position.Right} isConnectable={false} style={{opacity:0,pointerEvents:'none'}}/><strong><ComponentIconV3 typeId={data.typeId} /> {data.label}</strong><small>{componentLabelV3(data.typeId)}</small><small>{data.state ?? 'Sem estado observado'}</small>{data.ports.filter(e => e.direction !== 'INBOUND').map((e,index) => <Handle key={e.id} id={e.id} type="source" position={Position.Right} style={{ top: 28 + index * 18 }} title={e.id + ' · ' + e.protocolProfileId} isConnectable={!data.locked} />)}
  </div>;
});
const nodeTypes = { componentV3: ComponentNodeV3 };
interface Props { project: ProjectV3; selected: string | null; locked?: boolean; select: (id: string | null) => void; add: (typeId: ComponentTypeV3, position?: { x: number; y: number }) => void; move: (id: string, position: { x: number; y: number }) => void; connect?: (sourceEndpointId: string, targetEndpointId: string) => void; selectRelation?: (id: string) => void; invalidIds?: Set<string> }
function Canvas({ project, selected, locked = false, select, add, move, connect, selectRelation, invalidIds }: Props) {
  const { screenToFlowPosition } = useReactFlow();
  const nodes = useMemo<ComponentNode[]>(() => project.request.architecture.components.map(component => ({ id: component.id, type: 'componentV3', selected: selected === component.id, position: project.positions[component.id] ?? { x: 0, y: 0 }, data: { label: component.displayName, typeId: component.typeId, invalid:invalidIds?.has(component.id)??false, ports: project.request.architecture.endpoints.filter(e => e.ownerId === component.id), locked } })), [project, selected, locked, invalidIds]);
  const edges:Edge[]=relationEdgesV3(project.request.architecture);
  return <div className="editor-canvas" role="region" aria-label="Canvas v3" onDragOver={event => { if (!locked) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }} onDrop={event => { event.preventDefault(); const type = event.dataTransfer.getData(DRAG_V3); if (!locked && componentTypeV3(type)) add(type, screenToFlowPosition({ x: event.clientX, y: event.clientY })); }}>
    <ReactFlow<ComponentNode> nodes={nodes} edges={edges} onConnect={connection => { if (!locked && connection.sourceHandle && connection.targetHandle) connect?.(connection.sourceHandle,connection.targetHandle); }} onEdgeClick={(_,edge)=>selectRelation?.(edge.id)} nodeTypes={nodeTypes} nodesDraggable={!locked} nodesConnectable={!locked} deleteKeyCode={null} onNodeClick={(_, node) => select(node.id)} onPaneClick={() => select(null)} onNodeDragStop={(_, node) => { if (!locked) move(node.id, node.position); }} fitView aria-label="Grafo da arquitetura v3"><Background /><Controls /><MiniMap /></ReactFlow>
  </div>;
}
export function CanvasV3(props: Props) { return <ReactFlowProvider><Canvas {...props} /></ReactFlowProvider>; }
