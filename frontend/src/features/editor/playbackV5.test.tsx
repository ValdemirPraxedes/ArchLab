import {describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen} from '@testing-library/react';
import type {SimulationResponseV2} from '../../api/archlabV2Client';
import {SimulationPlaybackV2} from './SimulationPlaybackV2';

describe('reprodução v0.5',()=>{
  it('mostra resultado tipado e ramo SKIPPED no relógio virtual',()=>{
    const result={execution:{id:'exec',mode:'UNIFIED',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'req',entryComponentId:'client',entryConnectionId:'edge',totalDurationMs:10},initialState:{components:{},requests:{},exchanges:{}},trace:[
      {type:'ACTION_EVENT',sequence:1,timestampMs:5,actionId:'lookup',componentId:'service',operationId:'req',status:'COMPLETED',cause:'CACHE_GET'},
      {type:'ACTION_EVENT',sequence:2,timestampMs:6,actionId:'hit-response',componentId:'service',operationId:'req',status:'SKIPPED',cause:'BRANCH'},
    ],resourceOutcomes:[{actionId:'lookup',status:'SUCCEEDED',resultCode:'MISS',timestampMs:5,componentId:'cache',operationId:'req',details:{key:'orders'}}],timeline:[],messageRepresentations:[],resourceStates:[],metrics:{totalDurationMs:10,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]}} as unknown as SimulationResponseV2;
    render(<SimulationPlaybackV2 result={result} onStatus={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button',{name:/Passo a passo/}));
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Resultados dos recursos v0.5')).toHaveTextContent('MISS');
    fireEvent.click(screen.getByRole('button',{name:/Próximo/}));
    expect(screen.getByLabelText('Actions e ramos v0.5')).toHaveTextContent('SKIPPED');
  });
});
