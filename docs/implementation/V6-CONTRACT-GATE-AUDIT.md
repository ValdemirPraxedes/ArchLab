# v0.6 contract gate audit

Status: open for technical and product review. Base: `origin/develop` at `9703544e`.

## Existing work

- The OpenAPI includes the five P0 component schemas, eleven P0 Action schemas and additive `dataOutcomes`.
- The v0.6 fixtures validate structurally and TypeScript types generate successfully.
- The current Java tests pass, but the Java runtime and frontend still implement v0.5 behavior. Their passing status does not verify v0.6 semantics.

## Blocking contract gaps

1. `Component.oneOf` and its discriminator still admit `DATABASE` in every request using `Component`. The comment says legacy input only, but JSON Schema cannot enforce that comment. Define separate legacy input and canonical creation/export schemas, then test rejection of a new `DATABASE` node.
2. The fixture validator checks only a hard-coded invalid `DB_SELECT`/`KEY_VALUE_STORE` pairing. It does not validate the complete type × Action matrix, directed connection compatibility, or executor roles. These require semantic tests against the Java validator.
3. The v0.6 fixtures cover relational, key-value and ETL shapes, but lack valid document CRUD/FIND, invalid limits, ambiguous legacy migration and `dataOutcomes` examples.
4. The budget and outcome schemas have numerical maxima, but the runtime policy for aggregate records/bytes, queue timeouts, partial bulk writes and the initial virtual dataset is not fixed in an approved normative document.
5. Generated TypeScript types have not been reconciled with hand-written frontend types or Java DTOs. The API may accept a payload that neither client nor runtime can execute.

## Required decisions before Gate A closes

- Approve the canonical-versus-legacy schema boundary and whether `/api/v1/architectures/validate` may receive a legacy document directly or only after explicit client migration.
- Approve the complete capability matrix, directed connection profiles, deterministic ordering, budget defaults and failure atomicity.
- Approve the exact `dataOutcomes` fields and correlations to Action, trace and timeline.

Until those decisions are resolved, V6-01 through V6-05 are partial and V6-06 through V6-24 cannot be marked accepted. Runtime and editor work can be developed on dependent branches, but must not be merged as a conforming v0.6 implementation.
