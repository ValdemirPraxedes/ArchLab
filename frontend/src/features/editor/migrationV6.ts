import type { ArchitectureV1 } from '../../api/archlabV1Client';
import { prepareArchitectureImportV5 } from './migrationV5';

export type PreparedImportV6 = ReturnType<typeof prepareArchitectureImportV5> & {
  databaseIds: string[];
  migrationNotes: string[];
};

export type MigrationAmbiguityReasonV6 = 'NON_QUERY_CONNECTION' | 'HISTORICAL_CALL_TIMEOUT';
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

/**
 * Lists what still blocks DATABASE → RELATIONAL_DATABASE (product decision 2, 2026-11-07): a connection
 * that is not DATABASE_QUERY, or a CALL with timeoutMs (its deadline covers the network round trip, a
 * data Action deadline does not, so timing would change). queryText/responseText and plain CALLs convert.
 */
export function findMigrationAmbiguitiesV6(source:ArchitectureV1):MigrationDiagnosticV6[] {
  const diagnostics:MigrationDiagnosticV6[]=[];
  for (const component of source.components) {
    if (component.type!=='DATABASE') continue;
    source.connections.forEach((edge,edgeIndex)=>{
      if (edge.targetId!==component.id) return;
      if (edge.integration.kind!=='DATABASE_QUERY')
        diagnostics.push({code:'DATA_MIGRATION_AMBIGUOUS',componentId:component.id,path:`connections[${edgeIndex}].integration`,
          reason:'NON_QUERY_CONNECTION',
          message:`DATABASE ${component.id}: a conexão ${edge.id} (${edge.integration.kind}) não é DATABASE_QUERY; escolha a operação de dados equivalente.`});
      callsTo(source,edge.id).forEach(({actor,actorIndex,behaviorIndex,actionIndex,action})=>{
        if ((action as {timeoutMs?:number}).timeoutMs)
          diagnostics.push({code:'DATA_MIGRATION_AMBIGUOUS',componentId:component.id,
            path:`components[${actorIndex}].behaviors[${behaviorIndex}].actions[${actionIndex}]`,reason:'HISTORICAL_CALL_TIMEOUT',
            message:`DATABASE ${component.id}: o CALL ${action.id} de ${actor.id} tem timeoutMs, que conta a ida e volta pela rede; numa Action de dados o prazo conta só fila + processamento. Remova o timeout ou converta manualmente.`});
      });
    });
  }
  return diagnostics;
}

type BehaviorAction = NonNullable<Extract<ArchitectureV1['components'][number],{behaviors?:unknown}>['behaviors']>[number]['actions'][number];
function callsTo(source:ArchitectureV1,edgeId:string) {
  const found:{actor:ArchitectureV1['components'][number];actorIndex:number;behaviorIndex:number;actionIndex:number;action:BehaviorAction}[]=[];
  source.components.forEach((actor,actorIndex)=>{
    if (!('behaviors' in actor)) return;
    actor.behaviors?.forEach((behavior,behaviorIndex)=>behavior.actions.forEach((action,actionIndex)=>{
      if (action.kind==='CALL' && action.connectionId===edgeId) found.push({actor,actorIndex,behaviorIndex,actionIndex,action});
    }));
  });
  return found;
}

/** Converts legacy DATABASE nodes and their CALLs on a copy of the input; mirrors POST /api/v1/architectures/migrate-v6. */
export function prepareArchitectureImportV6(input:unknown):PreparedImportV6 {
  if (!input || typeof input!=='object' || !('components' in input) || !('connections' in input))
    throw new Error('Documento deve conter components e connections.');
  const source=structuredClone(input) as ArchitectureV1;
  if (!Array.isArray(source.components) || !Array.isArray(source.connections))
    throw new Error('components e connections devem ser listas.');
  const ambiguities=findMigrationAmbiguitiesV6(source);
  if (ambiguities.length) throw new MigrationAmbiguityErrorV6(ambiguities);
  const databaseIds:string[]=[];
  const calls:BehaviorAction[]=[];
  const migrationNotes:string[]=[];
  source.components=source.components.map(component=>{
    if (component.type!=='DATABASE') return component;
    const processingMs=component.processingMs;
    databaseIds.push(component.id);
    for (const edge of source.connections.filter(edge=>edge.targetId===component.id)) {
      const integration=edge.integration as {queryText?:string;responseText?:string};
      if (integration.queryText || integration.responseText)
        migrationNotes.push(`${component.id}: a conexão ${edge.id} mantém queryText/responseText como texto da mensagem (sem semântica executável).`);
      for (const {actor,behaviorIndex,action} of callsTo(source,edge.id)) {
        const behavior=(actor as {behaviors:{id:string}[]}).behaviors[behaviorIndex];
        calls.push(action);
        migrationNotes.push(`${component.id}: ${actor.id}/${behavior.id}/${action.id} CALL → DB_SELECT (collection default; mesmo tempo virtual).`);
      }
    }
    migrationNotes.push(`${component.id}: processingMs ${processingMs} → latências de leitura/escrita; registros iniciais 0; 512 bytes por registro; pool e concorrência 1000 (o legado não tinha fila).`);
    return {id:component.id,displayName:component.displayName,type:'RELATIONAL_DATABASE',dataProperties:{
      initialRecordCount:0,averageRecordBytes:512,readLatencyMs:processingMs,writeLatencyMs:processingMs,
      maxConcurrentOperations:1000,connectionPoolSize:1000,queueCapacity:10,
    }} as ArchitectureV1['components'][number];
  });
  // The copy's CALL objects are rewritten in place only after every DATABASE was accepted.
  for (const call of calls) Object.assign(call,{kind:'DB_SELECT',collection:'default'});
  const prepared=prepareArchitectureImportV5(source);
  return {...prepared,databaseIds,migrationNotes};
}
