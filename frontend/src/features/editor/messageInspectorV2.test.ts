import { describe, expect, it } from 'vitest';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { exchangeTree, messageEvents, representationFor } from './messageInspectorV2';

const message = (sequence:number, exchangeId:string, parentExchangeId:string|null, connectionId:string, direction:'REQUEST'|'RESPONSE') => ({
  type:'MESSAGE_EVENT' as const, sequence,timestampMs:sequence*10,action:'SENT' as const,messageId:'msg-'+sequence,requestId:'req',exchangeId,parentExchangeId,connectionId,sourceId:'api1',targetId:'api2',direction,intent:'API_REQUEST' as const,
});
const result = {
  trace: [message(1,'parent',null,'edge-a','REQUEST'),message(2,'child','parent','edge-b','REQUEST'),message(3,'child','parent','edge-b','RESPONSE'),message(4,'parent',null,'edge-a','RESPONSE')],
  messageRepresentations: [
    {connectionId:'edge-a',integration:{kind:'HTTP_REST',request:{method:'GET',path:'/x'},response:{statusCode:500,body:'<script>alert(1)</script>'}}},
    {connectionId:'edge-b',integration:{kind:'SOAP_HTTP',soapVersion:'1.2',requestEnvelope:'<root/>'}},
    {connectionId:'unused',integration:{kind:'DATABASE_QUERY',queryText:'DROP TABLE x',responseText:'ignored'}},
  ],
} as unknown as SimulationResponseV2;

describe('inspetor de mensagens v2', () => {
  it('constrói exchanges pai-filho, correlacionando request/response', () => {
    const tree = exchangeTree(result);
    expect(tree.roots).toEqual(['parent']);
    expect(tree.byExchange.get('parent')?.children).toEqual(['child']);
    expect(tree.byExchange.get('child')?.events.map(e=>e.direction)).toEqual(['REQUEST','RESPONSE']);
  });
  it('lê integração do snapshot e mantém status HTTP 500 ilustrativo', () => {
    const event = messageEvents(result)[0];
    const representation = representationFor(result,event);
    expect(representation?.kind).toBe('HTTP_REST');
    if (representation?.kind === 'HTTP_REST') {
      expect(representation.response?.statusCode).toBe(500);
      expect(representation.response?.body).toContain('<script>');
    }
  });
  it('não inclui representação de conexão sem mensagem executada', () => {
    expect(messageEvents(result).map(e=>e.connectionId)).not.toContain('unused');
    expect(exchangeTree(result).byExchange.size).toBe(2);
  });
});
