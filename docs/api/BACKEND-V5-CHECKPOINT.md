# Backend v0.5 implementation checkpoint

Branch: `feat/v0.5-backend-service-expansion`, based on `origin/develop` at `2979cc1` (approved documentation PR #86).

## Implemented

- Added an explicit, copy-based historical `API` to `SERVICE` migration adapter and tests for preservation of IDs, Behaviors, connections, exact type matching, and atomic failure on malformed input.
- The adapter is currently a library entry point only. It is not wired to an import endpoint or the simulation flow. Do not treat MIG-01 through MIG-07 as complete.

## Validation executed

- `backend/.\mvnw.cmd clean verify -q`: passed, 327 tests, zero failures/errors/skips.
- `frontend/node_modules/.bin/openapi-typescript.cmd docs/api/openapi.yaml -o backend/target/schema-v5.d.ts`: passed.
- `npx --yes @redocly/cli lint docs/api/openapi.yaml`: structurally valid, three warnings (missing license, unused `LegacyApiMigrationV5` schema and unused `v5CacheBranchScenario` example).
- `npm ci` and `npm run validate` in `docs/api/examples/v0.4`: passed all 16 JSON fixtures and three embedded OpenAPI examples, including the v0.5 cache/branch example.

## Remaining

BE5-01 needs contract alignment with Java and semantic validation of the v0.5 example. BE5-02 needs runtime/API integration, structural revalidation, and MIG-01 through MIG-07 tests. BE5-03 through BE5-15 remain unimplemented. No PR has been opened and the branch has not been published. The frontend has not been changed.
