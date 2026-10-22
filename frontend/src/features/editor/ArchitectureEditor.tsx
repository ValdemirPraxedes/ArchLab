import { useCallback, useMemo, useRef, useState } from 'react';
import {
  Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, useNodesState, useEdgesState, useReactFlow,
} from '@xyflow/react';
import type { DragEvent } from 'react';
import '@xyflow/react/dist/style.css';
import { ArchitectureNode } from './ArchitectureNode';
import Playback from './Playback';
import { reconstructState } from './traceState';
import { archlabClient, ArchlabHttpError } from '../../api/archlabClient';
import type { SimulationResponse } from '../../api/archlabClient';
import { toArchitectureRequest, validateArchitectureLocally } from './validation';
import type { ValidationIssue } from './validation';
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
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [validationStatus, setValidationStatus] = useState<'idle' | 'checking' | 'valid' | 'invalid' | 'error'>('idle');
  const [executionStatus, setExecutionStatus] = useState<'READY' | 'VALIDATING' | 'EXECUTING' | 'COMPLETED' | 'ERROR'>('READY');
  const [simulationResult, setSimulationResult] = useState<SimulationResponse | null>(null);
  const [playbackTimeMs, setPlaybackTimeMs] = useState(0);
  const [playbackSequence, setPlaybackSequence] = useState(Number.POSITIVE_INFINITY);
  const executionLocked = executionStatus === 'VALIDATING' || executionStatus === 'EXECUTING' || executionStatus === 'COMPLETED';
  const [edgeDraft, setEdgeDraft] = useState<{ protocol: Protocol; latencyMs: string } | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const nodeTypes = useMemo(() => ({ architecture: ArchitectureNode }), []);
  const node = selection?.kind === 'node' ? nodes.find((n) => n.id === selection.id) : undefined;
  const edge = selection?.kind === 'edge' ? edges.find((e) => e.id === selection.id) : undefined;
  const stateAtPlayback = simulationResult ? reconstructState(simulationResult.initialState, simulationResult.trace, playbackTimeMs, playbackSequence) : null;
  const activeActivity = simulationResult?.timeline.find((a) => a.startMs <= playbackTimeMs && playbackTimeMs < a.endMs);
  const shownNodes = nodes.map((n) => ({
    ...n, className: stateAtPlayback ? `trace-node--${(stateAtPlayback.components[n.id] ?? 'IDLE').toLowerCase()}` : '',
    data: { ...n.data, stateLabel: stateAtPlayback?.components[n.id] },
  }));
  const shownEdges = edges.map((e) => ({
    ...e, className: activeActivity?.kind === 'COMMUNICATION' && activeActivity.connectionId === e.id ? 'playback-edge-active' : '',
    animated: activeActivity?.kind === 'COMMUNICATION' && activeActivity.connectionId === e.id,
    style: activeActivity?.kind === 'COMMUNICATION' && activeActivity.connectionId === e.id ?
      { stroke: activeActivity.sourceId === e.source ? '#329eff' : '#35d88c', strokeWidth: 3 } : undefined,
  }));
  const destination = nodes.find((n) => n.id === targetId);
  const compatibleProtocols = node && destination ? allowedProtocols(node.data.componentType, destination.data.componentType) : [];
  const possibleTargets = node ? nodes.filter((n) => n.id !== node.id && allowedProtocols(node.data.componentType, n.data.componentType).length > 0) : [];

  const restart = () => {
    setExecutionStatus('READY'); setSimulationResult(null); setPlaybackTimeMs(0); setPlaybackSequence(Number.POSITIVE_INFINITY);
    setIssues([]); setValidationStatus('idle'); setError('');
  };
  const simulate = async () => {
    if (executionStatus !== 'READY' && executionStatus !== 'ERROR') return;
    const localIssues = validateArchitectureLocally(nodes, edges);
    if (localIssues.length) {
      setIssues(localIssues); setValidationStatus('invalid'); setExecutionStatus('ERROR'); return;
    }
    setExecutionStatus('VALIDATING'); setIssues([]);
    try {
      const architecture = toArchitectureRequest(nodes, edges);
      const validated = await archlabClient.validate(architecture);
      if (!validated.valid) {
        setIssues(validated.violations.map((v) => ({ code: v.code, path: v.path, message: v.message })));
        setValidationStatus('invalid'); setExecutionStatus('ERROR'); return;
      }
      setValidationStatus('valid'); setExecutionStatus('EXECUTING');
      const result = await archlabClient.simulate(architecture);
      setPlaybackTimeMs(0); setSimulationResult(result); setExecutionStatus('COMPLETED');
    } catch (cause) {
      setSimulationResult(null); setExecutionStatus('ERROR');
      if (cause instanceof ArchlabHttpError) {
        const problem = cause.problem as { detail?: string; violations?: { code: string; path: string; message: string }[] } | null;
        setIssues(problem?.violations?.map((v) => ({ code: v.code, path: v.path, message: v.message })) ??
          [{ code: 'HTTP_ERROR', path: '', message: problem?.detail ?? `Erro HTTP ${cause.status}` }]);
      } else setIssues([{ code: 'NETWORK_ERROR', path: '', message: 'Falha de comunicação com a API.' }]);
    }
  };
  const resetValidation = () => { setIssues([]); setValidationStatus('idle'); };
  const validate = async () => {
    const localIssues = validateArchitectureLocally(nodes, edges);
    if (localIssues.length) { setIssues(localIssues); setValidationStatus('invalid'); return; }
    setValidationStatus('checking');
    setIssues([]);
    try {
      const result = await archlabClient.validate(toArchitectureRequest(nodes, edges));
      setIssues(result.violations.map((v) => ({ code: v.code, path: v.path, message: v.message })));
      setValidationStatus(result.valid ? 'valid' : 'invalid');
    } catch (cause) {
      const message = cause instanceof ArchlabHttpError ? `Erro HTTP ${cause.status} ao validar.` : 'Não foi possível consultar a API.';
      setIssues([{ code: 'API_ERROR', path: '', message }]);
      setValidationStatus('error');
    }
  };
  const addNode = useCallback((type: ComponentType, position: { x: number; y: number }) => {
    if (executionLocked) return null;
    resetValidation();
    const newNode = createArchitectureNode(type, position);
    setNodes((current) => [...current, newNode]);
    return newNode;
  }, [setNodes, executionLocked]);
  const select = (next: { kind: 'node' | 'edge'; id: string } | null) => {
    setSelection(next); setTargetId(''); setError('');
    const selectedEdge = next?.kind === 'edge' ? edges.find((item) => item.id === next.id) : undefined;
    setEdgeDraft(selectedEdge ? { protocol: selectedEdge.data!.protocol, latencyMs: String(selectedEdge.data!.latencyMs) } : null);
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
    if (!node || executionLocked) return;
    const parsed = Number(value);
    if (!value.trim() || !validDuration(parsed)) { setError('Informe um tempo inteiro não negativo.'); return; }
    resetValidation();
    setNodes((current) => current.map((item) => item.id === node.id ? { ...item, data: { ...item.data, [field]: parsed } } : item));
    setError('');
  };
  const connect = () => {
    if (executionLocked) return;
    if (!node || !destination) { setError('Selecione o destino.'); return; }
    const issue = validateConnection(nodes, edges, node.id, destination.id);
    if (issue) { setError(issue); return; }
    const types = allowedProtocols(node.data.componentType, destination.data.componentType);
    const chosenProtocol = types.includes(protocol) ? protocol : types[0];
    const duration = Number(latencyMs);
    if (!latencyMs.trim() || !validDuration(duration)) { setError('Latência deve ser um inteiro não negativo.'); return; }
    resetValidation();
    setEdges((current) => [...current, createArchitectureEdge(node.id, destination.id, chosenProtocol, duration)]);
    setError(''); setTargetId('');
  };
  const confirmEdge = () => {
    if (!edge || !edgeDraft || executionLocked) return;
    const ms = Number(edgeDraft.latencyMs);
    const source = nodes.find((n) => n.id === edge.source);
    const target = nodes.find((n) => n.id === edge.target);
    if (!source || !target || !allowedProtocols(source.data.componentType, target.data.componentType).includes(edgeDraft.protocol) || !edgeDraft.latencyMs.trim() || !validDuration(ms)) {
      setError('Protocolo incompatível ou latência inválida.'); return;
    }
    resetValidation();
    setEdges((current) => current.map((item) => item.id === edge.id ? { ...item, label: edgeDraft.protocol, data: { protocol: edgeDraft.protocol, latencyMs: ms } } : item));
    setError('');
  };
  const cancelEdge = () => {
    if (!edge) return;
    setEdgeDraft({ protocol: edge.data!.protocol, latencyMs: String(edge.data!.latencyMs) });
    setError('');
  };
  const removeSelected = () => {
    if (!selection || executionLocked) return;
    resetValidation();
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
          <button key={type} type="button" className="palette-item" draggable={!executionLocked} disabled={executionLocked}
            onDragStart={(event) => { if (!executionLocked) onPaletteDragStart(event, type); }}
            onClick={() => addNode(type, { x: 120 + (nodes.length % 4) * 210, y: 110 + Math.floor(nodes.length / 4) * 120 })}>
            {COMPONENT_LABELS[type]}
          </button>
        ))}
      </aside>
      <section className="editor-main" aria-label="Editor visual">
        <div className="editor-toolbar"><span>{nodes.length} componente(s) · {edges.length} conexão(ões)</span><span>{selection ? 'Elemento selecionado' : 'Selecione um componente'}</span><button type="button" onClick={validate} disabled={executionLocked || validationStatus === 'checking'}>Validar arquitetura</button><button type="button" onClick={simulate} disabled={executionLocked}>Simular</button>{executionStatus !== 'READY' && <button type="button" onClick={restart}>Reiniciar</button>}</div>
        <div className="execution-status" role="status">Execução: {executionStatus}{simulationResult && <span> · Tempo virtual total: {simulationResult.execution.totalDurationMs} ms</span>}</div>
        {validationStatus !== 'idle' && <section className="validation-result" aria-label="Resultado da validação" aria-live="polite">
          {validationStatus === 'checking' && <p>Validando arquitetura...</p>}
          {validationStatus === 'valid' && <p>Arquitetura válida para simulação.</p>}
          {(validationStatus === 'invalid' || validationStatus === 'error' || executionStatus === 'ERROR') && <><strong>{validationStatus === 'invalid' ? 'Arquitetura inválida' : 'Erro de validação'}</strong><ul>{issues.map((issue, index) => <li key={index}><strong>{issue.code}</strong>: {issue.message} {issue.path && <small>({issue.path})</small>}</li>)}</ul></>}
        </section>}
        {simulationResult && <Playback result={simulationResult} onTimeChange={(time, sequence) => { setPlaybackTimeMs(time); setPlaybackSequence(sequence); }} />}
        <div className="editor-canvas" ref={canvasRef} onDrop={(event) => { if (!executionLocked) onDrop(event); }} onDragOver={(event) => { if (!executionLocked) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }} aria-label="Canvas da arquitetura">
          <ReactFlow nodes={shownNodes} edges={shownEdges} nodeTypes={nodeTypes} onNodesChange={(changes) => { onNodesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select' && (c.type !== 'position' || !c.dragging))) resetValidation(); }} onEdgesChange={(changes) => { onEdgesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select')) resetValidation(); }}
            onNodeClick={(_, chosen) => select({ kind: 'node', id: chosen.id })}
            onEdgeClick={(_, chosen) => select({ kind: 'edge', id: chosen.id })}
            onPaneClick={() => select(null)}
            deleteKeyCode={null} nodesDraggable={!executionLocked} edgesReconnectable={false} nodesConnectable={false}
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
            <label>Pré-processamento (ms)<input aria-label="Pré-processamento (ms)" type="number" min="0" step="1" value={node.data.preProcessingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('preProcessingMs', e.target.value)} /></label>
            <label>Pós-processamento (ms)<input aria-label="Pós-processamento (ms)" type="number" min="0" step="1" value={node.data.postProcessingMs ?? 0} onChange={(e) => updateTime('postProcessingMs', e.target.value)} /></label>
          </>}
          {node.data.componentType === 'DATABASE' && <label>Processamento (ms)<input aria-label="Processamento (ms)" type="number" min="0" step="1" value={node.data.processingMs ?? 0} onChange={(e) => updateTime('processingMs', e.target.value)} /></label>}
          <h3>Conectar</h3>
          <label>Destino
            <select aria-label="Destino" value={targetId} disabled={executionLocked} onChange={(e) => { setTargetId(e.target.value); const target = nodes.find((n) => n.id === e.target.value); const protocols = target ? allowedProtocols(node.data.componentType, target.data.componentType) : []; setProtocol(protocols[0] ?? 'REST'); setError(''); }}>
              <option value="">Selecione...</option>
              {possibleTargets.map((n) => <option key={n.id} value={n.id}>{n.data.label} · {n.id.slice(0, 8)}</option>)}
            </select>
          </label>
          {compatibleProtocols.length > 0 && <>
            <label>Protocolo<select aria-label="Protocolo" value={protocol} disabled={executionLocked} onChange={(e) => setProtocol(e.target.value as Protocol)}>{compatibleProtocols.map((p) => <option key={p} value={p}>{p}</option>)}</select></label>
            <label>Latência (ms)<input aria-label="Latência (ms)" type="number" min="0" step="1" value={latencyMs} disabled={executionLocked} onChange={(e) => setLatencyMs(e.target.value)} /></label>
          </>}
          <button type="button" disabled={executionLocked} onClick={connect}>Conectar</button>
        </>}
        {edge && <>
          <p>Conexão: {edge.source.slice(0, 8)} → {edge.target.slice(0, 8)}</p>
          <label>Protocolo
            <select aria-label="Editar protocolo" value={edgeDraft?.protocol ?? edge.data?.protocol} disabled={executionLocked} onChange={(e) => setEdgeDraft((draft) => draft ? { ...draft, protocol: e.target.value as Protocol } : null)}>
              {(allowedProtocols(nodes.find((n) => n.id === edge.source)!.data.componentType, nodes.find((n) => n.id === edge.target)!.data.componentType)).map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <label>Latência (ms)<input aria-label="Editar latência (ms)" type="number" min="0" step="1" value={edgeDraft?.latencyMs ?? String(edge.data?.latencyMs ?? 0)} disabled={executionLocked} onChange={(e) => setEdgeDraft((draft) => draft ? { ...draft, latencyMs: e.target.value } : null)} /></label>
          <div className="property-actions"><button type="button" disabled={executionLocked} onClick={confirmEdge}>Confirmar alterações</button><button type="button" disabled={executionLocked} onClick={cancelEdge}>Cancelar alterações</button></div>
        </>}
        {error && <p className="editor-error" role="alert">{error}</p>}
        {selection && <button type="button" className="danger" disabled={executionLocked} onClick={removeSelected}>Remover selecionado</button>}
      </aside>
    </div>
  );
}
export default function ArchitectureEditor() { return <ReactFlowProvider><EditorCanvas /></ReactFlowProvider>; }
