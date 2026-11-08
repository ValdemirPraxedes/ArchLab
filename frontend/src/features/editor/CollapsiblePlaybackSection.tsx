import { ChevronDown, ChevronRight } from 'lucide-react';
import { useState, type ReactNode } from 'react';

/**
 * Collapsible playback panel shared by "Fluxo correlacionado" and "Actions e ramos".
 * Collapsed by default: only the title, the record count and the expand indicator are shown.
 * Expanded: the list is limited to ~160 px with internal scrolling (style.css, .playback-collapsible__list).
 */
export function CollapsiblePlaybackSection({ label, title, listId, listLabel, count, singular, plural, summary, children }: {
  /** Accessible name of the region (kept stable for existing consumers and tests). */
  label: string;
  title: string;
  listId: string;
  listLabel: string;
  count: number;
  singular: string;
  plural: string;
  /** Always-visible content below the header (e.g. the HTTP response line). */
  summary?: ReactNode;
  /** The <li> items, in their original order. */
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return <section aria-label={label} className="playback-collapsible">
    <h3 className="playback-collapsible__title"><button type="button" aria-expanded={open} aria-controls={listId}
      onClick={() => setOpen(current => !current)}>{open ? <ChevronDown size={16} aria-hidden="true" /> : <ChevronRight size={16} aria-hidden="true" />}
      {title} <span className="playback-collapsible__count">({count} {count === 1 ? singular : plural})</span></button></h3>
    {summary}
    {open && <ol id={listId} className="playback-collapsible__list" tabIndex={0} aria-label={listLabel}>{children}</ol>}
  </section>;
}
