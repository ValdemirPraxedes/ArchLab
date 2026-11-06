# Frontend v0.5 — service expansion

FE5-01 through FE5-05 extend the existing React Flow editor and `/api/v1` client. New architectures offer `SERVICE` and the other fourteen approved v0.5 service/resource types. `API` remains in the TypeScript model solely to read historical data; it is absent from the palette and blocked in drag-and-drop. The `toArchitectureV1` serializer emits the canonical v0.5 component types and type-specific properties.

The JSON import control copies the source document, converts exact `type: "API"` nodes to `SERVICE`, preserves IDs, Behaviors, Actions, directed connections and integration settings, then calls `/api/v1/architectures/validate`. The canvas changes only after successful validation. Feedback names the converted IDs; invalid imports leave the current canvas untouched. The backend migration service and frontend importer use the same explicit conversion rule. The editor does not currently provide an export/download control; serialization is used for validation and simulation requests.

The palette and canvas share `ComponentIcon` with the Lucide mapping from UI-V5-001. `Layers3` renders the `lucide-layers` SVG class in the installed library. Decorative SVGs use `aria-hidden` and labels remain text. Existing client, Database and Broker icons remain mapped.

Contextual properties edit Gateway routes, Scheduler schedules, Cache TTL and Rules Engine decisions. The Behavior editor exposes v0.5 Actions, output references, BRANCH cases/default actions, schedules, resource operation parameters, notification outcomes and descriptive transformation metadata. Action and trigger choices follow component capabilities; the backend remains the final validator for graph references and result domains.

The existing playback clock displays typed `resourceOutcomes` at their virtual timestamps and Action statuses, including `SKIPPED`. The prior trace, timeline, metrics, main/global status and message inspector remain in the same playback view.

## Validation

- `npm run build`: TypeScript and Vite production build passed.
- `npm test -- --reporter=dot`: 171 tests passed in 28 files, including successful and rejected import UI tests.
- `npm run lint`: passed.
- `ARCHLAB_API_URL=http://127.0.0.1:18081 npm run test:integration`: passed against the local Java 25 backend, including historical architecture validation, 260 ms simulation, duplicate connection rejection and zero-duration regression.

## Scope notes

- JSON import currently accepts an architecture object (`components` and `connections`). It does not import a full simulation request with scenario selection.
- Generic metadata/tag fields in the Action editor use JSON text; invalid intermediate JSON is not applied to the model.
- The Rules Engine property form supports simple string, numeric comparison and comma-separated `IN` values. Scenario attributes are not in the v0.5 wire contract; rules consume earlier Action result references.
