# Backend v0.5 — validation checkpoint

Branch: `feat/v0.5-backend-service-expansion`, based on `origin/develop` at `2979cc1` (approved documentation PR #86).

## Implementation status

BE5-03 through BE5-14 are implemented in the existing `ArchitectureV2` / `UnifiedSimulationV41` path. The fifteen component types, capability validation, typed outcomes, BRANCH, virtual resources, scheduling, notification simulation, synchronous and asynchronous flows, traces, and `/api/v1` integration are covered by unit and integration tests. BE5-15 supplies regression and live HTTP evidence below. Architecture diagrams and behavior details are in [BE-V5-IMPLEMENTATION](../architecture/BE-V5-IMPLEMENTATION.md).

BE5-01 has a validated OpenAPI 3.1 contract and generated TypeScript types. BE5-02 has a copy-based backend migration service that converts historical `API` nodes to `SERVICE`, reports converted IDs, revalidates the graph, and preserves the original on failure. Tests cover two APIs, the Client → API → Broker → API → Database chain, and before/after virtual result equivalence. It is a Java service entry point; the editor's import flow is outside this backend branch. In particular, MIG-04 requires the frontend palette and serializer to stop offering `API`. The request explicitly prohibits frontend changes, so MIG-04 and the full product import path remain open.

## Validation executed on 2026-11-05

- `backend/.\mvnw.cmd clean verify -q` with Java 25.0.4.1: **198 tests, zero failures, zero errors, zero skipped**. The Java executable on `PATH` was broken, so the command used `JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1` and loopback access for Spring HTTP tests.
- `npx --yes @redocly/cli lint docs/api/openapi.yaml`: valid OpenAPI, two warnings: missing license metadata and unused `LegacyApiMigrationV5` schema. The schema remains available for a future import wire contract; no new versioned endpoint was introduced.
- `frontend/node_modules/.bin/openapi-typescript.cmd docs/api/openapi.yaml -o backend/target/schema-v5.d.ts`: passed. The output is an ignored validation artifact; no frontend source was edited.
- `npm run validate` in `docs/api/examples/v0.4`: 16 JSON fixtures and three embedded OpenAPI examples passed.
- `npm run verify:v5-live` against a local Spring Boot server: eight v0.5 requests passed HTTP response and full `SimulationResponse` schema validation, including Cache/BRANCH, Gateway/BFF, Broker/Worker/Notification, Auth/Rules, virtual resources, Webhook/Orchestrator/Bus, and Scheduler.
- `npm run verify:live` against the same server: 13 historical positive scenarios and three negative cases passed.

The full delivery PR remains unopened because the original request permits only one PR after all BE5-01 through BE5-15 criteria are complete, and MIG-04 is a frontend criterion excluded from this delivery. No merge was performed.
