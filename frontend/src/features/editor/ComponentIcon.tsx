import { Bot, Cpu, Database, Globe, Monitor, Network, Server, Smartphone, Terminal } from 'lucide-react';
import type { ComponentType } from './model';
const icons: Record<ComponentType, typeof Monitor> = {
  CLIENT: Monitor, WEB_BROWSER: Globe, MOBILE_APP: Smartphone, DESKTOP_APP: Monitor,
  CLI: Terminal, BOT: Bot, EMBEDDED_SYSTEM: Cpu, API: Server, DATABASE: Database, BROKER: Network,
};
export function ComponentIcon({ type }: { type: ComponentType }) {
  const Icon = icons[type];
  return <span aria-hidden="true" className={`component-icon component-icon--${type.toLowerCase()}`}><Icon size={22} strokeWidth={1.8} focusable="false" /></span>;
}
