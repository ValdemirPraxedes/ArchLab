# v0.6 virtual data limit checkpoint

## Critérios de aceite da V6-23 (decisão 4, 2026-11-07)

A decisão 4 do responsável fixou um aceite **funcional e portável**, com um **teto folgado de latência**:

| # | Critério | Como é verificado |
| --- | --- | --- |
| A1 | Com heap Java de **256 MiB**, todo cenário de pior caso termina com **resultado controlado** (outcome tipado ou 422 `EXECUTION_LIMIT_EXCEEDED`), **sem 5xx e sem OutOfMemoryError** | `performance/P6-01..06.json` executados a cada `mvnw test`, com `argLine=-Xmx256m` no `backend/pom.xml` (vale para toda a suíte), e `profile-limits.cjs` contra o servidor com `-Xmx256m -XX:+ExitOnOutOfMemoryError` |
| A2 | Cada cenário de pior caso responde em **< 2.000 ms** de relógio real | `maxWallMs: 2000` em cada caso P6 (`Ct6FixtureReplayTest`, `profile-limits.cjs`) |
| A3 | Casos pequenos (relacional e ETL de referência) têm **p95 ≤ 100 ms com concorrência 32** | `profile-api.cjs` com `ARCHLAB_PROFILE_MAX_P95_MS=100`, que encerra com erro se o p95 passar do teto |
| A4 | Resultados determinísticos: a mesma entrada produz resposta byte-idêntica | Todos os perfis comparam cada resposta com a primeira |

Throughput e memória total do processo (RSS) são **observações**, não critérios: dependem do sistema operacional e da máquina.

### Cenários de pior caso (P6)

| Caso | Envelope exercitado | Resultado esperado |
| --- | --- | --- |
| P6-01 | 1.000.000 registros lógicos (900.000 iniciais + bulk de 100.000) | `INSERTED`, e a próxima escrita dá `LIMIT_EXCEEDED` |
| P6-02 | 1 GiB lógico (1.024 × 1 MiB) | `LIMIT_EXCEEDED` |
| P6-03 | ETL de 1.000.000 registros em 1.000 lotes; variante com um lote a menos do que o necessário | `LOADED` com 1.000.000 registros; a variante dá `ETL_BATCH_LIMIT_EXCEEDED` e LOAD `SKIPPED` |
| P6-04 | Fan-out 99 × 99 × 99 (mais de 50.000 eventos) | 422 `EXECUTION_LIMIT_EXCEEDED` |
| P6-05 | Relógio virtual (100.000 × 100 ms > 3.600.000 ms) | 422 `EXECUTION_LIMIT_EXCEEDED` |
| P6-06 | 100 operações concorrentes em pool 1 com fila de 10.000 | 100 `INSERTED`, FIFO (`queueWaitMs` de 0 a 99) |

### Evidência de 2026-11-07 (VM Linux, JDK 25.0.5, `-Xmx256m`)

**Relacional e ETL** (1.000 requisições, concorrência 32, teto A3 ativo):

| Perfil | p50 | p95 | Máximo | Throughput |
| --- | ---: | ---: | ---: | ---: |
| relational | 47,35 ms | 77,87 ms | 175,16 ms | 644 req/s |
| etl | 38,37 ms | 64,63 ms | 96,01 ms | 772 req/s |

Uma segunda execução confirmou o p95: 74,42 ms (relacional) e 67,82 ms (ETL).

**Pior caso** (`profile-limits.cjs`, 10 repetições por caso, concorrência 4):

| Caso | Status | p50 | Máximo | Teto |
| --- | ---: | ---: | ---: | ---: |
| P6-01 | 200 | 6,6 ms | 11,5 ms | 2.000 ms |
| P6-02 | 200 | 6,6 ms | 16,5 ms | 2.000 ms |
| P6-03 (1.000 lotes) | 200 | 7,0 ms | 13,6 ms | 2.000 ms |
| P6-03 (um lote a menos) | 200 | 8,9 ms | 15,1 ms | 2.000 ms |
| P6-04 | 422 | 475,5 ms | 629,5 ms | 2.000 ms |
| P6-05 | 422 | 4,4 ms | 8,0 ms | 2.000 ms |
| P6-06 | 200 | 35,4 ms | 53,9 ms | 2.000 ms |

**Memória:** `MaxHeapSize=268435456` (256 MiB). Ao fim das três rodadas, o heap G1 estava com 172.223 KiB usados de 262.144 KiB reservados. Não houve `OutOfMemoryError` (`-XX:+ExitOnOutOfMemoryError` ativo). O pico de RSS do processo foi 419.100 kB; ele inclui metaspace, threads e code cache, e não é comparável ao working set do Windows medido antes.

O P6-04 é o caso mais caro, porque o motor processa eventos até atingir o teto de 50.000 antes de encerrar com 422. Mesmo assim, fica abaixo de um terço do teto A2.

**Comandos:**

```text
cd backend && ./mvnw test                      # A1/A2/A4 com -Xmx256m (argLine do pom)
./mvnw -B -q package -DskipTests
java -Xmx256m -XX:+ExitOnOutOfMemoryError -jar archlab-api/target/archlab-api-0.1.0-SNAPSHOT.jar --server.port=18081
cd docs/api/examples/v0.6
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_PROFILE_ITERATIONS=1000 ARCHLAB_PROFILE_CONCURRENCY=32 ARCHLAB_PROFILE_MAX_P95_MS=100 node profile-api.cjs
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_PROFILE_KIND=etl ARCHLAB_PROFILE_ITERATIONS=1000 ARCHLAB_PROFILE_CONCURRENCY=32 ARCHLAB_PROFILE_MAX_P95_MS=100 node profile-api.cjs
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_LIMITS_REPEATS=10 node profile-limits.cjs
```

---

## Histórico das medições anteriores

`VirtualDataResourcesV6LimitTest` exercises the per-run logical state without materializing one heap object per bulk record.

| Case | Observed result |
| --- | --- |
| Ten batches of 100,000 records | 1,000,000 logical records, then a one-record batch returns `LIMIT_EXCEEDED` without changing the count. |
| 1,024 records at 1 MiB each | 1 GiB logical bytes, then a one-record batch returns `LIMIT_EXCEEDED` without changing the count. |
| New runtime instance | Starts with the configured initial count, confirming state isolation. |

The focused test passed with a 64 MiB Java heap:

```text
cd backend
.\mvnw.cmd -pl archlab-simulation-core -Dtest=VirtualDataResourcesV6LimitTest -DargLine=-Xmx64m test
```

Surefire reported two passing tests. The full backend `mvn test` suite also passed. This establishes bounded logical bulk representation for these cases; it does not measure the peak heap of an end-to-end API request or latency under concurrent load. (Superseded by the V6-23 acceptance section above.)

## Local API load profile

The API from `develop` after PR #100 was started with `-Xmx256m` on port 18081. `profile-api.cjs` sends the corrected relational insert→select fixture, warms up with ten requests, and compares every response byte-for-byte. The fixture now includes `POST /orders` in its HTTP integration so the behavior actually executes.

| Measured requests | Concurrency | Elapsed wall time | Throughput | p50 | p95 | Maximum |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 200 | 8 | 123.56 ms | 1,618.69 requests/s | 4.53 ms | 7.56 ms | 9.82 ms |
| 1,000 | 32 | 479.38 ms | 2,086.04 requests/s | 14.05 ms | 24.25 ms | 33.52 ms |

All 1,200 measured requests returned `INSERTED` then `FOUND`, with identical complete JSON responses. After the second run, the Java process reported 181,272,576 bytes (172.88 MiB) peak working set and 228,974,592 bytes private memory. These are local process measurements, including JVM startup and framework overhead; they are not portable performance targets or a peak Java heap measurement.

To repeat the profile:

```text
cd backend/archlab-api
..\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18081 -Dspring-boot.run.jvmArguments=-Xmx256m
cd docs/api/examples/v0.6
ARCHLAB_API_URL=http://127.0.0.1:18081 node profile-api.cjs
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_PROFILE_ITERATIONS=1000 ARCHLAB_PROFILE_CONCURRENCY=32 node profile-api.cjs
```

The relational profile covers one small scenario on one Windows host. Large payloads, long runs, and other concurrency levels remain unprofiled. (Superseded by the V6-23 acceptance section above.)

## Local ETL profile

The same API heap cap (`-Xmx256m`) was used for an ETL `IDENTITY` scenario with 100 source records and an explicit `DATA_TRANSFORMER`. The profile runner checked `EXTRACTED`, `TRANSFORMED`, `LOADED`, each with a count of 100, and compared full responses across runs.

| Measured requests | Concurrency | Elapsed wall time | Throughput | p50 | p95 | Maximum |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 200 | 8 | 130.32 ms | 1,534.65 requests/s | 4.92 ms | 7.84 ms | 9.73 ms |
| 1,000 | 32 | 537.76 ms | 1,859.57 requests/s | 16.03 ms | 27.57 ms | 35.76 ms |

All 1,200 measured ETL requests had identical responses. Peak process working set after the second run was 179,687,424 bytes (171.36 MiB); private memory was 227,004,416 bytes. These are local observations, not acceptance thresholds. The fixture is `docs/api/examples/v0.6/etl-identity-simulation.json` and passes the v0.6 OpenAPI fixture validator.

```text
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_PROFILE_KIND=etl node profile-api.cjs
ARCHLAB_API_URL=http://127.0.0.1:18081 ARCHLAB_PROFILE_KIND=etl ARCHLAB_PROFILE_ITERATIONS=1000 ARCHLAB_PROFILE_CONCURRENCY=32 node profile-api.cjs
```

Large ETL batches, non-IDENTITY transforms, longer runs and target-specific performance thresholds remain outside this profile.
