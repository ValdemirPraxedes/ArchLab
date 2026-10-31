import type { SimulationResponseV2, IntegrationV2 } from '../../api/archlabV2Client';
import { exchangeTree, messageEvents, representationFor } from './messageInspectorV2';
import type { MessageEventV2 } from './messageInspectorV2';

function IntegrationSnapshot({ integration }: { integration: IntegrationV2 }) {
  if (integration.kind === 'HTTP_REST') return <div>
    <h4>HTTP REST — snapshot</h4>
    <p>Requisição: {integration.request?.method ?? 'GET'} {integration.request?.path ?? '/'}</p>
    <p>Resposta HTTP: {integration.response?.statusCode ?? 200} (conteúdo ilustrativo)</p>
    <h5>Headers e body da requisição</h5>
    <pre>{JSON.stringify({ headers: integration.request?.headers ?? {}, body: integration.request?.body ?? '' }, null, 2)}</pre>
    <h5>Headers e body da resposta</h5>
    <pre>{JSON.stringify({ headers: integration.response?.headers ?? {}, body: integration.response?.body ?? '' }, null, 2)}</pre>
  </div>;
  if (integration.kind === 'SOAP_HTTP') return <div>
    <h4>SOAP HTTP — snapshot</h4>
    <p>Operação: {integration.operation ?? ''} · SOAP {integration.soapVersion}</p>
    <p>Status HTTP: {integration.httpStatusCode ?? 200} (ilustrativo)</p>
    <p>SOAP Action: {integration.action ?? ''}</p>
    <h5>Envelope de requisição</h5><pre>{integration.requestEnvelope ?? ''}</pre>
    <h5>Envelope de resposta</h5><pre>{integration.responseEnvelope ?? ''}</pre>
  </div>;
  return <div><h4>DATABASE_QUERY — snapshot</h4>
    <h5>Consulta</h5><pre>{integration.queryText ?? ''}</pre>
    <h5>Resposta</h5><pre>{integration.responseText ?? ''}</pre>
  </div>;
}

function EventDetail({ event, result }: { event: MessageEventV2; result: SimulationResponseV2 }) {
  const snapshot = representationFor(result,event);
  const response = messageEvents(result).filter(item => item.exchangeId === event.exchangeId && item.direction === 'RESPONSE');
  return <section aria-label="Detalhes da mensagem">
    <h3>Mensagem selecionada — {event.direction} / {event.action}</h3>
    <dl>
      {([['messageId',event.messageId],['requestId',event.requestId],['exchangeId',event.exchangeId],['parentExchangeId',event.parentExchangeId ?? '—'],['connectionId',event.connectionId],['Origem',event.sourceId],['Destino',event.targetId],['Direção',event.direction],['Ação',event.action],['Tempo virtual',event.timestampMs+' ms'],['Intenção',event.intent]] as const).map(([key,value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}
    </dl>
    <p>Eventos de resposta correlacionados: {response.length}</p>
    {snapshot ? <IntegrationSnapshot integration={snapshot}/> : <p>Sem representação ilustrativa no snapshot.</p>}
  </section>;
}

type Props = { result: SimulationResponseV2; selectedSequence: number | null; onSelect: (sequence: number) => void };
export function MessageInspectorV2({ result, selectedSequence, onSelect }: Props) {
  const events = messageEvents(result);
  const selected = events.find(event => event.sequence === selectedSequence);
  const { byExchange, roots } = exchangeTree(result);
  const draw = (id: string, visited: Set<string>): React.ReactNode => {
    const item = byExchange.get(id);
    if (!item || visited.has(id)) return null;
    const next = new Set(visited); next.add(id);
    return <li key={id}><strong>Exchange {id}</strong> · {item.events.length} eventos
      <ul>{item.events.map(event => <li key={event.sequence}>
        <button type="button" aria-pressed={selectedSequence === event.sequence} onClick={() => onSelect(event.sequence)}>
          #{event.sequence} · {event.direction} {event.action} · {event.timestampMs} ms
        </button>
      </li>)}</ul>
      {item.children.length > 0 && <ul>{item.children.map(child => draw(child,next))}</ul>}
    </li>;
  };
  return <section aria-label="Inspetor de mensagens v2" className="message-inspector">
    <h3>Exchanges executadas ({byExchange.size})</h3>
    {events.length ? <ul>{roots.map(root => draw(root,new Set()))}</ul> : <p>Não há mensagens executadas no trace.</p>}
    {selected && <EventDetail event={selected} result={result}/>}
    <p>Os dados exibidos são texto literal do snapshot; nenhum corpo HTTP, XML ou SQL é executado.</p>
  </section>;
}
