import type { IntegrationV2 } from '../../api/archlabV2Client';
import type { ComponentType } from './model';

export type IntegrationKind = IntegrationV2['kind'];
export function allowedIntegrationKinds(source: ComponentType, target: ComponentType): IntegrationKind[] {
  if ((source === 'CLIENT' && target === 'API') || (source === 'API' && target === 'API')) return ['HTTP_REST', 'SOAP_HTTP'];
  if (source === 'API' && target === 'DATABASE') return ['DATABASE_QUERY'];
  return [];
}
export function defaultIntegration(kind: IntegrationKind): IntegrationV2 {
  switch (kind) {
    case 'HTTP_REST': return { kind, request: { method: 'GET', path: '/' }, response: { statusCode: 200 } };
    case 'SOAP_HTTP': return { kind, soapVersion: '1.2', httpStatusCode: 200 };
    case 'DATABASE_QUERY': return { kind, queryText: '', responseText: '' };
  }
}
export function validHeaders(value: Record<string, string>): boolean {
  return Object.keys(value).length <= 32 && Object.entries(value).every(([k, v]) => k.length > 0 && k.length <= 128 && v.length <= 1024);
}
export function parseHeaders(text: string): Record<string, string> | null {
  if (!text.trim()) return {};
  const headers: Record<string, string> = Object.create(null) as Record<string, string>;
  for (const line of text.split('\n')) {
    const index = line.indexOf(':');
    if (index <= 0) return null;
    const name = line.slice(0, index).trim(), value = line.slice(index + 1).trim();
    if (!name || Object.hasOwn(headers, name)) return null;
    headers[name] = value;
  }
  return validHeaders(headers) ? headers : null;
}
export function integrationError(value: IntegrationV2, source: ComponentType, target: ComponentType): string | null {
  if (!allowedIntegrationKinds(source, target).includes(value.kind)) return 'Integração incompatível com a conexão.';
  if (value.kind === 'HTTP_REST') {
    if (!value.request || !['GET','POST','PUT','PATCH','DELETE'].includes(value.request.method) || !value.request.path || value.request.path.length > 2048) return 'Método ou path REST inválido (máximo 2048).';
    if (!value.response || !Number.isInteger(value.response.statusCode) || value.response.statusCode < 100 || value.response.statusCode > 599) return 'Status HTTP deve estar entre 100 e 599.';
    if ((value.request.body?.length ?? 0) > 32768 || (value.response.body?.length ?? 0) > 32768) return 'Body HTTP excede 32768 caracteres.';
    if ((value.request.headers && !validHeaders(value.request.headers)) || (value.response.headers && !validHeaders(value.response.headers))) return 'Cabeçalhos HTTP inválidos.';
  }
  if (value.kind === 'SOAP_HTTP') {
    if (value.soapVersion !== '1.2') return 'Apenas SOAP 1.2 é permitido.';
    if ((value.operation?.length ?? 0) > 256 || (value.action?.length ?? 0) > 2048 || (value.requestEnvelope?.length ?? 0) > 32768 || (value.responseEnvelope?.length ?? 0) > 32768) return 'Campo SOAP ultrapassa o limite do contrato.';
    if (value.httpStatusCode !== undefined && (!Number.isInteger(value.httpStatusCode) || value.httpStatusCode < 100 || value.httpStatusCode > 599)) return 'Status SOAP/HTTP inválido.';
    if ((value.requestHeaders && !validHeaders(value.requestHeaders)) || (value.responseHeaders && !validHeaders(value.responseHeaders))) return 'Cabeçalhos SOAP inválidos.';
    if ([value.requestEnvelope, value.responseEnvelope].some(x => x && /<!DOCTYPE|<!ENTITY/i.test(x))) return 'Declarações DTD e entidades XML não são permitidas.';
  }
  if (value.kind === 'DATABASE_QUERY' && ((value.queryText?.length ?? 0) > 32768 || (value.responseText?.length ?? 0) > 32768)) return 'Texto de consulta/resposta excede 32768 caracteres.';
  return null;
}
