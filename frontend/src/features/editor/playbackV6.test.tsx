import {describe,expect,it} from 'vitest';
import {render,screen} from '@testing-library/react';
import {SimulationPlaybackV2} from './SimulationPlaybackV2';
import type {SimulationResponseV2} from '../../api/archlabV2Client';

describe('playback de dados v0.6',()=>{
  it('mostra contadores e causa da operação no relógio virtual',()=>{
    const result=({execution:{id:'exec',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'request-1',
      entryComponentId:'browser',entryConnectionId:'entry',totalDurationMs:10,mode:'UNIFIED'},
      initialState:{components:{},requests:{},exchanges:{}},trace:[],timeline:[],messageRepresentations:[],
      dataOutcomes:[{operationId:'exchange-1',actionId:'put',componentId:'svc',resourceComponentId:'db',
        connectionId:'data',timestampMs:0,status:'SUCCEEDED',resultCode:'STORED',readCount:0,
        writtenCount:1,rejectedCount:0,logicalBytes:128,queueWaitMs:2}],
      metrics:{totalDurationMs:10,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]}}
      ) as unknown as SimulationResponseV2;
    render(<SimulationPlaybackV2 result={result} onStatus={()=>{}}/>);
    const region=screen.getByRole('region',{name:'Resultados de dados v0.6'});
    expect(region).toHaveTextContent('1 de 1');
    expect(region).toHaveTextContent('escritos 1');
    expect(region).toHaveTextContent('bytes 128');
    expect(region).toHaveTextContent('fila 2 ms');
  });
  it('mostra lotes ETL e registros normalizados quando informados',()=>{
    const result=({execution:{id:'exec',status:'COMPLETED',mainStatus:'COMPLETED',requestId:'request-1',
      entryComponentId:'browser',entryConnectionId:'entry',totalDurationMs:10,mode:'UNIFIED'},
      initialState:{components:{},requests:{},exchanges:{}},trace:[],timeline:[],messageRepresentations:[],
      dataOutcomes:[{operationId:'exchange-1',actionId:'load',componentId:'etl',resourceComponentId:'target',
        connectionId:'target-edge',timestampMs:0,status:'SUCCEEDED',resultCode:'LOADED',readCount:0,
        writtenCount:10,rejectedCount:0,logicalBytes:640,batchCount:3,batchSize:5,normalizedCount:10},
        {operationId:'exchange-2',actionId:'extract',componentId:'etl',resourceComponentId:'source',
        connectionId:'source-edge',timestampMs:0,status:'FAILED',resultCode:'LIMIT_EXCEEDED',readCount:0,
        writtenCount:0,rejectedCount:30,logicalBytes:0,batchCount:0,batchSize:5,errorCode:'ETL_BATCH_LIMIT_EXCEEDED'}],
      metrics:{totalDurationMs:10,processingByComponent:[],communicationByConnection:[],waitingByComponent:[],exchanges:[]}}
      ) as unknown as SimulationResponseV2;
    render(<SimulationPlaybackV2 result={result} onStatus={()=>{}}/>);
    const region=screen.getByRole('region',{name:'Resultados de dados v0.6'});
    expect(region).toHaveTextContent('lotes 3 × 5');
    expect(region).toHaveTextContent('normalizados 10');
    expect(region).toHaveTextContent('lotes 0 × 5');
    expect(region).toHaveTextContent('ETL_BATCH_LIMIT_EXCEEDED');
  });
});
