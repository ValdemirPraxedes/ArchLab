import type { ComponentV3 } from './types';
export const COMPONENTS_V3 = [
  { typeId: 'CLIENT_GENERIC', label: 'Client', icon: 'client', color: '#087f9c' },
  { typeId: 'API_SERVICE', label: 'API', icon: 'api', color: '#6556be' },
  { typeId: 'DATABASE_GENERIC', label: 'Database', icon: 'database', color: '#987122' },
  { typeId: 'BROKER_GENERIC', label: 'Broker', icon: 'broker', color: '#a84479' },
  { typeId: 'QUEUE_GENERIC', label: 'Queue', icon: 'queue', color: '#a4541e' },
  { typeId: 'TELEMETRY_GENERIC', label: 'Telemetry', icon: 'telemetry', color: '#147953' },
] as const;
export type ComponentTypeV3 = typeof COMPONENTS_V3[number]['typeId'];
export function componentTypeV3(value: string): value is ComponentTypeV3 { return COMPONENTS_V3.some(item => item.typeId === value); }
export function componentLabelV3(typeId: string) { return COMPONENTS_V3.find(item => item.typeId === typeId)?.label ?? typeId; }
export function componentDefaultsV3(id: string, typeId: ComponentTypeV3, name: string): ComponentV3 {
  return { id, typeId, displayName: name, properties: ['DATABASE_GENERIC', 'BROKER_GENERIC', 'TELEMETRY_GENERIC'].includes(typeId) ? { available: { kind: 'BOOLEAN', value: true }, processingMs: { kind: 'INTEGER', value: 0 } } : { available: { kind: 'BOOLEAN', value: true } } };
}
