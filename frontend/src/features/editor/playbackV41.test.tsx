import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import type {SimulationResponseV2} from '../../api/archlabV2Client';
import {SimulationPlaybackV2} from './SimulationPlaybackV2';

const result={
  execution:{id:'exec-1',mode:'UNIFIED',status:'COMPLETED_WITH_ERRORS',mainStatus:'COMPLETED',responseStatusCode:201,
    requestId:'request-1',entryComponentId:'browser',entryConnectionId:'entry',totalDurationMs:30},
  initialState:{components:{},requests:{},exchanges:{}},
  trace:[
    {type:'ACTION_EVENT',sequence:1,timestampMs:5,action:'COMPLETED',actionId:'respond',componentId:'orders',correlation:{operationId:'exchange-1',profile:'HTTP_REST'}},
    {type:'STATE_TRANSITION',sequence:2,timestampMs:30,entityKind:'DELIVERY',entityId:'delivery-1',previousState:'PROCESSING',newState:'TIMED_OUT',cause:'TIMEOUT',correlation:{operationId:'delivery-1',parentOperationId:'exchange-1',profile:'QUEUE'}},
  ],timeline:[],messageRepresentations:[],resourceStates:[{id:'delivery-1',kind:'DELIVERY',status:'TIMED_OUT'}],
  metrics:{totalDurationMs:30,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]},
} as unknown as SimulationResponseV2;

describe('reprodução mista v0.4.1',()=>{
  it('mostra HTTP 201 antes da falha derivada e correlaciona operações ao avançar',()=>{
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    expect(screen.getByText(/Operação principal:/)).toHaveTextContent('COMPLETED');
    expect(screen.getByText(/Conclusão global:/)).toHaveTextContent('COMPLETED_WITH_ERRORS');
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('Resposta HTTP: 201');
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('exchange-1');
    expect(screen.getByLabelText('Fluxo correlacionado')).not.toHaveTextContent('delivery-1');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('delivery-1');
    expect(screen.getByLabelText('Fluxo correlacionado')).toHaveTextContent('QUEUE');
  });
});
