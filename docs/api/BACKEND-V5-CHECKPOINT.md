# Backend v0.5 implementation checkpoint

Branch: `feat/v0.5-backend-service-expansion`, based on `origin/develop` at `2979cc1` (approved documentation PR #86).

## Implemented

- Added an explicit, copy-based historical `API` to `SERVICE` migration adapter and tests for preservation of IDs, Behaviors, connections, exact type matching, and atomic failure on malformed input.
- The adapter is currently a library entry point only. It is not wired to an import endpoint or the simulation flow. Do not treat MIG-01 through MIG-07 as complete.

## Validation executed

- The previous "327 tests" count came from stale Surefire XML reports. The Java executable resolved from `PATH` was failing before Maven started. The build was rerun with `JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1` and loopback available. The latest `clean verify` passed: 192 tests, zero failures/errors/skips.
- `frontend/node_modules/.bin/openapi-typescript.cmd docs/api/openapi.yaml -o backend/target/schema-v5.d.ts`: passed.
- `npx --yes @redocly/cli lint docs/api/openapi.yaml`: structurally valid, three warnings (missing license, unused `LegacyApiMigrationV5` schema and unused `v5CacheBranchScenario` example).
- `npm ci` and `npm run validate` in `docs/api/examples/v0.4`: passed all 16 JSON fixtures and three embedded OpenAPI examples, including the v0.5 cache/branch example.
- With a local Spring Boot server on port 18081, `npm run verify:v5-live` passed the v0.5 Cache/BRANCH example against `SimulationResponse`. `ARCHLAB_API_URL=http://127.0.0.1:18081 npm run verify:live` passed 13 positive and three negative historical cases. The first live v0.5 run found a missing timeline phase in OpenAPI; it was corrected and rerun successfully.

## Remaining

BE5-01 needs final contract alignment for all new flows. BE5-02 now has an explicit backend import service with structural revalidation and before/after simulation equivalence; it is not connected to a product import flow, so MIG-01 through MIG-07 are not all complete. BE5-03 through BE5-14 have implementation work in progress in the shared engine; see [BE-V5-IMPLEMENTATION](../architecture/BE-V5-IMPLEMENTATION.md) for architecture and current limits. BE5-15 still needs final acceptance evidence. No PR has been opened and the branch has not been published. The frontend has not been changed.
