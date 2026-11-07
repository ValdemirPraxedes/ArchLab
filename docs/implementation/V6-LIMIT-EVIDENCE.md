# v0.6 virtual data limit checkpoint

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

Surefire reported two passing tests. The full backend `mvn test` suite also passed. This establishes bounded logical bulk representation for these cases; it does not measure the peak heap of an end-to-end API request or latency under concurrent load. V6-23 remains in progress.

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

The profile covers one small relational scenario on one Windows host. Large payloads, ETL, long runs, and other concurrency levels remain unprofiled. V6-23 remains in progress.
