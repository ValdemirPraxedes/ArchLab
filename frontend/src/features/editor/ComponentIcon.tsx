import { Database, Monitor, Server } from 'lucide-react';
import type { ComponentType } from './model';
const icons: Partial<Record<ComponentType, typeof Monitor>> = {CLIENT: Monitor, API: Server, DATABASE: Database};
export function ComponentIcon({ type }: { type: ComponentType }) {
  const Icon = icons[type] ?? Monitor;
  return <span aria-hidden="true" className={`component-icon component-icon--${type.toLowerCase()}`}><Icon size={22} strokeWidth={1.8} focusable="false" /></span>;
}
