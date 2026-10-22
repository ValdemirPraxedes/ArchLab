import { useCallback, useMemo, useRef, useState } from 'react';
import {
  Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, useNodesState, useEdgesState, useReactFlow,
} from '@xyflow/react';
import type { DragEvent } from 'react';
import '@xyflow/react/dist/style.css';
import { ArchitectureNode } from './ArchitectureNode';
import {
  COMPONENT_TYPES, COMPONENT_LABELS, createArchitectureNode, createArchitectureEdge,
  isComponentType, allowedProtocols, validateConnection, validDuration, removeComponent,
} from './model';
import type { ArchitectureNode as ArchitectureNodeType, ArchitectureEdge, ComponentType, Protocol } from './model';

const DRAG_MIME = 'application/x-archlab-component';
const CANVAS_WIDTH = 2400;
const CANVAS_HEIGHT = 1600;

function EditorCanvas() {
  const [nodes, setNodes, onNodesChange] = useNodesState<ArchitectureNodeType>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<ArchitectureEdge>([]);
  const [selection, setSelection] = useState<{ kind: 'node' | 'edge'; id: string } | null>(null);
  const [targetId, setTargetId] = useState('');
  const [protocol, setProtocol] = useState<Protocol>('REST');
  const [latencyMs, setLatencyMs] = useState('0');
  const [error, setError] = useState('');
  const canvasRef = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const nodeTypes = useMemo(() => ({ architecture: ArchitectureNode }), []);
  const node = selection?.kind === 'node' ? nodes.find((n) => n.id === selection.id) : undefined;
  const edge = selection?.kind === 'edge' ? edges.find((e) => e.id === selection.id) : undefined;
  const destination = nodes.find((n) => n.id === targetId);
  const compatibleProtocols = node && destination ? allowedProtocols(node.data.componentType, destination.data.componentType) : [];
  const possibleTargets = node ? nodes.filter((n) => n.id !== node.id && allowedProtocols(node.data.componentType, n.data.componentType).length > 0) : [];

  const addNode = useCallback((type: ComponentType, position: { x: number; y: number }) => {
    const newNode = createArchitectureNode(type, position);
    setNodes((current) => [...current, newNode]);
    return newNode;
  }, [setNodes]);
  const select = (next: { kind: 'node' | 'edge'; id: string } | null) => {
    setSelection(next); setTargetId(''); setError('');
  };
  const onPaletteDragStart = (event: DragEvent<HTMLButtonElement>, type: ComponentType) => {
    event.dataTransfer.setData(DRAG_MIME, type); event.dataTransfer.effectAllowed = 'copy';
  };
  const onDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rawType = event.dataTransfer.getData(DRAG_MIME);
    if (!isComponentType(rawType)) return;
    const bounds = canvasRef.current?.getBoundingClientRect();
    if (!bounds || event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) return;
    const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    addNode(rawType, { x: Math.max(0, Math.min(position.x, CANVAS_WIDTH - 180)), y: Math.max(0, Math.min(position.y, CANVAS_HEIGHT - 90)) });
  }, [addNode, screenToFlowPosition]);

  const updateTime = (field: 'preProcessingMs' | 'postProcessingMs' | 'processingMs', value: string) => {
    if (!node) return;
    const parsed = Number(value);
    if (!value.trim() || !validDuration(parsed)) { setError('Informe um tempo inteiro não negativo.'); return; }
    setNodes((current) => current.map((item) => item.id === node.id ? { ...item, data: { ...item.data, [field]: parsed } } : item));
    setError('');
  };
  const connect = () => {
    if (!node || !destination) { setError('Selecione o destino.'); return; }
    const issue = validateConnection(nodes, edges, node.id, destination.id);
    if (issue) { setError(issue); return; }
    const types = allowedProtocols(node.data.componentType, destination.data.componentType);
    const chosenProtocol = types.includes(protocol) ? protocol : types[0];
    const duration = Number(latencyMs);
    if (!latencyMs.trim() || !validDuration(duration)) { setError('Latência deve ser um inteiro não negativo.'); return; }
    setEdges((current) => [...current, createArchitectureEdge(node.id, destination.id, chosenProtocol, duration)]);
    setError(''); setTargetId('');
  };
  const updateEdge = (nextProtocol: Protocol, nextLatency: string) => {
    if (!edge) return;
    const ms = Number(nextLatency);
    const source = nodes.find((n) => n.id === edge.source);
    const target = nodes.find((n) => n.id === edge.target);
    if (!source || !target || !allowedProtocols(source.data.componentType, target.data.componentType).includes(nextProtocol) || !nextLatency.trim() || !validDuration(ms)) {
      setError('Protocolo incompatível ou latência inválida.'); return;
    }
    setEdges((current) => current.map((item) => item.id === edge.id ? { ...item, label: nextProtocol, data: { protocol: nextProtocol, latencyMs: ms } } : item));
    setError('');
  };
  const removeSelected = () => {
    if (!selection) return;
    if (selection.kind === 'node') {
      const result = removeComponent(nodes, edges, selection.id);
      setNodes(result.nodes); setEdges(result.edges);
    } else setEdges((current) => current.filter((item) => item.id !== selection.id));
    select(null);
  };
  return (
    <div className="editor-layout">
      <aside className="palette" aria-label="Paleta de componentes">
        <h2>Componentes</h2>
        <p>Arraste para o canvas ou clique para adicionar.</p>
        {COMPONENT_TYPES.map((type) => (
          <button key={type} type="button" className="palette-item" draggable
            onDragStart={(event) => onPaletteDragStart(event, type)}
            onClick={() => addNode(type, { x: 120 + (nodes.length % 4) * 210, y: 110 + Math.floor(nodes.length / 4) * 120 })}>
            {COMPONENT_LABELS[type]}
          </button>
        ))}
      </aside>
      <section className="editor-main" aria-label="Editor visual">
        <div className="editor-toolbar"><span>{nodes.length} componente(s) · {edges.length} conexão(ões)</span><span>{selection ? 'Elemento selecionado' : 'Selecione um componente'}</span></div>
        <div className="editor-canvas" ref={canvasRef} onDrop={onDrop} onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; }} aria-label="Canvas da arquitetura">
          <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            onNodeClick={(_, chosen) => select({ kind: 'node', id: chosen.id })}
            onEdgeClick={(_, chosen) => select({ kind: 'edge', id: chosen.id })}
            onPaneClick={() => select(null)}
            deleteKeyCode={null} edgesReconnectable={false} nodesConnectable={false}
            nodeExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} translateExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} fitView>
            <Background /><Controls /><MiniMap pannable zoomable />
          </ReactFlow>
        </div>
      </section>
      <aside className="properties" aria-label="Propriedades">
        <h2>Propriedades</h2>
        {!selection && <p>Selecione um componente ou conexão no canvas.</p>}
        {node && <>
          <p><strong>{node.data.label}</strong> · {node.id.slice(0, 8)}</p>
          {node.data.componentType === 'API' && <>
            <label>Pré-processamento (ms)<input aria-label="Pré-processamento (ms)" type="number" min="0" step="1" value={node.data.preProcessingMs ?? 0} onChange={(e) => updateTime('preProcessingMs', e.target.value)} /></label>
            <label>Pós-processamento (ms)<input aria-label="Pós-processamento (ms)" type="number" min="0" step="1" value={node.data.postProcessingMs ?? 0} onChange={(e) => updateTime('postProcessingMs', e.target.value)} /></label>
          </>}
          {node.data.componentType === 'DATABASE' && <label>Processamento (ms)<input aria-label="Processamento (ms)" type="number" min="0" step="1" value={node.data.processingMs ?? 0} onChange={(e) => updateTime('processingMs', e.target.value)} /></label>}
          <h3>Conectar</h3>
          <label>Destino
            <select aria-label="Destino" value={targetId} onChange={(e) => { setTargetId(e.target.value); const target = nodes.find((n) => n.id === e.target.value); const protocols = target ? allowedProtocols(node.data.componentType, target.data.componentType) : []; setProtocol(protocols[0] ?? 'REST'); setError(''); }}>
              <option value="">Selecione...</option>
              {possibleTargets.map((n) => <option key={n.id} value={n.id}>{n.data.label} · {n.id.slice(0, 8)}</option>)}
            </select>
          </label>
          {compatibleProtocols.length > 0 && <>
            <label>Protocolo<select aria-label="Protocolo" value={protocol} onChange={(e) => setProtocol(e.target.value as Protocol)}>{compatibleProtocols.map((p) => <option key={p} value={p}>{p}</option>)}</select></label>
            <label>Latência (ms)<input aria-label="Latência (ms)" type="number" min="0" step="1" value={latencyMs} onChange={(e) => setLatencyMs(e.target.value)} /></label>
          </>}
          <button type="button" onClick={connect}>Conectar</button>
        </>}
        {edge && <>
          <p>Conexão: {edge.source.slice(0, 8)} → {edge.target.slice(0, 8)}</p>
          <label>Protocolo
            <select aria-label="Editar protocolo" value={edge.data?.protocol} onChange={(e) => updateEdge(e.target.value as Protocol, String(edge.data?.latencyMs ?? 0))}>
              {(allowedProtocols(nodes.find((n) => n.id === edge.source)!.data.componentType, nodes.find((n) => n.id === edge.target)!.data.componentType)).map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <label>Latência (ms)<input aria-label="Editar latência (ms)" type="number" min="0" step="1" value={edge.data?.latencyMs ?? 0} onChange={(e) => updateEdge(edge.data!.protocol, e.target.value)} /></label>
        </>}
        {error && <p className="editor-error" role="alert">{error}</p>}
        {selection && <button type="button" className="danger" onClick={removeSelected}>Remover selecionado</button>}
      </aside>
    </div>
  );
}
export default function ArchitectureEditor() { return <ReactFlowProvider><EditorCanvas /></ReactFlowProvider>; }
