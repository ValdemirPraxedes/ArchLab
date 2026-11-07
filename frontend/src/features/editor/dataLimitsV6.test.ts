import {describe,expect,it} from 'vitest';
import openapi from '../../../../docs/api/openapi.yaml?raw';
import {DATA_RESOURCE_LIMITS_V6,DATA_TRANSFORMER_LIMITS_V6,ETL_PIPELINE_LIMITS_V6,dataFieldErrorV6,dataResourceHintV6} from './dataLimitsV6';

/** Reads `name: {type: integer, minimum: X, maximum: Y}` (or a DurationMs $ref) inside one schema block. */
function contractLimits(schema:string,field:string):{min:number;max:number} {
  const start=openapi.indexOf(`\n    ${schema}:\n`);
  expect(start,`schema ${schema}`).toBeGreaterThan(0);
  const next=openapi.slice(start+1).search(/\n {4}[A-Za-z0-9]+:\n/);
  const block=openapi.slice(start,next<0?undefined:start+1+next);
  const line=block.split('\n').find(l=>l.trim().startsWith(`${field}:`));
  expect(line,`${schema}.${field}`).toBeDefined();
  if (line!.includes('DurationMs')) {
    const d=openapi.slice(openapi.indexOf('\n    DurationMs:\n'),openapi.indexOf('\n    DurationMs:\n')+200);
    return {min:Number(/minimum: (\d+)/.exec(d)![1]),max:Number(/maximum: (\d+)/.exec(d)![1])};
  }
  return {min:Number(/minimum: (\d+)/.exec(line!)![1]),max:Number(/maximum: (\d+)/.exec(line!)![1])};
}

describe('limites de dados v0.6 = contrato OpenAPI',()=>{
  it.each(Object.entries(DATA_RESOURCE_LIMITS_V6))('DataResourcePropertiesV6.%s',(field,limit)=>{
    expect(limit).toEqual(contractLimits('DataResourcePropertiesV6',field));
  });
  it.each(Object.entries(ETL_PIPELINE_LIMITS_V6))('EtlPipelineV6.%s',(field,limit)=>{
    expect(limit).toEqual(contractLimits('EtlPipelineV6',field));
  });
  it.each(Object.entries(DATA_TRANSFORMER_LIMITS_V6))('DataTransformerV6.%s',(field,limit)=>{
    expect(limit).toEqual(contractLimits('DataTransformerV6',field));
  });
  it('produz mensagens por campo e dica de concorrência efetiva',()=>{
    expect(dataFieldErrorV6('queueCapacity',10,DATA_RESOURCE_LIMITS_V6.queueCapacity)).toBe('');
    expect(dataFieldErrorV6('connectionPoolSize',0,DATA_RESOURCE_LIMITS_V6.connectionPoolSize)).toMatch(/entre 1 e 1000/);
    expect(dataFieldErrorV6('averageRecordBytes',1.5,DATA_RESOURCE_LIMITS_V6.averageRecordBytes)).toMatch(/inteiro/);
    expect(dataFieldErrorV6('batchSize',Number.NaN,ETL_PIPELINE_LIMITS_V6.batchSize)).toMatch(/obrigatório/);
    expect(dataResourceHintV6(4,2)).toMatch(/Concorrência efetiva: 2/);
    expect(dataResourceHintV6(3,3)).toBe('');
  });
});
