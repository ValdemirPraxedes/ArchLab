import type { ArchitectureNode, ArchitectureEdge } from './model';
import { isClientType } from './model';
import type { AsyncScenarioDraft, AsyncScenarioKind } from './architectureV1';

type Props = {kind: AsyncScenarioKind; nodes: ArchitectureNode[]; edges: ArchitectureEdge[];
  value: AsyncScenarioDraft; onChange: (next: AsyncScenarioDraft) => void; disabled: boolean};
export function AsyncScenarioEditor({kind,nodes,edges,value,onChange,disabled}: Props) {
  const eligible = edges.filter(edge => {
    const source = nodes.find(node => node.id === edge.source);
    const target = nodes.find(node => node.id === edge.target);
    if (!source || !target || !(isClientType(source.data.componentType) || source.data.componentType === 'API')) return false;
    return kind === 'WEBSOCKET_SESSION'
      ? target.data.componentType === 'API' && edge.data?.integration?.kind === 'WEBSOCKET'
      : target.data.componentType === 'BROKER' && ['QUEUE','TOPIC','MQTT'].includes(edge.data?.integration?.kind ?? '');
  });
  const sources = nodes.filter(node => eligible.some(edge => edge.source === node.id));
  const entries = eligible.filter(edge => edge.source === value.entryComponentId);
  const selected = eligible.find(edge => edge.id === value.entryConnectionId);
  const broker = nodes.find(node => node.id === selected?.target && node.data.componentType === 'BROKER');
  const destinations = (broker?.data.destinations ?? []).filter(destination => destination.kind === selected?.data?.integration?.kind);
  return <section className="scenario-editor" aria-label="Editor de cenário">
    <h3>{kind === 'WEBSOCKET_SESSION' ? 'Sessão WebSocket' : 'Publicação no Broker'}</h3>
    <label>Origem inicial<select aria-label="Origem inicial" disabled={disabled} value={value.entryComponentId}
      onChange={e => onChange({...value,entryComponentId:e.target.value,entryConnectionId:'',destinationName:''})}>
      <option value="">Selecione...</option>{sources.map(node => <option key={node.id} value={node.id}>{node.data.displayName ?? node.data.label}</option>)}
    </select></label>
    <label>Conexão de entrada<select aria-label="Conexão de entrada" disabled={disabled || !value.entryComponentId} value={value.entryConnectionId}
      onChange={e => onChange({...value,entryConnectionId:e.target.value,destinationName:''})}>
      <option value="">Selecione...</option>{entries.map(edge => <option key={edge.id} value={edge.id}>
        {nodes.find(node => node.id === edge.target)?.data.displayName} · {edge.data?.integration?.kind}
      </option>)}
    </select></label>
    {kind === 'MESSAGE_PUBLISH' && <>
      <label>Destino do Broker<select aria-label="Destino do Broker" disabled={disabled || !selected} value={value.destinationName}
        onChange={e => onChange({...value,destinationName:e.target.value})}>
        <option value="">Selecione...</option>{destinations.map(destination => <option key={destination.name} value={destination.name}>{destination.name}</option>)}
      </select></label>
      <label>Publicações<input aria-label="Publicações" type="number" min="1" max="100" step="1" disabled={disabled}
        value={value.publishCount} onChange={e => onChange({...value,publishCount:Number(e.target.value)})}/></label>
    </>}
    <p aria-live="polite">{kind === 'WEBSOCKET_SESSION' ? 'Mensagens e fechamento são configurados na conexão WebSocket.'
      : 'Destinos e assinaturas são configurados no Broker. A execução usa o relógio virtual.'}</p>
  </section>;
}
