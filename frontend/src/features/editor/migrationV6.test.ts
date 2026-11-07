import {describe,expect,it} from 'vitest';
import {MigrationAmbiguityErrorV6,prepareArchitectureImportV6} from './migrationV6';

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
  it('lista todas as ambiguidades com path e reason iguais aos do backend',()=>{
    const doc={components:[
      {id:'svc',type:'SERVICE',displayName:'Service',behaviors:[{id:'h',trigger:'REQUEST_RECEIVED',actions:[
        {id:'legacy-call',kind:'CALL',connectionId:'q2'}]}]},
      {id:'db1',type:'DATABASE',displayName:'DB1',processingMs:3},
      {id:'db2',type:'DATABASE',displayName:'DB2',processingMs:3},
    ],connections:[
      {id:'q1',sourceId:'svc',targetId:'db1',latencyMs:1,integration:{kind:'DATABASE_QUERY',queryText:'SELECT 1'}},
      {id:'q2',sourceId:'svc',targetId:'db2',latencyMs:1,integration:{kind:'DATABASE_QUERY'}},
    ]};
    const before=JSON.stringify(doc);
    let error:unknown;
    try { prepareArchitectureImportV6(doc); } catch (cause) { error=cause; }
    expect(error).toBeInstanceOf(MigrationAmbiguityErrorV6);
    const diagnostics=(error as MigrationAmbiguityErrorV6).diagnostics;
    expect(diagnostics.map(d=>[d.componentId,d.path,d.reason])).toEqual([
      ['db1','connections[0].integration','HISTORICAL_QUERY_TEXT'],
      ['db2','components[0].behaviors[0].actions[0]','HISTORICAL_CALL'],
    ]);
    expect(diagnostics.every(d=>d.code==='DATA_MIGRATION_AMBIGUOUS')).toBe(true);
    expect((error as Error).message).toMatch(/revisão semântica/);
    expect(JSON.stringify(doc)).toBe(before);
  });
  it('sinaliza conexão não DATABASE_QUERY como NON_QUERY_CONNECTION',()=>{
    const doc=structuredClone(legacy);
    doc.connections[0].integration={kind:'HTTP_REST'} as typeof doc.connections[0]['integration'];
    expect(()=>prepareArchitectureImportV6(doc)).toThrow(MigrationAmbiguityErrorV6);
    try { prepareArchitectureImportV6(doc); } catch (cause) {
      expect((cause as MigrationAmbiguityErrorV6).diagnostics[0]).toMatchObject({componentId:'db',reason:'NON_QUERY_CONNECTION'});
    }
  });
});
