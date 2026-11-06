# Backend v0.5 — validation checkpoint

Branch: `feat/v0.5-backend-service-expansion`, based on `origin/develop` at `2979cc1` (approved documentation PR #86).

## Implementation status

BE5-03 through BE5-14 are implemented in the existing `ArchitectureV2` / `UnifiedSimulationV41` path. The fifteen component types, capability validation, typed outcomes, BRANCH, virtual resources, scheduling, notification simulation, synchronous and asynchronous flows, traces, and `/api/v1` integration are covered by unit and integration tests. BE5-15 supplies regression and live HTTP evidence below. Architecture diagrams and behavior details are in [BE-V5-IMPLEMENTATION](../architecture/BE-V5-IMPLEMENTATION.md).

BE5-01 has a validated OpenAPI 3.1 contract and generated TypeScript types. BE5-02 has a copy-based backend migration service that converts historical `API` nodes to `SERVICE`, reports converted IDs, revalidates the graph, and preserves the original on failure. Tests cover two APIs, the Client → API → Broker → API → Database chain, and before/after virtual result equivalence. The later frontend authorization enabled FE5-01: the editor now offers JSON import with the same explicit conversion, backend validation before canvas replacement, and a report of converted IDs. `API` is absent from the palette and new canvas serialization uses `SERVICE`. See [FE-V5-IMPLEMENTATION](../architecture/FE-V5-IMPLEMENTATION.md).

## Validation executed on 2026-11-05

- `backend/.\mvnw.cmd clean verify -q` with Java 25.0.4.1: **198 tests, zero failures, zero errors, zero skipped**. The Java executable on `PATH` was broken, so the command used `JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1` and loopback access for Spring HTTP tests.
- `npx --yes @redocly/cli lint docs/api/openapi.yaml`: valid OpenAPI, two warnings: missing license metadata and unused `LegacyApiMigrationV5` schema. The schema remains available for a future import wire contract; no new versioned endpoint was introduced.
- `frontend/node_modules/.bin/openapi-typescript.cmd docs/api/openapi.yaml -o backend/target/schema-v5.d.ts`: passed. The output is an ignored validation artifact; no frontend source was edited.
- `npm run validate` in `docs/api/examples/v0.4`: 16 JSON fixtures and three embedded OpenAPI examples passed.
- `npm run verify:v5-live` against a local Spring Boot server: eight v0.5 requests passed HTTP response and full `SimulationResponse` schema validation, including Cache/BRANCH, Gateway/BFF, Broker/Worker/Notification, Auth/Rules, virtual resources, Webhook/Orchestrator/Bus, and Scheduler.
- `npm run verify:live` against the same server: 13 historical positive scenarios and three negative cases passed.

The user moved BE5-15 to validation in Trello and authorized FE5-01 through FE5-05. The full delivery is published as [PR #87](https://github.com/ValdemirPraxedes/ArchLab/pull/87) against `develop` for review. No merge was performed.
