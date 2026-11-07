import type { ArchitectureV1 } from '../../api/archlabV1Client';
import { prepareArchitectureImportV5 } from './migrationV5';

export type PreparedImportV6 = ReturnType<typeof prepareArchitectureImportV5> & {
  databaseIds: string[];
  migrationNotes: string[];
};

/** Converts only unambiguous legacy DATABASE nodes, on a copy of the input. */
export function prepareArchitectureImportV6(input:unknown):PreparedImportV6 {
  if (!input || typeof input!=='object' || !('components' in input) || !('connections' in input))
    throw new Error('Documento deve conter components e connections.');
  const source=structuredClone(input) as ArchitectureV1;
  if (!Array.isArray(source.components) || !Array.isArray(source.connections))
    throw new Error('components e connections devem ser listas.');
  const databaseIds:string[]=[];
  const migrationNotes:string[]=[];
  source.components=source.components.map(component=>{
    if (component.type!=='DATABASE') return component;
    const incoming=source.connections.filter(edge=>edge.targetId===component.id);
    const ambiguous=incoming.some(edge=>edge.integration.kind!=='DATABASE_QUERY' ||
      ('queryText' in edge.integration && Boolean(edge.integration.queryText)) ||
      ('responseText' in edge.integration && Boolean(edge.integration.responseText))) ||
      source.components.some(c=>'behaviors' in c && c.behaviors?.some(behavior=>
        behavior.actions.some(action=>action.kind==='CALL' && incoming.some(edge=>edge.id===action.connectionId))));
    if (ambiguous) throw new Error(`DATABASE ${component.id}: conexão ou Action histórica exige revisão semântica; canvas preservado.`);
    const processingMs=component.processingMs;
    databaseIds.push(component.id);
    migrationNotes.push(`${component.id}: processingMs ${processingMs} → latências de leitura/escrita; registros iniciais 0; 512 bytes por registro.`);
    return {id:component.id,displayName:component.displayName,type:'RELATIONAL_DATABASE',dataProperties:{
      initialRecordCount:0,averageRecordBytes:512,readLatencyMs:processingMs,writeLatencyMs:processingMs,
      maxConcurrentOperations:1,connectionPoolSize:1,queueCapacity:10,
    }} as ArchitectureV1['components'][number];
  });
  const prepared=prepareArchitectureImportV5(source);
  return {...prepared,databaseIds,migrationNotes};
}
