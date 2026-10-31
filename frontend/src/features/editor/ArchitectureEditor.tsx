import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, useNodesState, useEdgesState, useReactFlow,
} from '@xyflow/react';
import type { DragEvent } from 'react';
import '@xyflow/react/dist/style.css';
import { ArchitectureNode } from './ArchitectureNode';
import { ComponentIcon } from './ComponentIcon';
import { IntegrationEditor } from './IntegrationEditor';
import { defaultIntegration, allowedIntegrationKinds } from './integration';
import { ScenarioEditor } from './ScenarioEditor';
import { SimulationPlaybackV2 } from './SimulationPlaybackV2';
import { inFlightMessages, traceStateAt } from './traceV2';
import type { TraceCursor } from './traceV2';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { emptyScenario, reconcileScenario, selectedPath } from './scenario';
import type { ScenarioDraft } from './scenario';
import { toArchitectureV2 } from './architectureV2';
import { archlabV2Client, ArchlabV2HttpError } from '../../api/archlabV2Client';
import type { ValidationIssue } from './validation';
import {
  COMPONENT_TYPES, COMPONENT_LABELS, createArchitectureNode, createArchitectureEdge,
  isComponentType, allowedProtocols, validateConnection, validDuration, removeComponent, nextDisplayName, validDisplayName,
} from './model';
import type { ArchitectureNode as ArchitectureNodeType, ArchitectureEdge, ComponentType, Protocol } from './model';

const DRAG_MIME = 'application/x-archlab-component';
const CANVAS_WIDTH = 2400;
const CANVAS_HEIGHT = 1600;

function EditorCanvas() {
  const [nodes, setNodes, onNodesChange] = useNodesState<ArchitectureNodeType>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<ArchitectureEdge>([]);
  const [scenario, setScenario] = useState<ScenarioDraft>(emptyScenario);
  const [selection, setSelection] = useState<{ kind: 'node' | 'edge'; id: string } | null>(null);
  const [targetId, setTargetId] = useState('');
  const [protocol, setProtocol] = useState<Protocol>('REST');
  const [latencyMs, setLatencyMs] = useState('0');
  const [error, setError] = useState('');
  const [nameDraft, setNameDraft] = useState('');
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [validationScope, setValidationScope] = useState<'architecture' | 'scenario'>('architecture');
  const [validationStatus, setValidationStatus] = useState<'idle' | 'checking' | 'valid' | 'invalid' | 'error'>('idle');
  const [executionStatus, setExecutionStatus] = useState<'READY' | 'VALIDATING' | 'EXECUTING' | 'COMPLETED' | 'ERROR'>('READY');
  const [simulationResultV2, setSimulationResultV2] = useState<SimulationResponseV2 | null>(null);
  const [traceCursor, setTraceCursor] = useState<TraceCursor>({time:0,sequence:0});
  const generation = useRef(0);
  const [v2PlaybackStatus, setV2PlaybackStatus] = useState<'REPRODUZINDO' | 'PAUSADA' | 'CONCLUÍDA'>('PAUSADA');
  const executionLocked = executionStatus === 'VALIDATING' || executionStatus === 'EXECUTING' || executionStatus === 'COMPLETED' || simulationResultV2 !== null;
  const [edgeDraft, setEdgeDraft] = useState<{ protocol: Protocol; latencyMs: string } | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const propertiesRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!selection) return;
    const dismiss = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      if (propertiesRef.current?.contains(target)) return;
      if (target.closest('.react-flow__node, .react-flow__edge')) return;
      select(null);
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [selection]);
  const { screenToFlowPosition } = useReactFlow();
  const nodeTypes = useMemo(() => ({ architecture: ArchitectureNode }), []);
  const node = selection?.kind === 'node' ? nodes.find((n) => n.id === selection.id) : undefined;
  const edge = selection?.kind === 'edge' ? edges.find((e) => e.id === selection.id) : undefined;
  const traceState = simulationResultV2 ? traceStateAt(simulationResultV2,traceCursor) : null;
  const flying = simulationResultV2 ? inFlightMessages(simulationResultV2.trace,traceCursor) : [];
  const flyingConnections = new Set(flying.map(e => e.connectionId));
  const path = selectedPath(scenario, nodes, edges);
  const hasPath = path.components.size > 0;
  const invalidIds = new Set(issues.flatMap(issue => {
    const match = /components\[(\d+)\]/.exec(issue.path);
    const id = match ? nodes[Number(match[1])]?.id : undefined;
    return id ? [id] : [];
  }));
  const invalidEdgeIds = new Set(issues.flatMap(issue => {
    const match = /connections\[(\d+)\]/.exec(issue.path);
    const id = match ? edges[Number(match[1])]?.id : undefined;
    return id ? [id] : [];
  }));
  const shownNodes = nodes.map((n) => ({
    ...n, className: invalidIds.has(n.id) ? 'validation-node--invalid' : traceState ? `trace-node--${(traceState.components[n.id] ?? 'IDLE').toLowerCase()}` : hasPath ? (path.components.has(n.id) ? 'scenario-node--active' : 'scenario-node--inactive') : '',
    data: { ...n.data, stateLabel: (['IDLE', 'PROCESSING', 'WAITING', 'COMPLETED'] as const).find(status => status === traceState?.components[n.id]) },
  }));
  const shownEdges = edges.map((e) => ({
    ...e, className: invalidEdgeIds.has(e.id) ? 'validation-edge--invalid' : flyingConnections.has(e.id) ? 'playback-edge-active' : hasPath ? (path.connections.has(e.id) ? 'scenario-edge--active' : 'scenario-edge--inactive') : '',
    animated: flyingConnections.has(e.id),
    style: flyingConnections.has(e.id) ? { stroke: '#329eff', strokeWidth: 3 } : hasPath && path.connections.has(e.id) ? { stroke: '#1ca879', strokeWidth: 3 } : undefined,
  }));
  const destination = nodes.find((n) => n.id === targetId);
  const compatibleProtocols = node && destination ? allowedProtocols(node.data.componentType, destination.data.componentType) : [];
  const possibleTargets = node ? nodes.filter((n) => n.id !== node.id && allowedProtocols(node.data.componentType, n.data.componentType).length > 0) : [];

  const restart = () => {
    generation.current += 1;
    setTraceCursor({time:0,sequence:0});
    setExecutionStatus('READY'); setSimulationResultV2(null); setV2PlaybackStatus('PAUSADA');
    setIssues([]); setValidationStatus('idle'); setError('');
  };
  const simulate = async () => {
    if (executionStatus !== 'READY' && executionStatus !== 'ERROR') return;
    const requestGeneration = ++generation.current;
    setIssues([]); setValidationStatus('checking'); setExecutionStatus('VALIDATING'); setValidationScope('scenario');
    try {
      const architecture = toArchitectureV2(nodes, edges);
      const request = { architecture, scenario };
      const result = await archlabV2Client.validateScenario(request);
      if (requestGeneration !== generation.current) return;
      if (!result.valid) {
        setIssues(result.violations.map(v => ({ code: v.code, path: v.path, message: v.message })));
        setValidationStatus('invalid'); setExecutionStatus('ERROR'); return;
      }
      setValidationStatus('valid'); setExecutionStatus('EXECUTING');
      const response = await archlabV2Client.simulate(request);
      if (requestGeneration !== generation.current) return;
      setTraceCursor({time:0,sequence:0});
      setSimulationResultV2(response);
      setV2PlaybackStatus('PAUSADA');
      setExecutionStatus('COMPLETED');
    } catch (cause) {
      if (requestGeneration !== generation.current) return;
      setSimulationResultV2(null); setExecutionStatus('ERROR'); setValidationStatus('error');
      if (cause instanceof ArchlabV2HttpError) {
        const violations = cause.violations.map(v => ({ code: v.code, path: v.path, message: v.message }));
        setIssues(violations.length ? violations : [{ code: cause.problem?.code ?? 'HTTP_ERROR', path: '', message: `HTTP ${cause.status}: ${cause.problem?.detail ?? cause.message}` }]);
      } else {
        setIssues([{ code: 'NETWORK_ERROR', path: '', message: cause instanceof Error ? cause.message : 'Falha de comunicação com a API v2.' }]);
      }
    }
  };
  const resetValidation = useCallback(() => { setIssues([]); setValidationStatus('idle'); setExecutionStatus((status) => status === 'ERROR' ? 'READY' : status); }, []);
  const validateV2 = async (scope: 'architecture' | 'scenario') => {
    if (executionLocked || validationStatus === 'checking') return;
    setValidationScope(scope);
    setIssues([]);
    setValidationStatus('checking');
    try {
      const architecture = toArchitectureV2(nodes, edges);
      const response = scope === 'architecture'
        ? await archlabV2Client.validateArchitecture(architecture)
        : await archlabV2Client.validateScenario({ architecture, scenario });
      setIssues(response.violations.map(v => ({ code: v.code, path: v.path, message: v.message })));
      setValidationStatus(response.valid ? 'valid' : 'invalid');
    } catch (cause) {
      if (cause instanceof ArchlabV2HttpError) {
        const problem = cause.problem;
        const violations = cause.violations.map(v => ({ code: v.code, path: v.path, message: v.message }));
        setIssues(violations.length ? violations : [{
          code: problem?.code ?? 'HTTP_ERROR', path: '', message: `HTTP ${cause.status}: ${problem?.detail ?? problem?.title ?? cause.message}`,
        }]);
      } else {
        setIssues([{ code: 'NETWORK_ERROR', path: '', message: cause instanceof Error ? cause.message : 'Falha na validação v2.' }]);
      }
      setValidationStatus('error');
    }
  };
  const addNode = useCallback((type: ComponentType, position: { x: number; y: number }) => {
    if (executionLocked) return null;
    resetValidation();
    const newNode = createArchitectureNode(type, position, nextDisplayName(type, nodes));
    setNodes((current) => [...current, newNode]);
    return newNode;
  }, [setNodes, executionLocked, nodes, resetValidation]);
  const select = (next: { kind: 'node' | 'edge'; id: string } | null) => {
    setSelection(next); setTargetId(''); setError('');
    setNameDraft(next?.kind === 'node' ? (nodes.find((item) => item.id === next.id)?.data.displayName ?? '') : '');
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

  const commitName = () => {
    if (!node || executionLocked) return;
    const nextName = nameDraft.trim();
    if (!validDisplayName(nextName)) { setError('Nome obrigatório com até 80 caracteres.'); return; }
    setNodes((current) => current.map((item) => item.id === node.id ?
      { ...item, data: { ...item.data, displayName: nextName, label: nextName } } : item));
    resetValidation(); setError('');
  };
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
    const created = createArchitectureEdge(node.id, destination.id, chosenProtocol, duration);
    const kind = allowedIntegrationKinds(node.data.componentType, destination.data.componentType)[0];
    setEdges((current) => [...current, { ...created, data: { ...created.data!, integration: defaultIntegration(kind) } }]);
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
    setEdges((current) => current.map((item) => item.id === edge.id ? { ...item, label: edgeDraft.protocol, data: { ...item.data!, protocol: edgeDraft.protocol, latencyMs: ms } } : item));
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
      setScenario((current) => reconcileScenario(current, result.nodes, result.edges));
    } else setEdges((current) => {
      const remaining = current.filter((item) => item.id !== selection.id);
      setScenario((value) => reconcileScenario(value, nodes, remaining));
      return remaining;
    });
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
            <ComponentIcon type={type} /> {COMPONENT_LABELS[type]}
          </button>
        ))}
      </aside>
      <section className="editor-main" aria-label="Editor visual">
        <div className="editor-toolbar"><span>{nodes.length} componente(s) · {edges.length} conexão(ões)</span><span>{selection ? 'Elemento selecionado' : 'Selecione um componente'}</span><button type="button" onClick={() => void validateV2('architecture')} disabled={executionLocked || validationStatus === 'checking'}>Validar arquitetura v2</button><button type="button" onClick={() => void validateV2('scenario')} disabled={executionLocked || validationStatus === 'checking'}>Validar cenário v2</button><button type="button" onClick={simulate} disabled={executionLocked}>Simular</button>{executionStatus !== 'READY' && <button type="button" onClick={restart}>Reiniciar</button>}</div>
        <div className="execution-status" role="status">Execução: {simulationResultV2 ? v2PlaybackStatus : executionStatus}{simulationResultV2 && <span> · Tempo virtual total: {simulationResultV2.execution.totalDurationMs} ms</span>}</div>
        {validationStatus !== 'idle' && <section className="validation-result" aria-label="Resultado da validação" aria-live="polite">
          {validationStatus === 'checking' && <p>Validando arquitetura...</p>}
          {validationStatus === 'valid' && <p>{validationScope === 'architecture' ? 'Arquitetura v2 válida pelo backend.' : 'Cenário v2 válido pelo backend.'}</p>}
          {(validationStatus === 'invalid' || validationStatus === 'error' || executionStatus === 'ERROR') && <><strong>{validationStatus === 'invalid' ? (validationScope === 'architecture' ? 'Arquitetura inválida' : 'Cenário inválido') : 'Erro de validação'}</strong><ul>{issues.map((issue, index) => <li key={index}><strong>{issue.code}</strong>: {issue.message} {issue.path && <small>({issue.path})</small>}</li>)}</ul></>}
        </section>}
        <ScenarioEditor nodes={nodes} edges={edges} value={scenario} onChange={(next) => { setScenario(next); resetValidation(); }} disabled={executionLocked} />
        {simulationResultV2 && <SimulationPlaybackV2 result={simulationResultV2} onStatus={setV2PlaybackStatus} onCursor={setTraceCursor} />}
        <div className="editor-canvas" ref={canvasRef} onDrop={(event) => { if (!executionLocked) onDrop(event); }} onDragOver={(event) => { if (!executionLocked) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }} aria-label="Canvas da arquitetura">
          <ReactFlow nodes={shownNodes} edges={shownEdges} nodeTypes={nodeTypes} onNodesChange={(changes) => { onNodesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select' && (c.type !== 'position' || !c.dragging))) resetValidation(); }} onEdgesChange={(changes) => { onEdgesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select')) resetValidation(); }}
            onNodeClick={(_, chosen) => select({ kind: 'node', id: chosen.id })}
            onEdgeClick={(_, chosen) => select({ kind: 'edge', id: chosen.id })}
            onPaneClick={() => select(null)}
            deleteKeyCode={null} nodesDraggable={!executionLocked} edgesReconnectable={false} nodesConnectable={false}
            nodeExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} translateExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} fitView>
            <Background /><Controls /><MiniMap pannable zoomable nodeColor={(n) => n.data?.componentType === "CLIENT" ? "#1d9eaf" : n.data?.componentType === "API" ? "#6963d4" : "#cc9440"} nodeStrokeColor="#37506a" nodeStrokeWidth={2} maskColor="rgba(95,126,160,0.18)" />
          </ReactFlow>
        </div>
      </section>
      <aside className="properties" aria-label="Propriedades" ref={propertiesRef}>
        <h2>Propriedades</h2>
        {!selection && <p>Selecione um componente ou conexão no canvas.</p>}
        {node && <>
          <p><strong>{node.data.displayName ?? node.data.label}</strong> · {node.id.slice(0, 8)}</p>
          <label>Nome de apresentação<input aria-label="Nome de apresentação" type="text" maxLength={100} value={nameDraft} disabled={executionLocked} onChange={(event) => setNameDraft(event.target.value)} onBlur={commitName} /></label>
          <button type="button" disabled={executionLocked} onClick={commitName}>Salvar nome</button>
          {node.data.componentType === 'API' && <>
            <label>Pré-processamento (ms)<input aria-label="Pré-processamento (ms)" type="number" min="0" step="1" value={node.data.preProcessingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('preProcessingMs', e.target.value)} /></label>
            <label>Pós-processamento (ms)<input aria-label="Pós-processamento (ms)" type="number" min="0" step="1" value={node.data.postProcessingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('postProcessingMs', e.target.value)} /></label>
          </>}
          {node.data.componentType === 'DATABASE' && <label>Processamento (ms)<input aria-label="Processamento (ms)" type="number" min="0" step="1" value={node.data.processingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('processingMs', e.target.value)} /></label>}
          <h3>Conectar</h3>
          <label>Destino
            <select aria-label="Destino" value={targetId} disabled={executionLocked} onChange={(e) => { setTargetId(e.target.value); const target = nodes.find((n) => n.id === e.target.value); const protocols = target ? allowedProtocols(node.data.componentType, target.data.componentType) : []; setProtocol(protocols[0] ?? 'REST'); setError(''); }}>
              <option value="">Selecione...</option>
              {possibleTargets.map((n) => <option key={n.id} value={n.id} disabled={Boolean(validateConnection(nodes, edges, node.id, n.id))}>{n.data.label} · {n.id.slice(0, 8)}</option>)}
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
          <IntegrationEditor key={edge.id} value={edge.data?.integration ?? defaultIntegration(allowedIntegrationKinds(nodes.find(n => n.id === edge.source)!.data.componentType, nodes.find(n => n.id === edge.target)!.data.componentType)[0])}
            source={nodes.find(n => n.id === edge.source)!.data.componentType} target={nodes.find(n => n.id === edge.target)!.data.componentType}
            disabled={executionLocked} onSave={(integration) => { setEdges(current => current.map(item => item.id === edge.id ? { ...item, data: { ...item.data!, integration } } : item)); resetValidation(); }} />
          <div className="property-actions"><button type="button" disabled={executionLocked} onClick={confirmEdge}>Confirmar alterações</button><button type="button" disabled={executionLocked} onClick={cancelEdge}>Cancelar alterações</button></div>
        </>}
        {error && <p className="editor-error" role="alert">{error}</p>}
        {selection && <button type="button" className="danger" disabled={executionLocked} onClick={removeSelected}>Remover selecionado</button>}
      </aside>
    </div>
  );
}
export default function ArchitectureEditor() { return <ReactFlowProvider><EditorCanvas /></ReactFlowProvider>; }
