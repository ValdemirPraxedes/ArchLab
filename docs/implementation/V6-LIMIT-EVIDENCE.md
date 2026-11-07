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
