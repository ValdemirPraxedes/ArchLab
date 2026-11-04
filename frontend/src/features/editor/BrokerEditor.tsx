import { useState } from 'react';
import type { BrokerDestinationV1, BrokerSubscriptionV1 } from '../../api/archlabV1Client';
import type { ArchitectureNode, ArchitectureEdge } from './model';

type Props = {node: ArchitectureNode; nodes: ArchitectureNode[]; edges: ArchitectureEdge[];
  disabled: boolean; onChange: (destinations: BrokerDestinationV1[], subscriptions: BrokerSubscriptionV1[]) => void};
export function BrokerEditor({node,nodes,edges,disabled,onChange}: Props) {
  const destinations = node.data.destinations ?? [];
  const subscriptions = node.data.subscriptions ?? [];
  const [name,setName] = useState('orders');
  const [kind,setKind] = useState<BrokerDestinationV1['kind']>('QUEUE');
  const [destinationName,setDestinationName] = useState('');
  const [connectionId,setConnectionId] = useState('');
  const [error,setError] = useState('');
  const consumerEdges = edges.filter(edge => edge.source === node.id && nodes.some(n => n.id === edge.target && n.data.componentType === 'API'));
  const addDestination = () => {
    const value = name.trim();
    if (!value || value.length > 256 || destinations.some(d => d.name === value) || (kind === 'MQTT' && /[#+]/.test(value))) {
      setError('Destino vazio, duplicado, longo ou com curinga MQTT.'); return;
    }
    onChange([...destinations,{name:value,kind}],subscriptions); setName(''); setError('');
  };
  const addSubscription = () => {
    const destination = destinations.find(d => d.name === destinationName);
    const edge = consumerEdges.find(e => e.id === connectionId);
    if (!destination || !edge || edge.data?.integration?.kind !== destination.kind ||
        subscriptions.some(s => s.destinationName === destinationName && s.connectionId === connectionId)) {
      setError('Selecione um destino e uma conexão Broker → API com o mesmo perfil.'); return;
    }
    onChange(destinations,[...subscriptions,{destinationName,connectionId,consumerComponentId:edge.target}]);
    setConnectionId(''); setError('');
  };
  return <section aria-label="Configuração do Broker">
    <h3>Destinos do Broker</h3>
    <ul>{destinations.map(destination => <li key={destination.name}>{destination.name} · {destination.kind}
      <button type="button" disabled={disabled} onClick={() => onChange(destinations.filter(d => d.name !== destination.name),
        subscriptions.filter(s => s.destinationName !== destination.name))}>Remover destino</button></li>)}</ul>
    <label>Nome do destino<input aria-label="Nome do destino" disabled={disabled} maxLength={256} value={name} onChange={e => setName(e.target.value)}/></label>
    <label>Perfil do destino<select aria-label="Perfil do destino" disabled={disabled} value={kind} onChange={e => setKind(e.target.value as BrokerDestinationV1['kind'])}>
      <option>QUEUE</option><option>TOPIC</option><option>MQTT</option></select></label>
    <button type="button" disabled={disabled || destinations.length >= 100} onClick={addDestination}>Adicionar destino</button>
    <h3>Assinaturas</h3>
    <ul>{subscriptions.map(subscription => <li key={`${subscription.destinationName}-${subscription.connectionId}`}>
      {subscription.destinationName} → {nodes.find(n => n.id === subscription.consumerComponentId)?.data.displayName ?? subscription.consumerComponentId}
      <button type="button" disabled={disabled} onClick={() => onChange(destinations,subscriptions.filter(s => s !== subscription))}>Remover assinatura</button>
    </li>)}</ul>
    <label>Destino da assinatura<select aria-label="Destino da assinatura" disabled={disabled} value={destinationName} onChange={e => setDestinationName(e.target.value)}>
      <option value="">Selecione...</option>{destinations.map(d => <option key={d.name} value={d.name}>{d.name} · {d.kind}</option>)}
    </select></label>
    <label>Conexão de entrega<select aria-label="Conexão de entrega" disabled={disabled} value={connectionId} onChange={e => setConnectionId(e.target.value)}>
      <option value="">Selecione...</option>{consumerEdges.map(edge => <option key={edge.id} value={edge.id}>
        {nodes.find(n => n.id === edge.target)?.data.displayName} · {edge.data?.integration?.kind ?? edge.data?.protocol}
      </option>)}</select></label>
    <button type="button" disabled={disabled || subscriptions.length >= 100} onClick={addSubscription}>Adicionar assinatura</button>
    {error && <p role="alert">{error}</p>}
  </section>;
}
