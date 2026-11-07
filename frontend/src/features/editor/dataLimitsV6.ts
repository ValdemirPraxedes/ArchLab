/**
 * Bounds of the v0.6 data properties, identical to docs/api/openapi.yaml
 * (DataResourcePropertiesV6, EtlPipelineV6, DataTransformerV6, DurationMs).
 * dataLimitsV6.test.ts fails if any value drifts from the contract.
 * Local validation gives immediate feedback; the backend remains the authority.
 */
export type DataLimitV6 = {min:number; max:number};

const DURATION_MS:DataLimitV6={min:0,max:1_000_000_000};

export const DATA_RESOURCE_LIMITS_V6 = {
  initialRecordCount:{min:0,max:1_000_000},
  averageRecordBytes:{min:1,max:1_048_576},
  readLatencyMs:DURATION_MS,
  writeLatencyMs:DURATION_MS,
  maxConcurrentOperations:{min:1,max:1000},
  connectionPoolSize:{min:1,max:1000},
  queueCapacity:{min:0,max:10_000},
} as const satisfies Record<string,DataLimitV6>;

export const ETL_PIPELINE_LIMITS_V6 = {
  batchSize:{min:1,max:100_000},
  maxBatches:{min:1,max:1000},
} as const satisfies Record<string,DataLimitV6>;

export const DATA_TRANSFORMER_LIMITS_V6 = {
  transformCostPerRecordMs:DURATION_MS,
} as const satisfies Record<string,DataLimitV6>;

/** Human-readable error for a field value, or '' when it satisfies the contract. */
export function dataFieldErrorV6(field:string,value:number|undefined,limit:DataLimitV6):string {
  if (value===undefined || Number.isNaN(value)) return `${field} é obrigatório.`;
  if (!Number.isInteger(value)) return `${field} deve ser inteiro.`;
  if (value<limit.min || value>limit.max) return `${field} deve estar entre ${limit.min} e ${limit.max}.`;
  return '';
}

/** Cross-field rule enforced by the engine: the effective concurrency is min(pool, maxConcurrent). */
export function dataResourceHintV6(pool:number|undefined,maxConcurrent:number|undefined):string {
  if (pool===undefined || maxConcurrent===undefined || pool===maxConcurrent) return '';
  return `Concorrência efetiva: ${Math.min(pool,maxConcurrent)} (menor entre connectionPoolSize e maxConcurrentOperations).`;
}
