import { describe, expect, it } from 'vitest';
import { allowedIntegrationKinds, defaultIntegration, integrationError } from './integration';
import { allowedProtocols, createArchitectureEdge, createArchitectureNode } from './model';
import { toArchitectureV1 } from './architectureV1';

describe('perfis v0.4 no editor', () => {
  it('oferece os perfis da matriz e bloqueia conexões incompatíveis', () => {
    expect(allowedIntegrationKinds('WEB_BROWSER','API')).toContain('WEBSOCKET');
    expect(allowedIntegrationKinds('API','BROKER')).toEqual(['QUEUE','TOPIC','MQTT']);
    expect(allowedIntegrationKinds('BROKER','API')).toEqual(['QUEUE','TOPIC','MQTT']);
    expect(allowedProtocols('BROKER','BROKER')).toEqual([]);
    expect(allowedProtocols('CLIENT','DATABASE')).toEqual([]);
  });
  it('fornece defaults válidos para os oito perfis adicionados', () => {
    for (const kind of ['HTTP_GENERIC','GRAPHQL_HTTP','GRPC_UNARY','COAP','WEBSOCKET'] as const)
      expect(integrationError(defaultIntegration(kind),'BOT','API')).toBeNull();
    for (const kind of ['QUEUE','TOPIC','MQTT'] as const)
      expect(integrationError(defaultIntegration(kind),'BOT','BROKER')).toBeNull();
    expect(integrationError({...defaultIntegration('MQTT'),topic:'orders/+'} as ReturnType<typeof defaultIntegration>,'BOT','BROKER')).toMatch(/curingas/);
  });
  it('serializa Broker, destinos e assinaturas como o contrato', () => {
    const client = createArchitectureNode('BOT',{x:0,y:0});
    const broker = createArchitectureNode('BROKER',{x:1,y:0});
    const api = createArchitectureNode('API',{x:2,y:0});
    const entry = createArchitectureEdge(client.id,broker.id,'QUEUE',10);
    const delivery = createArchitectureEdge(broker.id,api.id,'QUEUE',10);
    broker.data.destinations = [{name:'orders',kind:'QUEUE'}];
    broker.data.subscriptions = [{destinationName:'orders',consumerComponentId:api.id,connectionId:delivery.id}];
    const architecture = toArchitectureV1([client,broker,api],[entry,delivery]);
    expect(architecture.components[1]).toMatchObject({type:'BROKER',destinations:[{name:'orders',kind:'QUEUE'}],subscriptions:[{connectionId:delivery.id}]});
    expect(architecture.connections.map(c => c.integration.kind)).toEqual(['QUEUE','QUEUE']);
  });
});
