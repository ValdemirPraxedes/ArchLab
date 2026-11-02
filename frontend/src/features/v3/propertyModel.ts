import type { PropertyV3, PropertiesV3 } from './types';
export type RuleV3 = { kind: 'INTEGER' | 'BOOLEAN' | 'REFERENCE' | 'LIST'; min?: number; max?: number; expectedTypeId?: string };
const integer: RuleV3 = { kind: 'INTEGER', min: 0, max: 1e12 };
export const COMPONENT_RULES: Record<string, Record<string, RuleV3>> = {
  CLIENT_GENERIC: { available: { kind: 'BOOLEAN' } }, API_SERVICE: { available: { kind: 'BOOLEAN' } },
  DATABASE_GENERIC: { available: { kind: 'BOOLEAN' }, processingMs: integer }, BROKER_GENERIC: { available: { kind: 'BOOLEAN' }, processingMs: integer },
  QUEUE_GENERIC: { available: { kind: 'BOOLEAN' } }, TELEMETRY_GENERIC: { available: { kind: 'BOOLEAN' }, processingMs: integer },
};
export const RESOURCE_RULES: Record<string, { owners: readonly string[]; fields: Record<string, RuleV3> }> = {
  PROCESSING_POOL: { owners: ['API_SERVICE', 'DATABASE_GENERIC', 'TELEMETRY_GENERIC'], fields: { capacity: integer, available: { kind: 'BOOLEAN' }, waitTimeoutMs: integer } },
  POLICY_INSTANCE: { owners: ['API_SERVICE', 'CLIENT_GENERIC'], fields: { timeoutMs: integer, attemptTimeoutMs: integer, maxAttempts: { ...integer, min: 1, max: 100000 }, retryDelayMs: integer } },
  TOPIC: { owners: ['BROKER_GENERIC'], fields: { queueIds: { kind: 'LIST', expectedTypeId: 'MESSAGE_QUEUE' }, acceptanceMs: integer } },
  MESSAGE_QUEUE: { owners: ['QUEUE_GENERIC'], fields: { capacity: integer, ttlMs: integer, consumerEndpointId: { kind: 'REFERENCE', expectedTypeId: 'ENDPOINT' }, consumerOperationId: { kind: 'REFERENCE', expectedTypeId: 'OPERATION' }, consumerConcurrency: { ...integer, min: 1, max: 100000 }, deliveryLatencyMs: integer, ackDelayMs: integer, ackTimeoutMs: integer, maxDeliveryAttempts: { ...integer, min: 1, max: 100000 }, redeliveryDelayMs: integer } },
};
export function setPropertyV3(properties: PropertiesV3, key: string, value?: PropertyV3): PropertiesV3 { const copy = { ...properties }; if (value === undefined) delete copy[key]; else copy[key] = structuredClone(value); return copy; }
/** Shared parser for hierarchical typed values: rejects unknown fields instead of dropping them. */
export function decodePropertyV3(input: unknown, depth = 0): PropertyV3 {
  if (depth > 64 || typeof input !== 'object' || input === null || Array.isArray(input)) throw new Error('Valor tipado inválido.');
  const item = input as Record<string, unknown>;
  const allowed = item.kind === 'REFERENCE' ? ['kind','targetId','expectedTypeId'] : item.kind === 'LIST' ? ['kind','values'] : item.kind === 'OBJECT' ? ['kind','fields'] : ['kind','value'];
  if (Object.keys(item).some(key => !allowed.includes(key))) throw new Error('Campo desconhecido no valor tipado.');
  const id = (v: unknown) => { if (typeof v !== 'string' || !v.trim() || [...v].length > 128) throw new Error('Referência inválida.'); return v; };
  switch (item.kind) {
    case 'STRING': case 'ENUM': if (typeof item.value !== 'string') break; return { kind: item.kind, value: item.value };
    case 'INTEGER': if (typeof item.value !== 'number' || !Number.isSafeInteger(item.value)) break; return { kind: 'INTEGER', value: item.value };
    case 'DECIMAL': if (typeof item.value !== 'string' || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(item.value)) break; return { kind: 'DECIMAL', value: item.value };
    case 'BOOLEAN': if (typeof item.value !== 'boolean') break; return { kind: 'BOOLEAN', value: item.value };
    case 'REFERENCE': return { kind: 'REFERENCE', targetId: id(item.targetId), expectedTypeId: id(item.expectedTypeId) };
    case 'LIST': if (!Array.isArray(item.values)) break; return { kind: 'LIST', values: item.values.map(value => decodePropertyV3(value, depth + 1)) };
    case 'OBJECT': { if (typeof item.fields !== 'object' || item.fields === null || Array.isArray(item.fields)) break; return { kind: 'OBJECT', fields: Object.fromEntries(Object.entries(item.fields).map(([key, value]) => [key, decodePropertyV3(value, depth + 1)])) }; }
  }
  throw new Error('Tipo ou valor incompatível.');
}
