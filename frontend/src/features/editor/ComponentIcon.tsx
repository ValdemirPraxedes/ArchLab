import { BellRing, Bot, Cable, CalendarClock, Cog, Cpu, Database, DatabaseZap, Globe, HardDrive, Layers3, ListChecks, Monitor, Network, Route, Search, Server, ShieldCheck, Smartphone, Terminal, Waypoints, Webhook, Workflow } from 'lucide-react';
import type { ComponentType } from './model';
const icons: Record<ComponentType, typeof Monitor> = {
  CLIENT: Monitor, WEB_BROWSER: Globe, MOBILE_APP: Smartphone, DESKTOP_APP: Monitor,
  CLI: Terminal, BOT: Bot, EMBEDDED_SYSTEM: Cpu, API: Server, DATABASE: Database, BROKER: Network,
  SERVICE:Server, API_GATEWAY:Route, BFF:Layers3, WORKER:Cog, SCHEDULER:CalendarClock,
  AUTHENTICATION_SERVICE:ShieldCheck, WEBHOOK_RECEIVER:Webhook, ORCHESTRATOR_SERVICE:Workflow, INTEGRATION_BUS:Cable,
  CACHE:DatabaseZap, OBJECT_STORAGE:HardDrive, NOTIFICATION_SERVICE:BellRing, SEARCH_ENGINE:Search,
  SERVICE_REGISTRY:Waypoints, RULES_ENGINE:ListChecks,
};
export function ComponentIcon({ type }: { type: ComponentType }) {
  const Icon = icons[type];
  return <span aria-hidden="true" className={`component-icon component-icon--${type.toLowerCase()}`}><Icon size={22} strokeWidth={1.8} focusable="false" /></span>;
}
