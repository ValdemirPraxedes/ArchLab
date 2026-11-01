import { useEffect, useState } from 'react';
import type { IntegrationV2 } from '../../api/archlabV2Client';
import type { ComponentType } from './model';
import { allowedIntegrationKinds, defaultIntegration, integrationError, parseHeaders } from './integration';

type Props = { value: IntegrationV2; source: ComponentType; target: ComponentType; disabled: boolean; onSave: (next: IntegrationV2) => void };
const headerText = (value?: Record<string, string>) => Object.entries(value ?? {}).map(([k,v]) => `${k}: ${v}`).join('\n');
export function IntegrationEditor({ value, source, target, disabled, onSave }: Props) {
  const [draft, setDraft] = useState<IntegrationV2>(value);
  const [requestHeaders, setRequestHeaders] = useState('');
  const [responseHeaders, setResponseHeaders] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    setDraft(value);
    setRequestHeaders(value.kind === 'HTTP_REST' ? headerText(value.request?.headers) : value.kind === 'SOAP_HTTP' ? headerText(value.requestHeaders) : '');
    setResponseHeaders(value.kind === 'HTTP_REST' ? headerText(value.response?.headers) : value.kind === 'SOAP_HTTP' ? headerText(value.responseHeaders) : '');
    setError('');
  }, [value]);
  const update = (change: Record<string, unknown>) => setDraft(d => ({ ...d, ...change }) as IntegrationV2);
  const save = () => {
    const req = parseHeaders(requestHeaders), res = parseHeaders(responseHeaders);
    if (draft.kind !== 'DATABASE_QUERY' && (!req || !res)) { setError('Cabeçalhos inválidos. Use uma linha Nome: valor por cabeçalho (máximo 32).'); return; }
    let next: IntegrationV2 = draft;
    if (draft.kind === 'HTTP_REST') next = { ...draft, request: { ...draft.request!, headers: req! }, response: { ...draft.response!, headers: res! } };
    if (draft.kind === 'SOAP_HTTP') next = { ...draft, requestHeaders: req!, responseHeaders: res! };
    const invalid = integrationError(next, source, target);
    if (invalid) { setError(invalid); return; }
    setError(''); onSave(next);
  };
  return <section aria-label="Integração ilustrativa" className="integration-editor">
    <h3>Integração ilustrativa</h3>
    <p>Conteúdo apenas demonstrativo: nenhuma chamada HTTP, XML ou SQL é executada. Status 500 e SOAP Fault não alteram automaticamente a simulação.</p>
    <label>Tipo de integração<select aria-label="Tipo de integração" disabled={disabled} value={draft.kind}
      onChange={e => { const kind = e.target.value as IntegrationV2['kind']; setDraft(defaultIntegration(kind)); setRequestHeaders(''); setResponseHeaders(''); setError(''); }}>
      {allowedIntegrationKinds(source, target).map(k => <option key={k} value={k}>{k}</option>)}
    </select></label>
    {draft.kind === 'HTTP_REST' && <>
      <label>Método HTTP<select aria-label="Método HTTP" disabled={disabled} value={draft.request?.method ?? 'GET'} onChange={e => update({ request: { ...draft.request!, method: e.target.value as 'GET'|'POST'|'PUT'|'PATCH'|'DELETE' } })}>{['GET','POST','PUT','PATCH','DELETE'].map(m => <option key={m}>{m}</option>)}</select></label>
      <label>Path<input aria-label="Path HTTP" disabled={disabled} maxLength={2048} value={draft.request?.path ?? ''} onChange={e => update({ request: { ...draft.request!, path: e.target.value } })}/></label>
      <label>Body da requisição<textarea aria-label="Body da requisição" disabled={disabled} maxLength={32768} value={draft.request?.body ?? ''} onChange={e => update({ request: { ...draft.request!, body: e.target.value } })}/></label>
      <label>Status HTTP<input aria-label="Status HTTP" type="number" min={100} max={599} disabled={disabled} value={draft.response?.statusCode ?? 200} onChange={e => update({ response: { ...draft.response!, statusCode: Number(e.target.value) } })}/></label>
      <label>Body da resposta<textarea aria-label="Body da resposta" disabled={disabled} maxLength={32768} value={draft.response?.body ?? ''} onChange={e => update({ response: { ...draft.response!, body: e.target.value } })}/></label>
    </>}
    {draft.kind === 'SOAP_HTTP' && <>
      <p>Versão SOAP: 1.2 (HTTP)</p>
      <label>Operação SOAP<input aria-label="Operação SOAP" disabled={disabled} maxLength={256} value={draft.operation ?? ''} onChange={e => update({ operation: e.target.value })}/></label>
      <label>SOAP Action<input aria-label="SOAP Action" disabled={disabled} maxLength={2048} value={draft.action ?? ''} onChange={e => update({ action: e.target.value })}/></label>
      <label>Envelope da requisição<textarea aria-label="Envelope da requisição" disabled={disabled} maxLength={32768} value={draft.requestEnvelope ?? ''} onChange={e => update({ requestEnvelope: e.target.value })}/></label>
      <label>Status SOAP/HTTP<input aria-label="Status SOAP/HTTP" type="number" min={100} max={599} disabled={disabled} value={draft.httpStatusCode ?? 200} onChange={e => update({ httpStatusCode: Number(e.target.value) })}/></label>
      <label>Envelope da resposta<textarea aria-label="Envelope da resposta" disabled={disabled} maxLength={32768} value={draft.responseEnvelope ?? ''} onChange={e => update({ responseEnvelope: e.target.value })}/></label>
    </>}
    {draft.kind === 'DATABASE_QUERY' && <>
      <label>Consulta ilustrativa<textarea aria-label="Consulta ilustrativa" disabled={disabled} maxLength={32768} value={draft.queryText ?? ''} onChange={e => update({ queryText: e.target.value })}/></label>
      <label>Resposta ilustrativa<textarea aria-label="Resposta ilustrativa" disabled={disabled} maxLength={32768} value={draft.responseText ?? ''} onChange={e => update({ responseText: e.target.value })}/></label>
    </>}
    {draft.kind !== 'DATABASE_QUERY' && <>
      <label>Headers da requisição (Nome: valor)<textarea aria-label="Headers da requisição" disabled={disabled} value={requestHeaders} onChange={e => setRequestHeaders(e.target.value)}/></label>
      <label>Headers da resposta (Nome: valor)<textarea aria-label="Headers da resposta" disabled={disabled} value={responseHeaders} onChange={e => setResponseHeaders(e.target.value)}/></label>
    </>}
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={disabled} onClick={save}>Salvar integração</button>
  </section>;
}
