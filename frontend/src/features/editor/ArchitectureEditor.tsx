import { Check, CirclePlay, Link2, RotateCcw, Save, Trash2, X, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, useNodesState, useEdgesState, useReactFlow,
} from '@xyflow/react';
import type { DragEvent } from 'react';
import '@xyflow/react/dist/style.css';
import { ArchitectureNode } from './ArchitectureNode';
import { ComponentIcon } from './ComponentIcon';
import { IntegrationEditor } from './IntegrationEditor';
import { BrokerEditor } from './BrokerEditor';
import { defaultIntegration, allowedIntegrationKinds } from './integration';
import type { IntegrationKind } from './integration';
import { UnifiedScenarioEditorV41 } from './UnifiedScenarioEditorV41';
import { BehaviorEditorV41 } from './BehaviorEditorV41';
import { ServicePropertiesV5 } from './ServicePropertiesV5';
import { emptyEntryV41, toUnifiedRequestV41 } from './unifiedScenarioV41';
import type { EntryDraftV41, SessionDraftV41 } from './unifiedScenarioV41';
import { SimulationPlaybackV2 } from './SimulationPlaybackV2';
import { inFlightMessages, traceStateAt } from './traceV2';
import type { TraceCursor } from './traceV2';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { toArchitectureV1 } from './architectureV1';
import { MigrationAmbiguityErrorV6, prepareArchitectureImportV6 } from './migrationV6';
import { DataNumberFieldV6 } from './DataNumberFieldV6';
import { DATA_RESOURCE_LIMITS_V6, DATA_TRANSFORMER_LIMITS_V6, ETL_PIPELINE_LIMITS_V6, dataResourceHintV6 } from './dataLimitsV6';
import type { PreparedImportV6 } from './migrationV6';
import { exportArchitectureV6 } from './exportV6';
import { archlabV1Client, ArchlabV1HttpError } from '../../api/archlabV1Client';
import type { ValidationIssue } from './validation';
import {
  VISUAL_CLIENT_TYPES, V5_SERVICE_TYPES, V6_DATA_TYPES, V6_PROCESSOR_TYPES, COMPONENT_LABELS, createArchitectureNode, createArchitectureEdge, isProcessorType, isV6DataType,
  isComponentType, isClientType, allowedProtocols, validateConnection, validDuration, removeComponent, nextDisplayName, validDisplayName,
} from './model';
import type { ArchitectureNode as ArchitectureNodeType, ArchitectureEdge, ComponentType, Protocol } from './model';

const DRAG_MIME = 'application/x-archlab-component';
const CANVAS_WIDTH = 2400;
const CANVAS_HEIGHT = 1600;
const kindForProtocol = (value: Protocol): IntegrationKind => value === 'REST' ? 'HTTP_REST' : value === 'SOAP' ? 'SOAP_HTTP' : value === 'TCP' ? 'DATABASE_QUERY' : value;
const protocolForKind = (value: string): Protocol => value === 'HTTP_REST' ? 'REST' : value === 'SOAP_HTTP' ? 'SOAP' : value === 'DATABASE_QUERY' ? 'TCP' : value as Protocol;
const PALETTE_CATEGORIES = [
  { title: 'Clientes', types: VISUAL_CLIENT_TYPES },
  { title: 'Serviços', types: V5_SERVICE_TYPES.filter(type => !['CACHE','OBJECT_STORAGE','SEARCH_ENGINE','SERVICE_REGISTRY','RULES_ENGINE'].includes(type)) },
  { title: 'Dados', types: [...V6_DATA_TYPES,'CACHE','OBJECT_STORAGE','SEARCH_ENGINE','SERVICE_REGISTRY','RULES_ENGINE'] },
  { title: 'Processamento de dados', types: V6_PROCESSOR_TYPES },
  { title: 'Mensageria', types: ['BROKER'] },
] as const satisfies ReadonlyArray<{ title: string; types: readonly ComponentType[] }>;

function EditorCanvas() {
  const [nodes, setNodes, onNodesChange] = useNodesState<ArchitectureNodeType>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<ArchitectureEdge>([]);
  const [entry, setEntry] = useState<EntryDraftV41>(emptyEntryV41);
  const [sessions, setSessions] = useState<SessionDraftV41[]>([]);
  const [selection, setSelection] = useState<{ kind: 'node' | 'edge'; id: string } | null>(null);
  const [targetId, setTargetId] = useState('');
  const [protocol, setProtocol] = useState<Protocol>('REST');
  const [latencyMs, setLatencyMs] = useState('0');
  const [error, setError] = useState('');
  const [actionFeedback, setActionFeedback] = useState('');
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
  const [overrideDraft, setOverrideDraft] = useState<{enabled:boolean; pre:string; post:string} | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const propertiesRef = useRef<HTMLElement>(null);
  const importRef = useRef<HTMLInputElement>(null);
  const [pendingImport,setPendingImport] = useState<PreparedImportV6 | null>(null);
  useEffect(() => {
    if (!selection) return;
    const dismiss = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      if (propertiesRef.current?.contains(target)) return;
      if (target.closest('.react-flow__node, .react-flow__edge')) return;
      setSelection(null);
      setTargetId('');
      setError('');
      setNameDraft('');
      setEdgeDraft(null);
      setOverrideDraft(null);
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
  const initialEdge = edges.find(edge => edge.id === entry.connectionId && edge.source === entry.componentId);
  const path = {components: new Set(initialEdge ? [initialEdge.source,initialEdge.target] : []),
    connections: new Set(initialEdge ? [initialEdge.id] : [])};
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
    setIssues([]); setValidationStatus('idle'); setError(''); setActionFeedback('Execução reiniciada. A arquitetura foi preservada.');
  };
  const importArchitecture = async (file:File) => {
    if (executionLocked) return;
    try {
      const prepared=prepareArchitectureImportV6(JSON.parse(await file.text()) as unknown);
      const validation=await archlabV1Client.validateArchitecture(prepared.architecture);
      if (!validation.valid) {
        setIssues(validation.violations.map(v=>({code:v.code,path:v.path,message:v.message})));
        setValidationStatus('invalid'); setValidationScope('architecture');
        setActionFeedback('Importação cancelada; o canvas original foi preservado.');
        return;
      }
      if (prepared.databaseIds.length) {
        setPendingImport(prepared);
        setActionFeedback('Prévia de migração pronta. Confirme para aplicar ao canvas.');
      } else applyImport(prepared);
    } catch (cause) {
      if (cause instanceof MigrationAmbiguityErrorV6) {
        setIssues(cause.diagnostics.map(d=>({code:d.code,path:d.path,message:`${d.message} (${d.reason})`})));
        setValidationStatus('invalid'); setValidationScope('architecture');
      }
      setActionFeedback(`Importação cancelada; o canvas original foi preservado. ${cause instanceof Error?cause.message:'Arquivo inválido.'}`);
    }
  };
  const exportArchitecture = () => {
    try {
      const content=exportArchitectureV6(nodes,edges);
      const url=URL.createObjectURL(new Blob([content],{type:'application/json'}));
      const link=document.createElement('a');link.href=url;link.download='archlab-v0.6.json';link.click();
      URL.revokeObjectURL(url);
      setActionFeedback('Arquitetura canônica exportada em JSON.');
    } catch(cause) {setActionFeedback(cause instanceof Error?cause.message:'Falha na exportação.');}
  };
  const applyImport = (prepared:PreparedImportV6) => {
    setNodes(prepared.nodes);setEdges(prepared.edges);setEntry(emptyEntryV41());setSessions([]);select(null);
    setPendingImport(null);setIssues([]);setValidationStatus('valid');setValidationScope('architecture');
    setActionFeedback(prepared.databaseIds.length
      ? `Importação concluída. ${prepared.databaseIds.length} DATABASE(s) convertido(s): ${prepared.databaseIds.join(', ')}.`
      : prepared.convertedIds.length
        ? `Importação concluída: ${prepared.convertedIds.length} API convertida(s) para SERVICE. IDs: ${prepared.convertedIds.join(', ')}.`
        : 'Importação concluída sem conversão de API.');
  };
  const simulate = async () => {
    if (executionStatus !== 'READY' && executionStatus !== 'ERROR') return;
    setValidationScope('scenario');
    const requestGeneration = ++generation.current;
    setIssues([]); setValidationStatus('checking'); setExecutionStatus('VALIDATING'); setValidationScope('scenario');
    try {
      const architecture = toArchitectureV1(nodes, edges);
      const request = toUnifiedRequestV41(architecture,entry,sessions,nodes,edges);
      const result = await archlabV1Client.validateScenario(request);
      if (requestGeneration !== generation.current) return;
      if (!result.valid) {
        setIssues(result.violations.map(v => ({ code: v.code, path: v.path, message: v.message })));
        setValidationStatus('invalid'); setExecutionStatus('ERROR'); return;
      }
      setValidationStatus('valid'); setExecutionStatus('EXECUTING');
      const response = await archlabV1Client.simulate(request);
      if (requestGeneration !== generation.current) return;
      setTraceCursor({time:0,sequence:0});
      setSimulationResultV2(response as unknown as SimulationResponseV2);
      setV2PlaybackStatus('PAUSADA');
      setExecutionStatus('COMPLETED');
    } catch (cause) {
      if (requestGeneration !== generation.current) return;
      setSimulationResultV2(null); setExecutionStatus('ERROR'); setValidationStatus('error');
      if (cause instanceof ArchlabV1HttpError) {
        const violations = cause.violations.map(v => ({ code: v.code, path: v.path, message: v.message }));
        setIssues(violations.length ? violations : [{ code: cause.problem?.code ?? 'HTTP_ERROR', path: '', message: `HTTP ${cause.status}: ${cause.problem?.detail ?? cause.message}` }]);
      } else {
        setIssues([{ code: 'NETWORK_ERROR', path: '', message: cause instanceof Error ? cause.message : 'Falha de comunicação com a API v1.' }]);
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
      const architecture = toArchitectureV1(nodes, edges);
      const response = scope === 'architecture'
        ? await archlabV1Client.validateArchitecture(architecture)
        : await archlabV1Client.validateScenario(toUnifiedRequestV41(architecture,entry,sessions,nodes,edges));
      setIssues(response.violations.map(v => ({ code: v.code, path: v.path, message: v.message })));
      setValidationStatus(response.valid ? 'valid' : 'invalid');
    } catch (cause) {
      if (cause instanceof ArchlabV1HttpError) {
        const problem = cause.problem;
        const violations = cause.violations.map(v => ({ code: v.code, path: v.path, message: v.message }));
        setIssues(violations.length ? violations : [{
          code: problem?.code ?? 'HTTP_ERROR', path: '', message: `HTTP ${cause.status}: ${problem?.detail ?? problem?.title ?? cause.message}`,
        }]);
      } else {
        setIssues([{ code: 'NETWORK_ERROR', path: '', message: cause instanceof Error ? cause.message : 'Falha na validação v1.' }]);
      }
      setValidationStatus('error');
    }
  };
  const addNode = useCallback((type: ComponentType, position: { x: number; y: number }) => {
    if (executionLocked || type==='DATABASE' || type==='API') return null;
    resetValidation();
    const newNode = createArchitectureNode(type, position, nextDisplayName(type, nodes));
    setNodes((current) => [...current, newNode]);
    return newNode;
  }, [setNodes, executionLocked, nodes, resetValidation]);
  const select = (next: { kind: 'node' | 'edge'; id: string } | null) => {
    setSelection(next); setTargetId(''); setError(''); setActionFeedback('');
    setNameDraft(next?.kind === 'node' ? (nodes.find((item) => item.id === next.id)?.data.displayName ?? '') : '');
    const selectedEdge = next?.kind === 'edge' ? edges.find((item) => item.id === next.id) : undefined;
    setEdgeDraft(selectedEdge ? { protocol: selectedEdge.data!.protocol, latencyMs: String(selectedEdge.data!.latencyMs) } : null);
    const defaults = nodes.find(n => n.id === selectedEdge?.target)?.data;
    const override = selectedEdge?.data?.apiProcessingOverride;
    setOverrideDraft(selectedEdge ? { enabled: Boolean(override), pre: String(override?.preProcessingMs ?? defaults?.preProcessingMs ?? 0), post: String(override?.postProcessingMs ?? defaults?.postProcessingMs ?? 0) } : null);
  };
  const onPaletteDragStart = (event: DragEvent<HTMLButtonElement>, type: ComponentType) => {
    event.dataTransfer.setData(DRAG_MIME, type); event.dataTransfer.effectAllowed = 'copy';
  };
  const onDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rawType = event.dataTransfer.getData(DRAG_MIME);
    if (!isComponentType(rawType) || rawType === 'API' || rawType === 'DATABASE') return;
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
    resetValidation(); setError(''); setActionFeedback('Nome atualizado no canvas.');
  };
  const updateTime = (field: 'preProcessingMs' | 'postProcessingMs' | 'processingMs', value: string) => {
    if (!node || executionLocked) return;
    const parsed = Number(value);
    if (!value.trim() || !validDuration(parsed)) { setError('Informe um tempo inteiro não negativo.'); return; }
    resetValidation();
    setNodes((current) => current.map((item) => item.id === node.id ? { ...item, data: { ...item.data, [field]: parsed } } : item));
    setError(''); setActionFeedback('Tempo aplicado imediatamente ao componente.');
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
    const kind = kindForProtocol(chosenProtocol);
    setEdges((current) => [...current, { ...created, data: { ...created.data!, integration: defaultIntegration(kind) } }]);
    setError(''); setTargetId(''); setActionFeedback('Conexão criada no canvas.');
  };
  const confirmEdge = () => {
    if (!edge || !edgeDraft || executionLocked) return;
    const ms = Number(edgeDraft.latencyMs);
    const source = nodes.find((n) => n.id === edge.source);
    const target = nodes.find((n) => n.id === edge.target);
    if (!source || !target || !allowedProtocols(source.data.componentType, target.data.componentType).includes(edgeDraft.protocol) || !edgeDraft.latencyMs.trim() || !validDuration(ms)) {
      setError('Protocolo incompatível ou latência inválida.'); return;
    }
    const targetNode = nodes.find(n => n.id === edge.target);
    if (overrideDraft?.enabled && (!targetNode || !['API','SERVICE'].includes(targetNode.data.componentType) || source.data.componentType === 'BROKER')) { setError('Personalização permitida apenas para conexões de entrada em API ou SERVICE.'); return; }
    const overridePre = Number(overrideDraft?.pre), overridePost = Number(overrideDraft?.post);
    if (overrideDraft?.enabled && (![overrideDraft.pre, overrideDraft.post].every(value => value.trim() !== '') || ![overridePre, overridePost].every(value => Number.isSafeInteger(value) && value >= 0 && value <= 1_000_000_000))) { setError('Pré e pós-processamento devem ser inteiros entre 0 e 1000000000 ms.'); return; }
    resetValidation();
    setEdges((current) => current.map((item) => {
      if (item.id !== edge.id) return item;
      const kind = kindForProtocol(edgeDraft.protocol);
      const integration = item.data?.protocol === edgeDraft.protocol || item.data?.integration?.kind === kind ? (item.data?.integration ?? defaultIntegration(kind)) : defaultIntegration(kind);
      return { ...item, label: edgeDraft.protocol, data: { ...item.data!, protocol: edgeDraft.protocol, integration, latencyMs: ms, apiProcessingOverride: overrideDraft?.enabled ? { preProcessingMs: overridePre, postProcessingMs: overridePost } : undefined } };
    }));
    setError(''); setActionFeedback('Alterações da conexão aplicadas.');
  };
  const cancelEdge = () => {
    if (!edge) return;
    setEdgeDraft({ protocol: edge.data!.protocol, latencyMs: String(edge.data!.latencyMs) });
    const defaultApi = nodes.find(n => n.id === edge.target)?.data;
    const override = edge.data?.apiProcessingOverride;
    setOverrideDraft({ enabled: Boolean(override), pre: String(override?.preProcessingMs ?? defaultApi?.preProcessingMs ?? 0), post: String(override?.postProcessingMs ?? defaultApi?.postProcessingMs ?? 0) });
    setError(''); setActionFeedback('Alterações não aplicadas foram descartadas.');
  };
  const removeSelected = () => {
    if (!selection || executionLocked) return;
    resetValidation();
    if (selection.kind === 'node') {
      const result = removeComponent(nodes, edges, selection.id);
      setNodes(result.nodes); setEdges(result.edges);
      if (entry.componentId === selection.id) setEntry(emptyEntryV41());
    } else setEdges((current) => {
      const remaining = current.filter((item) => item.id !== selection.id);
      if (entry.connectionId === selection.id) setEntry(value => ({...value,connectionId:'',operation:''}));
      return remaining;
    });
    select(null);
    setActionFeedback('Elemento removido do canvas.');
  };
  return (
    <div className="editor-layout">
      <aside className="palette" aria-label="Paleta de componentes">
        <h2>Componentes</h2>
        <p>Arraste para o canvas ou clique para adicionar.</p>
        {PALETTE_CATEGORIES.map((category) => <section className="palette-category" aria-label={category.title} key={category.title}>
          <h3>{category.title}</h3>
          {category.types.map((type) => (
          <button key={type} type="button" className="palette-item" draggable={!executionLocked} disabled={executionLocked}
            onDragStart={(event) => { if (!executionLocked) onPaletteDragStart(event, type); }}
            onClick={() => addNode(type, { x: 120 + (nodes.length % 4) * 210, y: 110 + Math.floor(nodes.length / 4) * 120 })}>
            <ComponentIcon type={type} /> {COMPONENT_LABELS[type]}
          </button>
          ))}
        </section>)}
      </aside>
      <section className="editor-main" aria-label="Editor visual">
        <div className="editor-toolbar"><span>{nodes.length} componente(s) · {edges.length} conexão(ões)</span><span>{selection ? 'Elemento selecionado' : 'Selecione um componente'}</span><input ref={importRef} type="file" accept=".json,application/json" aria-label="Arquivo de arquitetura" hidden onChange={event=>{const file=event.target.files?.[0];if(file) void importArchitecture(file);event.target.value='';}}/><button type="button" onClick={()=>importRef.current?.click()} disabled={executionLocked}>Importar arquitetura JSON</button><button type="button" onClick={exportArchitecture} disabled={executionLocked || Boolean(pendingImport)}>Exportar arquitetura JSON</button><button type="button" onClick={() => void validateV2('architecture')} disabled={executionLocked || validationStatus === 'checking'} title="Consultar o backend para validar o grafo"><ShieldCheck size={16} aria-hidden="true" /> Validar arquitetura</button><button type="button" onClick={() => void validateV2('scenario')} disabled={executionLocked || validationStatus === 'checking'} title="Consultar o backend para validar o cenário selecionado"><ShieldCheck size={16} aria-hidden="true" /> Validar cenário</button><button type="button" onClick={simulate} disabled={executionLocked} title="Validar e executar a simulação no backend"><CirclePlay size={16} aria-hidden="true" /> Simular</button>{executionStatus !== 'READY' && <button type="button" onClick={restart} title="Limpar a execução mantendo o canvas"><RotateCcw size={16} aria-hidden="true" /> Reiniciar</button>}</div>
        {pendingImport && <section aria-label="Prévia de migração DATABASE"><h3>Prévia de migração</h3>
          <p>DATABASE → RELATIONAL_DATABASE: {pendingImport.databaseIds.join(', ')}. IDs e conexões preservados.</p>
          <ul>{pendingImport.migrationNotes.map(note=><li key={note}>{note}</li>)}</ul>
          <button type="button" onClick={()=>applyImport(pendingImport)}>Confirmar migração</button>
          <button type="button" onClick={()=>{setPendingImport(null);setActionFeedback('Migração cancelada; canvas preservado.');}}>Cancelar migração</button>
        </section>}
        {actionFeedback && <p className="action-feedback" role="status" aria-live="polite">{actionFeedback}</p>}
        <div className="execution-status" role="status">Execução: {simulationResultV2 ? v2PlaybackStatus : executionStatus}{simulationResultV2 && <span> · Tempo virtual total: {simulationResultV2.execution.totalDurationMs} ms</span>}</div>
        {validationStatus !== 'idle' && <section className="validation-result" aria-label="Resultado da validação" aria-live="polite">
          {validationStatus === 'checking' && <p>Validando arquitetura...</p>}
          {validationStatus === 'valid' && <p>{validationScope === 'architecture' ? 'Arquitetura válida pelo backend.' : 'Cenário válido pelo backend.'}</p>}
          {(validationStatus === 'invalid' || validationStatus === 'error' || executionStatus === 'ERROR') && <><strong>{validationStatus === 'invalid' ? (validationScope === 'architecture' ? 'Arquitetura inválida' : 'Cenário inválido') : 'Erro de validação'}</strong><ul>{issues.map((issue, index) => <li key={index}><strong>{issue.code}</strong>: {issue.message} {issue.path && <small>({issue.path})</small>}</li>)}</ul></>}
        </section>}
        <UnifiedScenarioEditorV41 nodes={nodes} edges={edges} entry={entry} sessions={sessions}
          onEntry={next => {setEntry(next);resetValidation();}} onSessions={next => {setSessions(next);resetValidation();}} disabled={executionLocked}/>
        {simulationResultV2 && <SimulationPlaybackV2 result={simulationResultV2} onStatus={setV2PlaybackStatus} onCursor={setTraceCursor} />}
        <div className="editor-canvas" ref={canvasRef} onDrop={(event) => { if (!executionLocked) onDrop(event); }} onDragOver={(event) => { if (!executionLocked) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }} aria-label="Canvas da arquitetura">
          <ReactFlow nodes={shownNodes} edges={shownEdges} nodeTypes={nodeTypes} onNodesChange={(changes) => { onNodesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select' && (c.type !== 'position' || !c.dragging))) resetValidation(); }} onEdgesChange={(changes) => { onEdgesChange(executionLocked ? changes.filter((c) => c.type === 'select') : changes); if (changes.some((c) => c.type !== 'select')) resetValidation(); }}
            onNodeClick={(_, chosen) => select({ kind: 'node', id: chosen.id })}
            onEdgeClick={(_, chosen) => select({ kind: 'edge', id: chosen.id })}
            onPaneClick={() => select(null)}
            deleteKeyCode={null} nodesDraggable={!executionLocked} edgesReconnectable={false} nodesConnectable={false}
            nodeExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} translateExtent={[[0, 0], [CANVAS_WIDTH, CANVAS_HEIGHT]]} fitView>
            <Background /><Controls /><MiniMap pannable zoomable nodeColor={(n) => isClientType(n.data?.componentType as ComponentType) ? "#1d9eaf" : isProcessorType(n.data?.componentType as ComponentType) ? "#6963d4" : "#cc9440"} nodeStrokeColor="#37506a" nodeStrokeWidth={2} maskColor="rgba(95,126,160,0.18)" />
          </ReactFlow>
        </div>
      </section>
      <aside className="properties" aria-label="Propriedades" ref={propertiesRef}>
        <h2>Propriedades</h2>
        {!selection && <p>Selecione um componente ou conexão no canvas.</p>}
        {node && <>
          <p><strong>{node.data.displayName ?? node.data.label}</strong> · {node.id.slice(0, 8)}</p>
          <label>Nome de apresentação<input aria-label="Nome de apresentação" type="text" maxLength={100} value={nameDraft} disabled={executionLocked} onChange={(event) => setNameDraft(event.target.value)} onBlur={commitName} /></label>
          <button type="button" disabled={executionLocked} onClick={commitName}><Save size={16} aria-hidden="true" /> Salvar nome</button>
          <p className="property-help">Os tempos são aplicados imediatamente ao canvas. Não é necessário salvar.</p>
          {isProcessorType(node.data.componentType) && <>
            {!V6_PROCESSOR_TYPES.some(type=>type===node.data.componentType) && <>
            <label>Pré-processamento (ms)<input aria-label="Pré-processamento (ms)" type="number" min="0" step="1" value={node.data.preProcessingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('preProcessingMs', e.target.value)} /></label>
            <label>Pós-processamento (ms)<input aria-label="Pós-processamento (ms)" type="number" min="0" step="1" value={node.data.postProcessingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('postProcessingMs', e.target.value)} /></label>
            </>}
            <BehaviorEditorV41 node={node} nodes={nodes} edges={edges} behaviors={node.data.behaviors ?? []} disabled={executionLocked}
              onChange={behaviors => {setNodes(current=>current.map(item=>item.id===node.id?{...item,data:{...item.data,behaviors}}:item));resetValidation();}}/>
          </>}
          {node.data.componentType === 'DATABASE' && <label>Processamento (ms)<input aria-label="Processamento (ms)" type="number" min="0" step="1" value={node.data.processingMs ?? 0} disabled={executionLocked} onChange={(e) => updateTime('processingMs', e.target.value)} /></label>}
          {isV6DataType(node.data.componentType) && node.data.dataProperties && <section aria-label="Propriedades de dados"><h3>Dados virtuais</h3>
            {(Object.keys(DATA_RESOURCE_LIMITS_V6) as (keyof typeof DATA_RESOURCE_LIMITS_V6)[]).map(field=><DataNumberFieldV6 key={field} field={field} limit={DATA_RESOURCE_LIMITS_V6[field]} disabled={executionLocked} value={node.data.dataProperties?.[field]} onChange={value=>{setNodes(current=>current.map(item=>item.id===node.id?{...item,data:{...item.data,dataProperties:{...item.data.dataProperties!,[field]:value}}}:item));resetValidation();}}/>)}
            {dataResourceHintV6(node.data.dataProperties.connectionPoolSize,node.data.dataProperties.maxConcurrentOperations) && <p className="field-hint">{dataResourceHintV6(node.data.dataProperties.connectionPoolSize,node.data.dataProperties.maxConcurrentOperations)}</p>}
          </section>}
          {node.data.componentType === 'ETL_PIPELINE' && <section aria-label="Configuração ETL"><h3>Pipeline ETL</h3>
            {(Object.keys(ETL_PIPELINE_LIMITS_V6) as (keyof typeof ETL_PIPELINE_LIMITS_V6)[]).map(field=><DataNumberFieldV6 key={field} field={field} limit={ETL_PIPELINE_LIMITS_V6[field]} disabled={executionLocked} value={node.data[field]} onChange={value=>{setNodes(current=>current.map(item=>item.id===node.id?{...item,data:{...item.data,[field]:value}}:item));resetValidation();}}/>)}
          </section>}
          {node.data.componentType === 'DATA_TRANSFORMER' && <DataNumberFieldV6 field="transformCostPerRecordMs" limit={DATA_TRANSFORMER_LIMITS_V6.transformCostPerRecordMs} disabled={executionLocked} value={node.data.transformCostPerRecordMs} onChange={value=>{setNodes(current=>current.map(item=>item.id===node.id?{...item,data:{...item.data,transformCostPerRecordMs:value}}:item));resetValidation();}}/>}
          {node.data.componentType === 'BROKER' && <BrokerEditor node={node} nodes={nodes} edges={edges} disabled={executionLocked}
            onChange={(destinations,subscriptions) => { setNodes(current => current.map(item => item.id === node.id ? {...item,data:{...item.data,destinations,subscriptions}} : item)); resetValidation(); }}/>}
          <ServicePropertiesV5 node={node} edges={edges} disabled={executionLocked} onChange={patch=>{setNodes(current=>current.map(item=>item.id===node.id?{...item,data:{...item.data,...patch}}:item));resetValidation();}}/>
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
          <button type="button" disabled={executionLocked} onClick={connect}><Link2 size={16} aria-hidden="true" /> Conectar</button>
        </>}
        {edge && <>
          <p>Conexão: {edge.source.slice(0, 8)} → {edge.target.slice(0, 8)}</p>
          <label>Protocolo
            <select aria-label="Editar protocolo" value={edgeDraft?.protocol ?? edge.data?.protocol} disabled={executionLocked} onChange={(e) => setEdgeDraft((draft) => draft ? { ...draft, protocol: e.target.value as Protocol } : null)}>
              {(allowedProtocols(nodes.find((n) => n.id === edge.source)!.data.componentType, nodes.find((n) => n.id === edge.target)!.data.componentType)).map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <label>Latência (ms)<input aria-label="Editar latência (ms)" type="number" min="0" step="1" value={edgeDraft?.latencyMs ?? String(edge.data?.latencyMs ?? 0)} disabled={executionLocked} onChange={(e) => setEdgeDraft((draft) => draft ? { ...draft, latencyMs: e.target.value } : null)} /></label>
          {['API','SERVICE'].includes(nodes.find(n => n.id === edge.target)?.data.componentType ?? '') && nodes.find(n => n.id === edge.source)?.data.componentType !== 'BROKER' && <section className="api-override-editor" aria-label="Processamento da API de destino">
            <h3>Processamento da API de destino</h3>
            <p>{nodes.find(n => n.id === edge.target)?.data.displayName ?? nodes.find(n => n.id === edge.target)?.data.label} · padrão: pré {nodes.find(n => n.id === edge.target)?.data.preProcessingMs ?? 0} ms / pós {nodes.find(n => n.id === edge.target)?.data.postProcessingMs ?? 0} ms</p>
            <label>Configuração de processamento
              <select aria-label="Modo de processamento da conexão" disabled={executionLocked} value={overrideDraft?.enabled ? 'custom' : 'default'} onChange={event => setOverrideDraft(current => current ? { ...current, enabled: event.target.value === 'custom' } : null)}>
                <option value="default">Usar padrão da API</option>
                <option value="custom">Personalizar nesta conexão</option>
              </select>
            </label>
            {overrideDraft?.enabled && <>
              <label>Pré-processamento da conexão (ms)<input aria-label="Pré-processamento da conexão (ms)" type="number" min="0" max="1000000000" step="1" disabled={executionLocked} value={overrideDraft.pre} onChange={event => setOverrideDraft(current => current ? { ...current, pre: event.target.value } : null)}/></label>
              <label>Pós-processamento da conexão (ms)<input aria-label="Pós-processamento da conexão (ms)" type="number" min="0" max="1000000000" step="1" disabled={executionLocked} value={overrideDraft.post} onChange={event => setOverrideDraft(current => current ? { ...current, post: event.target.value } : null)}/></label>
            </>}
          </section>}
          <IntegrationEditor key={edge.id} value={edge.data?.integration ?? defaultIntegration(allowedIntegrationKinds(nodes.find(n => n.id === edge.source)!.data.componentType, nodes.find(n => n.id === edge.target)!.data.componentType)[0])}
            source={nodes.find(n => n.id === edge.source)!.data.componentType} target={nodes.find(n => n.id === edge.target)!.data.componentType}
            disabled={executionLocked} onSave={(integration) => { setEdges(current => current.map(item => item.id === edge.id ? { ...item, label: protocolForKind(integration.kind), data: { ...item.data!, protocol: protocolForKind(integration.kind), integration } } : item)); setEdgeDraft(current => current ? {...current,protocol:protocolForKind(integration.kind)} : current); resetValidation(); }} />
          <p className="property-help">Protocolo, latência e override são aplicados ao clicar em Confirmar alterações. Cancelar restaura os valores atuais.</p>
          <div className="property-actions"><button type="button" disabled={executionLocked} onClick={confirmEdge}><Check size={16} aria-hidden="true" /> Confirmar alterações</button><button type="button" disabled={executionLocked} onClick={cancelEdge}><X size={16} aria-hidden="true" /> Cancelar alterações</button></div>
        </>}
        {error && <p className="editor-error" role="alert">{error}</p>}
        {selection && <button type="button" className="danger" disabled={executionLocked} onClick={removeSelected}><Trash2 size={16} aria-hidden="true" /> Remover selecionado</button>}
      </aside>
    </div>
  );
}
export default function ArchitectureEditor() { return <ReactFlowProvider><EditorCanvas /></ReactFlowProvider>; }
