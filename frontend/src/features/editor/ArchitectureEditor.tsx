import { useCallback, useMemo, useRef, useState } from 'react';
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';
import type { DragEvent } from 'react';
import '@xyflow/react/dist/style.css';
import { ArchitectureNode } from './ArchitectureNode';
import { COMPONENT_TYPES, COMPONENT_LABELS, createArchitectureNode, isComponentType } from './model';
import type { ArchitectureNode as ArchitectureNodeType, ComponentType } from './model';

const DRAG_MIME = 'application/x-archlab-component';
const CANVAS_WIDTH = 2400;
const CANVAS_HEIGHT = 1600;

function EditorCanvas() {
  const [nodes, setNodes, onNodesChange] = useNodesState<ArchitectureNodeType>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const nodeTypes = useMemo(() => ({ architecture: ArchitectureNode }), []);

  const addNode = useCallback((type: ComponentType, position: { x: number; y: number }) => {
    const node = createArchitectureNode(type, position);
    setNodes((current) => [...current, node]);
    return node;
  }, [setNodes]);

  const onPaletteDragStart = (event: DragEvent<HTMLButtonElement>, type: ComponentType) => {
    event.dataTransfer.setData(DRAG_MIME, type);
    event.dataTransfer.effectAllowed = 'copy';
  };

  const onDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rawType = event.dataTransfer.getData(DRAG_MIME);
    if (!isComponentType(rawType)) return;
    const bounds = canvasRef.current?.getBoundingClientRect();
    if (!bounds || event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) return;
    const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    addNode(rawType, {
      x: Math.max(0, Math.min(position.x, CANVAS_WIDTH - 180)),
      y: Math.max(0, Math.min(position.y, CANVAS_HEIGHT - 90)),
    });
  }, [addNode, screenToFlowPosition]);

  return (
    <div className="editor-layout">
      <aside className="palette" aria-label="Paleta de componentes">
        <h2>Componentes</h2>
        <p>Arraste para o canvas ou clique para adicionar.</p>
        {COMPONENT_TYPES.map((type) => (
          <button
            key={type}
            type="button"
            className="palette-item"
            draggable
            onDragStart={(event) => onPaletteDragStart(event, type)}
            onClick={() => addNode(type, { x: 120 + (nodes.length % 4) * 210, y: 110 + Math.floor(nodes.length / 4) * 120 })}
          >
            {COMPONENT_LABELS[type]}
          </button>
        ))}
      </aside>
      <section className="editor-main" aria-label="Editor visual">
        <div className="editor-toolbar">
          <span>{nodes.length} componente(s)</span>
          <span>{selectedId ? 'Componente selecionado' : 'Selecione um componente'}</span>
        </div>
        <div
          className="editor-canvas"
          ref={canvasRef}
          onDrop={onDrop}
          onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }}
          aria-label="Canvas da arquitetura"
        >
          <ReactFlow
            nodes={nodes}
            edges={[]}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onSelectionChange={({ nodes: selection }) => setSelectedId(selection[0]?.id ?? null)}
            onPaneClick={() => setSelectedId(null)}
            nodeExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]}
            translateExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]}
            fitView
          >
            <Background />
            <Controls />
            <MiniMap pannable zoomable />
          </ReactFlow>
        </div>
      </section>
    </div>
  );
}

export default function ArchitectureEditor() {
  return <ReactFlowProvider><EditorCanvas /></ReactFlowProvider>;
}
