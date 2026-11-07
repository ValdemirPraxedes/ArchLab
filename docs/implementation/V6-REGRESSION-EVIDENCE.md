# v0.6 historical API regression checkpoint

On 2026-11-06 (America/Sao_Paulo), the backend from `origin/develop` after PR #98 was started locally on port 18081. The existing live verifiers were run against `POST /api/v1/simulations`.

| Verifier | Result |
| --- | --- |
| `docs/api/examples/v0.4/verify-live.cjs` | 13 positive fixtures returned HTTP 200 and matched `SimulationResponse`; 3 negative fixtures returned expected 400/422 problem responses. The verifier also checked trace order and virtual clock bounds. |
| `docs/api/examples/v0.4/verify-v5-live.cjs` | 8 v0.5 cases returned HTTP 200 and matched `SimulationResponse`, including Cache miss/branch, gateway/BFF, Worker, authorization rules, resources, orchestration and Scheduler. |

The v0.4 `database-query.json` fixture completed in 60 virtual ms, confirming that the historical `DATABASE_QUERY` path remains executable. The new migration preview deliberately blocks historical `CALL` to `DATABASE` until operation equivalence can be specified; these are separate compatibility contracts.

Commands used:

```text
cd backend && .\mvnw.cmd -pl archlab-api -am install -DskipTests
cd backend/archlab-api && ..\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18081
cd docs/api/examples/v0.4 && ARCHLAB_API_URL=http://127.0.0.1:18081 node verify-live.cjs
cd docs/api/examples/v0.4 && ARCHLAB_API_URL=http://127.0.0.1:18081 node verify-v5-live.cjs
```

This does not yet prove full v0.1–v0.3 or frontend regression, nor equivalence of a converted historical database operation. V6-22 remains in progress.
