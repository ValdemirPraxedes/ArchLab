import { describe, expect, it } from 'vitest';
import { allowedIntegrationKinds, defaultIntegration, integrationError, parseHeaders } from './integration';
import { createArchitectureNode, createArchitectureEdge } from './model';
import { toArchitectureV2 } from './architectureV2';

describe('integrações ilustrativas v2', () => {
  it('admite HTTP_REST e SOAP_HTTP entre APIs, mas apenas DATABASE_QUERY para DB', () => {
    expect(allowedIntegrationKinds('API','API')).toEqual(['HTTP_REST', 'SOAP_HTTP']);
    expect(allowedIntegrationKinds('API','DATABASE')).toEqual(['DATABASE_QUERY']);
    expect(allowedIntegrationKinds('CLIENT','DATABASE')).toEqual([]);
  });
  it('aplica defaults tipados e HTTP 500 permanece conteúdo ilustrativo', () => {
    const i = defaultIntegration('HTTP_REST');
    expect(i).toEqual({ kind: 'HTTP_REST', request: { method: 'GET', path: '/' }, response: { statusCode: 200 } });
    if (i.kind !== 'HTTP_REST') throw new Error('tipo incorreto');
    const with500 = { ...i, response: { statusCode: 500, body: 'Falha ilustrativa' } };
    expect(integrationError(with500, 'CLIENT', 'API')).toBeNull();
    expect(integrationError({ ...i, request: { method: 'GET', path: 'x'.repeat(2049) } }, 'CLIENT', 'API')).toMatch(/path/);
    expect(integrationError({ ...i, response: { statusCode: 600 } }, 'CLIENT', 'API')).toMatch(/Status/);
  });
  it('SOAP 1.2 permite Fault ilustrativo e rejeita DTD e limites excedidos', () => {
    const i = defaultIntegration('SOAP_HTTP');
    if (i.kind !== 'SOAP_HTTP') throw new Error('tipo incorreto');
    expect(integrationError({ ...i, httpStatusCode: 500, responseEnvelope: '<Fault>erro</Fault>' }, 'API', 'API')).toBeNull();
    expect(integrationError({ ...i, requestEnvelope: '<!DOCTYPE foo>' }, 'API', 'API')).toMatch(/DTD/);
    expect(integrationError({ ...i, operation: 'x'.repeat(257) }, 'API', 'API')).toMatch(/limite/);
    expect(integrationError({ ...i, soapVersion: '1.1' as '1.2' }, 'API', 'API')).toMatch(/1.2/);
  });
  it('DATABASE_QUERY é texto não executável, respeita 32768 caracteres', () => {
    const i = defaultIntegration('DATABASE_QUERY');
    if (i.kind !== 'DATABASE_QUERY') throw new Error('tipo incorreto');
    expect(integrationError({ ...i, queryText: 'SELECT * FROM produtos' }, 'API', 'DATABASE')).toBeNull();
    expect(integrationError({ ...i, responseText: 'x'.repeat(32769) }, 'API', 'DATABASE')).toMatch(/32768/);
    expect(integrationError(i, 'CLIENT', 'API')).toMatch(/incompatível/);
  });
  it('valida cabeçalhos sem tratar conteúdo como código', () => {
    expect(parseHeaders('Accept: application/json\nX-Trace: abc')).toEqual({ Accept: 'application/json', 'X-Trace': 'abc' });
    expect(parseHeaders('invalid')).toBeNull();
    expect(parseHeaders('X: 1\nX: 2')).toBeNull();
    expect(parseHeaders('X: a'.repeat(500))).toBeNull();
  });
  it('serializa ArchitectureV2 com os discriminadores do OpenAPI', () => {
    const client = createArchitectureNode('CLIENT',{ x: 0,y: 0 });
    const api = createArchitectureNode('API',{ x: 10,y: 0 });
    const db = createArchitectureNode('DATABASE',{ x: 20,y: 0 });
    const e1 = createArchitectureEdge(client.id,api.id,'REST',2);
    const e2 = createArchitectureEdge(api.id,db.id,'TCP',3);
    const arch = toArchitectureV2([client,api,db],[e1,e2]);
    expect(arch.connections.map(c => c.integration.kind)).toEqual(['HTTP_REST','DATABASE_QUERY']);
    expect(arch.components).toHaveLength(3);
    expect(arch.connections[0].latencyMs).toBe(2);
    const invalid = { ...e2, data: { ...e2.data!, integration: defaultIntegration('HTTP_REST') } };
    expect(() => toArchitectureV2([client,api,db],[invalid])).toThrow(/incompatível/);
  });
});
