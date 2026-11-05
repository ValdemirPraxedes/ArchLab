import type { IntegrationV1 } from '../../api/archlabV1Client';
import { isClientType } from './model';
import type { ComponentType } from './model';

export type IntegrationKind = IntegrationV1['kind'];
export function allowedIntegrationKinds(source: ComponentType, target: ComponentType): IntegrationKind[] {
  if ((isClientType(source) && target === 'API') || (source === 'API' && target === 'API'))
    return ['HTTP_REST', 'SOAP_HTTP', 'HTTP_GENERIC', 'GRAPHQL_HTTP', 'GRPC_UNARY', 'COAP', 'WEBSOCKET'];
  if (source === 'API' && target === 'DATABASE') return ['DATABASE_QUERY'];
  if ((isClientType(source) || source === 'API') && target === 'BROKER') return ['QUEUE', 'TOPIC', 'MQTT'];
  if (source === 'BROKER' && target === 'API') return ['QUEUE', 'TOPIC', 'MQTT'];
  return [];
}
export function defaultIntegration(kind: IntegrationKind): IntegrationV1 {
  switch (kind) {
    case 'HTTP_REST': return { kind, request: { method: 'GET', path: '/' }, response: { statusCode: 200 } };
    case 'SOAP_HTTP': return { kind, soapVersion: '1.2', httpStatusCode: 200 };
    case 'DATABASE_QUERY': return { kind, queryText: '', responseText: '' };
    case 'HTTP_GENERIC': return {kind, method: 'GET', path: '/'};
    case 'GRAPHQL_HTTP': return {kind, operationType: 'QUERY', operationName: 'orders'};
    case 'GRPC_UNARY': return {kind, service: 'OrdersService', method: 'GetOrder'};
    case 'COAP': return {kind, method: 'GET', resource: '/orders', mode: 'CON'};
    case 'WEBSOCKET': return {kind, path: '/events', connectTimeoutMs: 5000, idleTimeoutMs: null, closeMode: 'AUTOMATIC', messages: []};
    case 'QUEUE': return {kind, queueName: 'orders'};
    case 'TOPIC': return {kind, topic: 'orders'};
    case 'MQTT': return {kind, topic: 'orders', qos: 0};
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
export function integrationError(value: IntegrationV1, source: ComponentType, target: ComponentType): string | null {
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
  if (value.kind === 'HTTP_GENERIC' && (!value.path || value.path.length > 2048)) return 'Path HTTP inválido.';
  if (value.kind === 'GRAPHQL_HTTP' && (!value.operationName || value.operationName.length > 256)) return 'Operação GraphQL inválida.';
  if (value.kind === 'GRPC_UNARY' && (!value.service || !value.method || value.service.length > 256 || value.method.length > 256)) return 'Service ou método gRPC inválido.';
  if (value.kind === 'COAP' && (!value.resource || value.resource.length > 2048)) return 'Recurso CoAP inválido.';
  if (value.kind === 'WEBSOCKET') {
    if (!value.path || value.path.length > 2048 || !Number.isSafeInteger(value.connectTimeoutMs) || value.connectTimeoutMs < 0 ||
        (value.idleTimeoutMs !== null && (!Number.isSafeInteger(value.idleTimeoutMs) || value.idleTimeoutMs < 0)) || value.messages.length > 100 ||
        value.messages.some(message => !Number.isSafeInteger(message.intervalMs) || message.intervalMs < 0 || (message.payload?.length ?? 0) > 32768)) return 'Configuração WebSocket inválida.';
  }
  if (value.kind === 'QUEUE' && (!value.queueName || value.queueName.length > 256)) return 'Nome da Queue inválido.';
  if ((value.kind === 'TOPIC' || value.kind === 'MQTT') && (!value.topic || value.topic.length > 256 ||
      (value.kind === 'MQTT' && (value.topic.includes('#') || value.topic.includes('+'))))) return 'Tópico inválido; curingas MQTT não são suportados.';
  return null;
}
