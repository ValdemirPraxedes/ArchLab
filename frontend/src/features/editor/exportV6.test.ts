import {describe,expect,it} from 'vitest';
import {createArchitectureEdge,createArchitectureNode} from './model';
import {exportArchitectureV6} from './exportV6';
import {prepareArchitectureImportV6} from './migrationV6';
import golden from '../../../../docs/api/examples/v0.6/canonical-export-p0.json?raw';

describe('exportação canônica v0.6',()=>{
  it('faz round-trip de IDs, tipos P0, propriedades e conexões',()=>{
    const service=createArchitectureNode('SERVICE',{x:0,y:0});
    const database=createArchitectureNode('RELATIONAL_DATABASE',{x:1,y:1});
    const edge=createArchitectureEdge(service.id,database.id,'TCP',2);
    const exported=exportArchitectureV6([service,database],[edge]);
    expect(JSON.parse(exported)).toMatchObject({schemaVersion:'0.6'});
    expect(exported).not.toContain('"type": "DATABASE"');
    const imported=prepareArchitectureImportV6(JSON.parse(exported) as unknown);
    expect(imported.databaseIds).toEqual([]);
    expect(imported.nodes.map(node=>node.id)).toEqual([service.id,database.id]);
    expect(imported.edges[0].id).toBe(edge.id);
    expect(imported.nodes[1].data.dataProperties).toEqual(database.data.dataProperties);
  });
  it('recusa exportação de DATABASE sem migração',()=>{
    expect(()=>exportArchitectureV6([createArchitectureNode('DATABASE',{x:0,y:0})],[])).toThrow(/Migre/);
  });
  it('é idempotente sobre a fixture canônica com os 5 tipos P0 (golden compartilhado com backend e OpenAPI)',()=>{
    const imported=prepareArchitectureImportV6(JSON.parse(golden) as unknown);
    expect(imported.databaseIds).toEqual([]);
    const types=new Set(imported.nodes.map(node=>node.data.componentType));
    for (const type of ['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE','ETL_PIPELINE','DATA_TRANSFORMER'])
      expect(types).toContain(type);
    expect(exportArchitectureV6(imported.nodes,imported.edges)).toBe(golden);
  });
});
