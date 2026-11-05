import type { ArchitectureEdge, ArchitectureNode } from './model';
import { isClientType } from './model';
import type { EntryDraftV41, SessionDraftV41 } from './unifiedScenarioV41';
import { operationForEntry } from './unifiedScenarioV41';

type Props = {nodes: ArchitectureNode[]; edges: ArchitectureEdge[]; entry: EntryDraftV41;
  sessions: SessionDraftV41[]; onEntry: (entry: EntryDraftV41) => void;
  onSessions: (sessions: SessionDraftV41[]) => void; disabled: boolean};
export function UnifiedScenarioEditorV41({nodes,edges,entry,sessions,onEntry,onSessions,disabled}: Props) {
  const sources = nodes.filter(node => (isClientType(node.data.componentType) || node.data.componentType === 'API')
    && edges.some(edge => edge.source === node.id));
  const outgoing = edges.filter(edge => edge.source === entry.componentId);
  const wsEdges = edges.filter(edge => edge.data?.integration?.kind === 'WEBSOCKET');
  return <section className="scenario-editor" aria-label="Entrada da simulação">
    <h3>Entrada da simulação</h3>
    <label>Componente inicial<select aria-label="Componente inicial" disabled={disabled} value={entry.componentId}
      onChange={e => onEntry({componentId:e.target.value,connectionId:'',operation:''})}>
      <option value="">Selecione...</option>{sources.map(node => <option key={node.id} value={node.id}>{node.data.displayName ?? node.data.label}</option>)}
    </select></label>
    <label>Conexão inicial<select aria-label="Conexão inicial" disabled={disabled || !entry.componentId} value={entry.connectionId}
      onChange={e => { const edge=edges.find(item => item.id===e.target.value); onEntry({...entry,connectionId:e.target.value,operation:edge ? operationForEntry(edge) : ''}); }}>
      <option value="">Selecione...</option>{outgoing.map(edge => <option key={edge.id} value={edge.id}>{nodes.find(node => node.id===edge.target)?.data.displayName ?? edge.target} · {edge.data?.integration?.kind}</option>)}
    </select></label>
    {entry.connectionId && <label>Operação inicial<input aria-label="Operação inicial" disabled={disabled} value={entry.operation}
      onChange={e => onEntry({...entry,operation:e.target.value})} placeholder="Conforme a conexão" /></label>}
    {wsEdges.length > 0 && <details><summary>Sessões WebSocket antes da entrada</summary>
      <p>Abra uma sessão quando uma ação WS_SEND precisar dela antes da operação inicial.</p>
      {sessions.map((session,index) => <div key={index}>
        <label>Conexão WebSocket<select aria-label={`Conexão da sessão ${index+1}`} disabled={disabled} value={session.connectionId}
          onChange={e => onSessions(sessions.map((item,i) => i===index ? {...item,connectionId:e.target.value} : item))}>
          <option value="">Selecione...</option>{wsEdges.map(edge => <option key={edge.id} value={edge.id}>{edge.id}</option>)}
        </select></label>
        <label>Alias da sessão<input aria-label={`Alias da sessão ${index+1}`} disabled={disabled} value={session.sessionAlias}
          onChange={e => onSessions(sessions.map((item,i) => i===index ? {...item,sessionAlias:e.target.value} : item))}/></label>
        <label><input type="checkbox" disabled={disabled} checked={session.autoCloseAtEnd}
          onChange={e => onSessions(sessions.map((item,i) => i===index ? {...item,autoCloseAtEnd:e.target.checked} : item))}/> Fechar automaticamente</label>
        <button type="button" disabled={disabled} onClick={() => onSessions(sessions.filter((_,i) => i!==index))}>Remover sessão</button>
      </div>)}
      <button type="button" disabled={disabled} onClick={() => onSessions([...sessions,{connectionId:wsEdges[0].id,sessionAlias:`session-${sessions.length+1}`,autoCloseAtEnd:true}])}>Adicionar sessão</button>
    </details>}
  </section>;
}
