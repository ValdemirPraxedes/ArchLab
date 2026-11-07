import {describe,expect,it} from 'vitest';
import {MigrationAmbiguityErrorV6,prepareArchitectureImportV6} from './migrationV6';
import ct607 from '../../../../docs/api/examples/v0.6/ct/CT-6-07.json?raw';

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
  it('converte queryText histórico mantendo o texto na conexão (decisão 2)',()=>{
    const withText=structuredClone(legacy);
    withText.connections[0].integration={kind:'DATABASE_QUERY',queryText:'SELECT 1'} as typeof withText.connections[0]['integration'];
    const prepared=prepareArchitectureImportV6(withText);
    expect(prepared.architecture.connections[0].integration).toMatchObject({kind:'DATABASE_QUERY',queryText:'SELECT 1'});
    expect(prepared.migrationNotes.join(' ')).toMatch(/queryText\/responseText/);
    expect(withText.components[1].type).toBe('DATABASE');
  });
  it('lista as ambiguidades restantes com path e reason iguais aos do backend',()=>{
    const doc={components:[
      {id:'svc',type:'SERVICE',displayName:'Service',behaviors:[{id:'h',trigger:'REQUEST_RECEIVED',actions:[
        {id:'legacy-call',kind:'CALL',connectionId:'q2',timeoutMs:5}]}]},
      {id:'db1',type:'DATABASE',displayName:'DB1',processingMs:3},
      {id:'db2',type:'DATABASE',displayName:'DB2',processingMs:3},
    ],connections:[
      {id:'q1',sourceId:'svc',targetId:'db1',latencyMs:1,integration:{kind:'HTTP_REST',request:{method:'GET',path:'/'},response:{statusCode:200}}},
      {id:'q2',sourceId:'svc',targetId:'db2',latencyMs:1,integration:{kind:'DATABASE_QUERY'}},
    ]};
    const before=JSON.stringify(doc);
    let error:unknown;
    try { prepareArchitectureImportV6(doc); } catch (cause) { error=cause; }
    expect(error).toBeInstanceOf(MigrationAmbiguityErrorV6);
    const diagnostics=(error as MigrationAmbiguityErrorV6).diagnostics;
    expect(diagnostics.map(d=>[d.componentId,d.path,d.reason])).toEqual([
      ['db1','connections[0].integration','NON_QUERY_CONNECTION'],
      ['db2','components[0].behaviors[0].actions[0]','HISTORICAL_CALL_TIMEOUT'],
    ]);
    expect(diagnostics.every(d=>d.code==='DATA_MIGRATION_AMBIGUOUS')).toBe(true);
    expect((error as Error).message).toMatch(/revisão semântica/);
    expect(JSON.stringify(doc)).toBe(before);
  });
  it('converte CALL histórico em DB_SELECT igual ao backend (fixture CT-6-07)',()=>{
    const fixture=JSON.parse(ct607) as {cases:{name:string;request:unknown;expect:{fields:Record<string,unknown>}}[]};
    const testCase=fixture.cases.find(c=>c.name==='legacy-call-and-query-text-convert')!;
    const before=JSON.stringify(testCase.request);
    const prepared=prepareArchitectureImportV6(testCase.request);
    expect(JSON.stringify(testCase.request)).toBe(before);
    for (const [pointer,expected] of Object.entries(testCase.expect.fields)) {
      const path=pointer.replace(/^\/document\//,'').split('/');
      const actual=path.reduce<unknown>((node,key)=>(node as Record<string,unknown>)?.[key],prepared.architecture);
      expect(actual,pointer).toEqual(expected);
    }
    expect(prepared.migrationNotes.join(' ')).toMatch(/service\/handle\/load-order CALL → DB_SELECT/);
    expect(prepared.nodes.find(node=>node.id==='service')?.data.behaviors?.[0].actions[0].kind).toBe('DB_SELECT');
  });
});
