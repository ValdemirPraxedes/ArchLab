import {describe,expect,it} from 'vitest';
import {prepareArchitectureImportV6} from './migrationV6';

describe('migração DATABASE v0.6',()=>{
  const legacy={components:[
    {id:'svc',type:'SERVICE',displayName:'Service',behaviors:[]},
    {id:'db',type:'DATABASE',displayName:'DB',processingMs:7},
  ],connections:[{id:'edge',sourceId:'svc',targetId:'db',latencyMs:2,integration:{kind:'DATABASE_QUERY'}}]};
  it('preserva entrada original, IDs e conexões na prévia canônica',()=>{
    const prepared=prepareArchitectureImportV6(legacy);
    expect(legacy.components[1].type).toBe('DATABASE');
    expect(prepared.databaseIds).toEqual(['db']);
    expect(prepared.architecture.components[1]).toMatchObject({id:'db',type:'RELATIONAL_DATABASE',
      dataProperties:{readLatencyMs:7,writeLatencyMs:7}});
    expect(prepared.architecture.connections[0]).toMatchObject({id:'edge',sourceId:'svc',targetId:'db'});
    expect(prepared.nodes[1].data.dataProperties?.averageRecordBytes).toBe(512);
  });
  it('bloqueia consulta legada ambígua sem alterar o documento',()=>{
    const ambiguous=structuredClone(legacy);
    ambiguous.connections[0].integration={kind:'DATABASE_QUERY',queryText:'SELECT 1'} as typeof ambiguous.connections[0]['integration'];
    expect(()=>prepareArchitectureImportV6(ambiguous)).toThrow(/revisão semântica/);
    expect(ambiguous.components[1].type).toBe('DATABASE');
  });
});
