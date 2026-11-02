import { Activity, Database, Layers, Monitor, Network, Server } from 'lucide-react';
export function ComponentIconV3({ typeId }: { typeId: string }) {
  const Icon = typeId === 'CLIENT_GENERIC' ? Monitor : typeId === 'API_SERVICE' ? Server : typeId === 'DATABASE_GENERIC' ? Database : typeId === 'BROKER_GENERIC' ? Network : typeId === 'QUEUE_GENERIC' ? Layers : Activity;
  return <Icon size={20} aria-hidden="true" focusable="false" />;
}
