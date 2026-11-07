# v0.6 historical API regression checkpoint

## V6-22 — regressão v0.1–v0.5 em API e frontend (2026-11-07)

**Critério de aceite:** nenhuma mudança silenciosa.

### 1. Diferencial completo contra a release v0.5

Dois servidores rodaram lado a lado, ambos com `-Xmx256m`:
- **baseline:** release v0.5 (`origin/main` @ `5e6ae09b`);
- **candidato:** `develop` @ `45a8c73` (v0.6 com os PRs #90–#110).

O corpus congelado da release (`docs/api/examples/regression/corpus-v05.json`) tem 45 requests distintos:
- 25 blocos JSON dos testes Java da v0.5;
- 15 fixtures de `docs/api/examples` (v0.4 e anteriores);
- 5 exemplos do OpenAPI v1 e v2.

Cada request foi enviado aos 6 endpoints (`/api/v1` e `/api/v2`: `architectures/validate`, `scenarios/validate`, `simulations`).

| Medida | Resultado |
| --- | ---: |
| Chamadas comparadas | 270 |
| Respostas do baseline: 200 / 400 / 422 | 76 / 189 / 5 |
| **Diferenças (status ou corpo canônico)** | **0** |
| Variações toleradas (`dataOutcomes: []` aditivo) | 14 |

As respostas de erro (400/422, inclusive `type`, `title`, `detail` e `code` do Problem) também são idênticas.

### 2. Teste contínuo

O `HistoricalRegressionV6Test` repete as 270 chamadas em todo `mvnw test` e compara status e SHA-256 do corpo canônico com o `golden-v05.json`, gravado a partir da release v0.5: **270/270 iguais**. Uma mutação de um único caractere na mensagem `INVALID_REQUEST` derrubou 187 das 270 verificações, o que confirma que o teste detecta mudanças.

O repositório não tinha CI de backend; foi incluído o `.github/workflows/backend-ci.yml` (JDK 25, `./mvnw test`).

### 3. Clientes e verificadores ao vivo

Executados contra o baseline e contra o candidato, com o mesmo resultado nos dois:

| Verificador | Versões | Resultado |
| --- | --- | --- |
| `frontend/scripts/integration.mjs` | v0.1/v0.2 (`protocol` REST/TCP, API + DATABASE) | PASS (todos os cenários reais) |
| `frontend/scripts/integration-v03-clients.mjs` | v0.3 (clientes e protocolos v2) | PASS (24 cenários v2) |
| `docs/api/examples/v0.4/verify-live.cjs` | v0.4 | PASS (positivos e negativos) |
| `docs/api/examples/v0.4/verify-v5-live.cjs` | v0.5 | PASS (8 cenários) |

### 4. Frontend

A suíte do editor (`npm test`, 34 arquivos e 219 testes) continua verde. Ela cobre os clientes v0.3, a paleta, o import histórico de `API` (v0.5 → `SERVICE`) e de `DATABASE` (prévia v0.6), o playback v0.4/v0.5 e o export.

### Mudanças intencionais e documentadas (não silenciosas)

| Mudança | Onde está documentada |
| --- | --- |
| `dataOutcomes` aditivo nas simulações unificadas | API-V6-001, tolerado no diferencial |
| `DATABASE` histórico: migração explícita via `migrate-v6` (não altera `/validate` nem `/simulations`) | MIGRATION-V6-001 |
| `schemaVersion: "0.6"` rejeita `API`/`DATABASE` (documentos sem marcador seguem históricos) | API-V6-001 §2.1 |

---

## Checkpoint anterior (2026-11-06)

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

(Superseded by the V6-22 section above. Converted database operation equivalence is covered by MIGRATION-V6-001 / LegacyMigrationEquivalenceV6Test.)
