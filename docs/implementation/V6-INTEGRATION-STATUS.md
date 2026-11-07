# v0.6 P0 integration status

This is an implementation checkpoint, not product acceptance. The stacked draft PRs are A (contracts), B (backend), C (frontend), and D (integration). No branch has been merged into `develop`.

## Backlog status

| Tasks | State | Remaining acceptance |
| --- | --- | --- |
| V6-01–V6-05 | In progress | Final capability matrix, contract reconciliation and approved legacy policy |
| V6-06–V6-13 | In progress | Full CRUD/ETL semantics, limits, error states and outcome correlation coverage |
| V6-14 | Pending | Backend legacy adapter and historical equivalence proof |
| V6-15–V6-20 | In progress | Contextual validation, all migration choices and integrated round-trip |
| V6-21–V6-22 | In progress | Complete CT fixtures and v0.1–v0.5 comparison evidence |
| V6-23–V6-24 | Pending | Memory/performance profile and product acceptance |

No V6 task is marked complete solely from a passing build or a basic scenario test.

## Implemented and exercised

| Scenario | Evidence | Status |
| --- | --- | --- |
| CT-6-01 Relational insert → select | `DataActionsV6IntegrationTest.relationalInsertIsVisibleToLaterSelectAndRunIsIsolated` | Basic path passes |
| CT-6-02 Document put → find | `keyValueMissPutHitAndDocumentFindAreObservable` | Basic path passes |
| CT-6-03 Key-value miss → put → hit | Same Java integration test | Passes |
| CT-6-04 Pool size 1 and FIFO wait | `poolOneQueuesConcurrentWritesWithoutLosingEither` | Basic path passes |
| CT-6-05 ETL source → transformer → target | `etlExtractTransformLoadConservesCount` | IDENTITY batch passes |
| CT-6-06 Transform failure skips load | Same ETL test with `simulateFailure` | Passes |
| CT-6-07 Legacy migration preview | `migrationV6.test.ts` and `App.test.tsx` | Unambiguous inputs pass |
| CT-6-08 Ambiguous legacy input | `migrationV6.test.ts` | Query text is blocked without canvas mutation |
| CT-6-09 Determinism and run isolation | Relational integration test replays identical response | Basic path passes |
| CT-6-10 Finite limits | `resourceLimitsRejectOversizedInitialDataset`; Java event/time caps | Basic path passes |

## Remaining acceptance work

- `FILTER_VALID` and `NORMALIZE_METADATA` are in the OpenAPI enum but are rejected by the Java semantic validator until their record semantics are specified and implemented. `IDENTITY` is executable.
- Initial datasets and bulk inserts are represented by logical counts; detailed key/field lookup is available only for explicitly written records. This must be documented and checked against the intended CT-6 semantics.
- Queue timeout, processing timeout and pool occupancy have focused tests, but broader load and memory profiling remain pending.
- Legacy `DATABASE_QUERY` with text or `CALL` is ambiguous and intentionally blocks automatic conversion. An explicit user choice flow is still needed for those documents.
- The `/api/v1` OpenAPI allows unversioned historical input. Versioned `0.6` documents reject `API` and `DATABASE`. This boundary requires final contract review.
- Full API/frontend end-to-end replay, memory profiling, and product homologation remain pending. Redocly lint passed with two warnings; historical v0.4 fixture validation passed.

## Local verification commands

```text
cd backend && .\mvnw.cmd test
cd frontend && npm run typegen && npm run lint && npm test -- --run && npm run build
cd docs/api/examples/v0.6 && npm run validate
```
