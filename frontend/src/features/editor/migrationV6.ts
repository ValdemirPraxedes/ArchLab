import type { ArchitectureV1 } from '../../api/archlabV1Client';
import { prepareArchitectureImportV5 } from './migrationV5';

export type PreparedImportV6 = ReturnType<typeof prepareArchitectureImportV5> & {
  databaseIds: string[];
  migrationNotes: string[];
};

export type MigrationAmbiguityReasonV6 = 'HISTORICAL_QUERY_TEXT' | 'NON_QUERY_CONNECTION' | 'HISTORICAL_CALL';
export type MigrationDiagnosticV6 = {
  code:'DATA_MIGRATION_AMBIGUOUS'; componentId:string; path:string; reason:MigrationAmbiguityReasonV6; message:string;
};

/** Every ambiguity found in the document; paths and reasons mirror POST /api/v1/architectures/migrate-v6. */
export class MigrationAmbiguityErrorV6 extends Error {
  readonly diagnostics:MigrationDiagnosticV6[];
  constructor(diagnostics:MigrationDiagnosticV6[]) {
    super(`${diagnostics.length} ambiguidade(s) de migração DATABASE exigem revisão semântica; canvas preservado.`);
    this.name='MigrationAmbiguityErrorV6';
    this.diagnostics=diagnostics;
  }
}

/** Lists the historical semantics that block an automatic DATABASE → RELATIONAL_DATABASE conversion. */
export function findMigrationAmbiguitiesV6(source:ArchitectureV1):MigrationDiagnosticV6[] {
  const diagnostics:MigrationDiagnosticV6[]=[];
  source.components.forEach(component=>{
    if (component.type!=='DATABASE') return;
    source.connections.forEach((edge,edgeIndex)=>{
      if (edge.targetId!==component.id) return;
      const integration=edge.integration as {kind:string;queryText?:string;responseText?:string};
      if (integration.kind!=='DATABASE_QUERY' || integration.queryText || integration.responseText) {
        const reason=integration.kind!=='DATABASE_QUERY'?'NON_QUERY_CONNECTION':'HISTORICAL_QUERY_TEXT';
        diagnostics.push({code:'DATA_MIGRATION_AMBIGUOUS',componentId:component.id,
          path:`connections[${edgeIndex}].integration`,reason,
          message:reason==='NON_QUERY_CONNECTION'
            ?`DATABASE ${component.id}: a conexão ${edge.id} (${integration.kind}) não é DATABASE_QUERY; escolha a operação de dados equivalente.`
            :`DATABASE ${component.id}: a conexão ${edge.id} tem queryText/responseText histórico; substitua por uma Action DB_* declarativa.`});
      }
      source.components.forEach((actor,actorIndex)=>{
        if (!('behaviors' in actor)) return;
        actor.behaviors?.forEach((behavior,behaviorIndex)=>behavior.actions.forEach((action,actionIndex)=>{
          if (action.kind==='CALL' && action.connectionId===edge.id)
            diagnostics.push({code:'DATA_MIGRATION_AMBIGUOUS',componentId:component.id,
              path:`components[${actorIndex}].behaviors[${behaviorIndex}].actions[${actionIndex}]`,reason:'HISTORICAL_CALL',
              message:`DATABASE ${component.id}: a Action CALL ${action.id} de ${actor.id} não tem equivalente DB_* inequívoco; escolha a operação.`});
        }));
      });
    });
  });
  return diagnostics;
}

/** Converts only unambiguous legacy DATABASE nodes, on a copy of the input. */
export function prepareArchitectureImportV6(input:unknown):PreparedImportV6 {
  if (!input || typeof input!=='object' || !('components' in input) || !('connections' in input))
    throw new Error('Documento deve conter components e connections.');
  const source=structuredClone(input) as ArchitectureV1;
  if (!Array.isArray(source.components) || !Array.isArray(source.connections))
    throw new Error('components e connections devem ser listas.');
  const ambiguities=findMigrationAmbiguitiesV6(source);
  if (ambiguities.length) throw new MigrationAmbiguityErrorV6(ambiguities);
  const databaseIds:string[]=[];
  const migrationNotes:string[]=[];
  source.components=source.components.map(component=>{
    if (component.type!=='DATABASE') return component;
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
