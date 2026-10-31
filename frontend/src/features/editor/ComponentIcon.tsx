import type { ComponentType } from './model';
const icons: Record<ComponentType, string> = { CLIENT: '▣', API: '⬡', DATABASE: '▤' };
export function ComponentIcon({ type }: { type: ComponentType }) {
  return <span aria-hidden="true" className={`component-icon component-icon--${type.toLowerCase()}`}>{icons[type]}</span>;
}
