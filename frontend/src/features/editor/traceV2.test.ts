import { describe, expect, it } from 'vitest';
import type { SimulationResponseV2 } from '../../api/archlabV2Client';
import { cursorEvents, inFlightMessages, nextCursor, previousCursor, traceStateAt } from './traceV2';

const result = {
  initialState: { components: { apiA:'IDLE', apiB:'IDLE' }, requests: { req:'CREATED' }, exchanges: {} },
  trace: [
    { sequence:4,timestampMs:10,type:'STATE_TRANSITION',entityKind:'COMPONENT',entityId:'apiA',previousState:'WAITING',newState:'PROCESSING',cause:'RESPONSE' },
    { sequence:2,timestampMs:10,type:'MESSAGE_EVENT',action:'SENT',messageId:'msg',requestId:'req',exchangeId:'ex',parentExchangeId:null,connectionId:'edge',sourceId:'apiA',targetId:'apiB',direction:'REQUEST',intent:'API_REQUEST' },
    { sequence:1,timestampMs:10,type:'STATE_TRANSITION',entityKind:'COMPONENT',entityId:'apiA',previousState:'IDLE',newState:'WAITING',cause:'DEPENDENCY' },
    { sequence:3,timestampMs:10,type:'MESSAGE_EVENT',action:'ARRIVED',messageId:'msg',requestId:'req',exchangeId:'ex',parentExchangeId:null,connectionId:'edge',sourceId:'apiA',targetId:'apiB',direction:'REQUEST',intent:'API_REQUEST' },
  ],
} as unknown as SimulationResponseV2;

describe('trace v2 e ordenação determinística', () => {
  it('reconstrói estados por timestamp e sequence, inclusive WAITING', () => {
    expect(traceStateAt(result,{time:10,sequence:1}).components.apiA).toBe('WAITING');
    expect(traceStateAt(result,{time:10,sequence:4}).components.apiA).toBe('PROCESSING');
    expect(traceStateAt(result,{time:0,sequence:0}).components.apiA).toBe('IDLE');
  });
  it('diferencia SENT de ARRIVED sem criar mensagem extra', () => {
    expect(inFlightMessages(result.trace,{time:10,sequence:2})).toHaveLength(1);
    expect(inFlightMessages(result.trace,{time:10,sequence:3})).toHaveLength(0);
  });
  it('avança e volta por sequência em eventos simultâneos', () => {
    const cursor = nextCursor(result.trace,{time:10,sequence:1},100);
    expect(cursor).toEqual({time:10,sequence:2});
    expect(previousCursor(result.trace,cursor)).toEqual({time:10,sequence:1});
    expect(cursorEvents(result.trace,{time:10,sequence:3}).map(e=>e.sequence)).toEqual([1,2,3]);
  });
});
