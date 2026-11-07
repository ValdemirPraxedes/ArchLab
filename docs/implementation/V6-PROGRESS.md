# V6-PROGRESS — Registro de continuidade da v0.6

> Registro de handoff entre agentes. Complementa (não substitui) `V6-INTEGRATION-STATUS.md`,
> `V6-CONTRACT-GATE-AUDIT.md`, `V6-LIMIT-EVIDENCE.md` e `V6-REGRESSION-EVIDENCE.md`.
> Última atualização: 2026-11-07 (America/Sao_Paulo), Claude — sessão 5 (V6-03 e V6-14; decisão 2 tomada pelo responsável).
> A sessão 1 (2026-11-06) fez apenas a auditoria; seu texto está preservado nas seções 1–6. A seção 0 abaixo prevalece onde houver diferença.

## 0000. Sessão 5 — 2026-11-07 (Claude): V6-03, V6-14 e decisão 2

- PR #107 mesclado (`develop` @ `9859ab9`). Trello: V6-01 e V6-02 em **✅ Concluído**.
- **Decisão 2 do responsável:** converter com equivalência temporal. `CALL` → `DB_SELECT`; `queryText`/`responseText` ficam na conexão; pool e concorrência em 1000. Continuam bloqueados `NON_QUERY_CONNECTION` e `HISTORICAL_CALL_TIMEOUT` (o prazo do `CALL` inclui a rede).
- **V6-03:** política normativa em [`docs/architecture/MIGRATION-V6-001.md`](../architecture/MIGRATION-V6-001.md). Backend (`LegacyDatabaseMigrationV6`) e editor (`migrationV6.ts`) aplicam as mesmas regras, e o teste do editor compara a saída com a fixture CT-6-07 do backend. CT-6-07 ganhou o caso de conversão; CT-6-08 agora cobre os dois bloqueios restantes. `migration-legacy.json` foi atualizado.
- **V6-14:** `LegacyMigrationEquivalenceV6Test` com `docs/api/examples/v0.6/migration/M6-01..04.json`, comparando relatório esperado, IDs, conexões, Actions e `dependsOn` preservados, e simulações equivalentes (status, duração total, resposta e tempo de cada atividade da timeline). Um teste de mutação (pool 1) confirmou que a comparação detecta divergência.
- Gates: `mvnw test` (core 121 / API 140), `npm test` (34 arquivos / 219 testes), lint e build OK, `npm run validate` 95 PASS / 0 FAIL.
- Pendentes: decisões 1 e 4; V6-04, V6-22, V6-23 e V6-24.

## 000. Sessão 4 — 2026-11-07 (Claude): V6-02 e decisões 3/5

- PR #106 mesclado (`develop` @ `fdc46cb`). Trello: V6-05, V6-13, V6-15, V6-16, V6-17, V6-18, V6-19 e V6-20 em **✅ Concluído**.
- **Decisões do responsável:** (3) `DB_BULK_INSERT` atômico, com rejeição total no limite; (5) sem bulk em `KEY_VALUE_STORE` em P0.
- **V6-02:** documento normativo [`docs/architecture/DATA-BUDGETS-V6-001.md`](../architecture/DATA-BUDGETS-V6-001.md), com três camadas de limite, fórmulas de custo, atomicidade, tabela de estados e defaults. Exemplos executáveis em `docs/api/examples/v0.6/normative/N6-01..08.json` (16 casos), executados e repetidos por `Ct6FixtureReplayTest`, com requisição e resposta validadas por `validate.cjs`. Nenhuma mudança de runtime foi necessária: o motor já cumpria as regras, e os exemplos agora as congelam.
- **V6-01:** DM-V6-001 e `capability-matrix-v6.json` registram a decisão 5; o aceite ("matriz fechada, com rejeições documentadas") está atendido.
- Gates desta sessão: `mvnw test` (core 121 / API 133), `npm test`, lint e build, `npm run validate` (todos PASS).
- Pendentes: decisões 1, 2 e 4; V6-03, V6-04, V6-14, V6-22, V6-23 e V6-24.

## 00. Sessão 3 — 2026-11-07 (Claude): estado atual (prevalece sobre a seção 0)

### 00.1 Git, PRs e Trello

| Item | Valor |
| --- | --- |
| Integração | `origin/develop` @ `6cda053` (PR #105 mesclado; #104 também) |
| Branch desta sessão | `feat/v0.6-frontend-completion` (a partir de `6cda053`) |
| Push/PR | Esta sessão tem acesso de escrita ao repositório pela integração GitHub do Claude; o PR é aberto para `develop`, **sem merge** |
| Trello | V6-06, V6-07, V6-08, V6-09, V6-10, V6-11, V6-12 e V6-21 movidas para **✅ Concluído**, com comentário de evidência (aprovação do responsável + PR #105) |

### 00.2 Commits desta sessão

| Commit | Tarefa | Resumo |
| --- | --- | --- |
| `feat(frontend): list every ambiguous DATABASE migration…` | V6-18 | A prévia local reúne **todas** as ambiguidades como `DATA_MIGRATION_AMBIGUOUS`, com o mesmo `path`/`reason` do backend, e as mostra no painel de validação. O canvas e a entrada ficam intactos. |
| `test(v0.6): integrated canonical export round-trip…` | V6-20 | Golden `docs/api/examples/v0.6/canonical-export-p0.json` (5 tipos P0 + ETL): import→export idêntico byte a byte (frontend), schema `Architecture` (validate.cjs), validação + simulação com replay (backend). |
| `fix(v0.6): make data outcomes fully traceable…` | V6-13, V6-04 | `DATA_QUEUE_FULL` e o timeout na fila agora emitem `ACTION_EVENT` (antes ficavam sem causa no trace). Contrato alinhado ao motor, de forma aditiva: `ActionTrace.outcome` oneOf v5/v6; `MessageTrace.intent` com as Actions P0; `phase` com `DATA_OPERATION`/`ETL_*`. **As respostas reais dos CT-6 violavam o OpenAPI**; agora são validadas (8/8). |
| `feat(frontend): validate data properties against the OpenAPI bounds…` | V6-16 | Limites únicos (`dataLimitsV6.ts`), testados contra o `openapi.yaml`; campo com min/max, `aria-invalid` e mensagem; dica de concorrência efetiva. |
| `feat(v0.6): single normative type x action matrix…` | V6-01, V6-17 | `capability-matrix-v6.json` é a fonte única, lida pelo teste Java (24 combinações DB + ETL EXTRACT/LOAD nos 3 tipos) e pelo editor (equivalência + render de conexões oferecidas). Tabela na DM-V6-001 §2.1. |
| `test(frontend): every P0 data type is creatable…` | V6-15 | Os 5 tipos P0 são criáveis pela paleta, com o ícone Lucide do UI-V6-001; `DATABASE` continua ausente. |

### 00.3 Testes executados

| Gate | Resultado |
| --- | --- |
| `cd backend && ./mvnw test` | **SUCCESS** — core 121, API 117 |
| `cd frontend && npm test` | **34 arquivos / 219 testes** |
| `npm run lint` / `npm run build` | sem erros / SUCCESS |
| `npm run validate` (após `mvnw test`) | **59 PASS / 0 FAIL**, incluindo 8 respostas reais do backend contra `SimulationResponse` |

### 00.4 Novas divergências registradas

- **Importador do editor × contrato:** o editor exige `request`/`response` em `HTTP_REST`; o contrato e o backend aceitam sem eles. Por isso as fixtures CT-6 não podem ser importadas no canvas como estão (o golden declara os campos). Decidir se o editor deve aplicar defaults na importação.
- **KV × batch:** registrado na DM-V6-001 §2.1 (decisão 5).
- **`TRANSFORM` v0.5 e outros intents/fases v0.5** não exercitados pelos CT-6 não foram auditados contra os enums; a validação de respostas cobre hoje apenas cenários v0.6.

### 00.5 Status das 24 tarefas após a sessão 3

| Tarefa | Status | Evidência / pendência |
| --- | --- | --- |
| V6-01 | Pronta para revisão — aguarda decisão 5 | Matriz normativa + testes nos dois lados |
| V6-02 | Parcial | Decisão 3 (atomicidade de bulk) |
| V6-03 | Parcial | Decisão 2 (escolha manual de destino) |
| V6-04 | Parcial | Respostas validadas; falta a decisão 1 (`Component.oneOf` × `DATABASE`) e o Redocly lint |
| V6-05 | Pronta para revisão | Tipos TS 100% gerados; respostas validadas contra o schema; backend serializa `Map`/`JsonNode` (sem DTO manual a divergir) |
| V6-06 a V6-12 | **Concluídas** (Trello) | PR #105 |
| V6-13 | Pronta para revisão | Teste de correlação outcome ↔ trace/mensagens/timeline |
| V6-14 | Parcial — bloqueada pela decisão 2 | Sem escolha de destino, uma operação legada (`CALL`/`queryText`) não tem equivalente para comparar antes/depois |
| V6-15 | Pronta para revisão | Paleta P0 + ícones |
| V6-16 | Pronta para revisão | Limites do contrato no formulário |
| V6-17 | Pronta para revisão | Somente combinações compatíveis (testado por Action) |
| V6-18 | Pronta para revisão | Diagnósticos por componente; fluxo de escolha depende da decisão 2 |
| V6-19 | Pronta para revisão | Contadores, bytes, lotes e erros no playback; a correlação está coberta no backend (V6-13) |
| V6-20 | Pronta para revisão | Golden round-trip em 3 camadas |
| V6-21 | **Concluída** (Trello) | PR #105 |
| V6-22 | Parcial | Suítes v0.1–v0.5 verdes; falta evidência manual v0.1–v0.3 no frontend |
| V6-23 | Parcial | Decisão 4 (limiares) |
| V6-24 | Pendente | Após V6-22/23 e as decisões |

### 00.6 Próximos passos

1. Responsável: revisar e mesclar o PR desta sessão; responder às decisões 1–5 (seção 3).
2. Após a decisão 2: fluxo de escolha de destino na prévia (V6-18) e equivalência antes/depois da migração (V6-14).
3. V6-22: roteiro de regressão manual v0.1–v0.3 no frontend.
4. V6-23: executar `profile-api.cjs` com os limiares aprovados.

## 0. Sessão 2 — 2026-11-07 (Claude): estado atual

### 0.1 Ambiente e Git

| Item | Valor |
| --- | --- |
| Branch de trabalho | `feat/v0.6-data-ops-completion`, criada a partir de `origin/develop` @ `1cf1648` |
| Inclui | o commit de handoff `459af5a` (`docs/v0.6-progress-handoff`, cherry-pick) — o PR de handoff, se aberto, fica redundante |
| Clone local (`C:\Users\valde\Documents\GitHub\ArchLab`) | em `develop` atualizado; os 58 arquivos "modificados" eram **somente LF→CRLF** (`git diff --ignore-cr-at-eol --ignore-all-space` vazio). Não foram tocados nem descartados. |
| Toolchain usada | Temurin JDK 25.0.5, Maven Wrapper 3.9.11, Node 22. Os processos em segundo plano da VM não sobrevivem entre chamadas; tudo rodou em primeiro plano. |
| Push/PR | Esta sessão não tem credenciais GitHub: commits feitos localmente, **push e PR ficam com o responsável** |
| Trello | Sem atualização de cartões nesta sessão |

### 0.2 Commits desta sessão (em ordem)

| Commit | Tarefa | Resumo |
| --- | --- | --- |
| `fix(frontend): include required simulateFailure…` | Regressão | **`npm run build` estava quebrado na `develop`** (`tsc -b`: fixture de `ETL_TRANSFORM` sem `simulateFailure`, obrigatório no tipo gerado). Vitest e lint passavam, mascarando o erro. |
| `feat(v0.6): partition ETL into finite logical batches…` | V6-12, V6-02, V6-09 | Lotes reais: `ceil(registros / batchSize)`, `batchSize` da Action > `batchSize` do pipeline; acima de `maxBatches` → `LIMIT_EXCEEDED`/`ETL_BATCH_LIMIT_EXCEEDED`, LOAD `SKIPPED`, sem truncamento. Estouro de orçamento vira outcome `ETL_RECORD_BUDGET_EXCEEDED` (antes: `IllegalArgumentException` no loop de eventos). `batchCount`/`batchSize` opcionais em `DataOperationOutcomeV6` (OpenAPI aditivo). `DB_UPDATE` preserva atributos não declarados (antes, o documento atualizado sumia do `DB_FIND`). |
| `test(v0.6): cover full type x action matrix…` | V6-01, V6-06, V6-09, V6-10, V6-11, V6-12 | Matriz 3 tipos × 8 Actions DB (24 combinações); Document GET/UPDATE/DELETE/FIND + `limit`; KV DELETE e ausência de TTL após 10 min virtuais (`ttlMs` rejeitado na decodificação); `DATA_QUEUE_FULL` com pool 1/fila 2; lotes e limite de lotes. |
| `fix(v0.6): report ambiguous legacy DATABASE migration…` | V6-14, V6-03, CT-6-08 | `migrate-v6` agora responde 400 `DATA_MIGRATION_AMBIGUOUS` com `violations[0].path` e `details.reason` (`HISTORICAL_QUERY_TEXT`, `NON_QUERY_CONNECTION`, `HISTORICAL_CALL`) em vez de `INVALID_REQUEST` genérico. Status HTTP inalterado; `Problem` já permite campos extras. |
| `test(v0.6): add replayable CT-6-01..10…` | V6-21 | `docs/api/examples/v0.6/ct/CT-6-01..10.json` (13 casos) + `Ct6FixtureReplayTest` (executa e repete cada caso, exige resposta idêntica) + validação estrutural em `validate.cjs`. |
| `feat(frontend): show ETL batches…` | V6-19 | Playback mostra `lotes N × tamanho` e `normalizados N`. |

### 0.3 Testes executados nesta sessão (branch `feat/v0.6-data-ops-completion`)

| Gate | Resultado |
| --- | --- |
| `cd backend && ./mvnw test` | **SUCCESS** — core 121 testes, API 114 testes (eram 95 na `develop`), 0 falhas |
| `cd frontend && npm test` | **31 arquivos / 186 testes** passando |
| `npm run lint` | sem erros |
| `npm run build` (inclui `typegen` + `tsc -b`) | **SUCCESS** (falhava na `develop`) |
| `cd docs/api/examples/v0.6 && npm run validate` | **48 PASS, 0 FAIL** (inclui os 13 casos CT-6) |
| Redocly lint do OpenAPI | não executado nesta sessão |

### 0.4 Status das 24 tarefas após a sessão 2

| Tarefa | Status | Evidência | Próxima ação |
| --- | --- | --- | --- |
| V6-01 Matriz | Parcial → aguarda aprovação | `fullTypeByActionMatrixIsEnforcedBeforeExecution` (24 combinações) | Aprovar divergência: DM-V6-001 marca "Batch: Sim" para KV, mas `DB_BULK_INSERT` em KV é rejeitado (lote não tem chave exata única) |
| V6-02 Budgets/atomicidade | Parcial | Lotes, `ETL_BATCH_LIMIT_EXCEEDED`, `ETL_RECORD_BUDGET_EXCEEDED`, API-V6-001 atualizado | Decisão 3 (bulk parcial × rejeição total) |
| V6-03 Política de migração | Parcial | Diagnóstico tipado + CT-6-07/08 | Decisão 2 (escolha manual para `queryText`/`CALL`) |
| V6-04 OpenAPI | Parcial | `batchCount`/`batchSize`; fixtures CT validadas | Decisão 1 (`Component.oneOf` × `DATABASE`); Redocly lint |
| V6-05 TS × DTOs | Parcial | build com `typegen` verde | Reconciliar tipos manuais (`archlabV2Client`) com o gerado |
| V6-06 Capabilities | **Pronta para revisão** | matriz completa + `DATA_ACTION_INCOMPATIBLE` | Fechar junto com V6-01 |
| V6-07 Estado por execução | Pronta para revisão | CT-6-09 (3 replays idênticos, `DB_INSERT` de mesma chave nunca colide) | — |
| V6-08 Relational | Pronta para revisão | CT-6-01, CT-6-09, testes CRUD/duplicidade | Ver limitação 4.3 (bulk sem chaves) |
| V6-09 Document | **Pronta para revisão** | `documentGetUpdateDeleteAndFindReflectVirtualState`, CT-6-02 | A ordenação é observável só pela sequência de outcomes; FIND retorna contagem, não lista |
| V6-10 Key-Value | **Pronta para revisão** | `keyValueDeleteHasNoImplicitTtl`, CT-6-03 | — |
| V6-11 Pool/fila/timeout | **Pronta para revisão** | `fullDataQueueRejectsWithDataQueueFull`, CT-6-04, testes de timeout | — |
| V6-12 ETL | **Pronta para revisão** | lotes reais, CT-6-05/06/10 | — |
| V6-13 `dataOutcomes` | Parcial (funcional) | campos aditivos, regressão Java verde | Teste de correlação outcome ↔ trace |
| V6-14 Adaptador legado | Parcial | diagnóstico tipado, CT-6-07/08 | Equivalência de simulação antes/depois; fluxo de escolha (decisão 2) |
| V6-15 Paleta/ícones | Parcial (funcional) | sem mudança | Conferir ícones contra UI-V6-001 |
| V6-16 Formulários | Parcial | sem mudança | Validação contextual de limites |
| V6-17 Editor de Actions/ETL | Parcial | build corrigido | Teste de filtragem de combinações e `inputRef` |
| V6-18 Prévia de migração | Parcial | backend agora devolve `path`/`reason` | Mostrar `violations[0]` do `DATA_MIGRATION_AMBIGUOUS` na prévia |
| V6-19 Playback | Parcial (funcional) | lotes/normalizados + teste | Correlação por operação |
| V6-20 Export canônico | Parcial (funcional) | sem mudança | Round-trip integrado com backend |
| V6-21 Fixtures CT-6 | **Pronta para revisão** | `ct/` + `Ct6FixtureReplayTest` + `validate.cjs` | Usar no roteiro de homologação |
| V6-22 Regressão v0.1–v0.5 | Parcial | suíte Java completa verde (inclui `V1PublicContractRegressionTest`, migrações v5) | Evidência manual v0.1–v0.3 no frontend |
| V6-23 Performance | Parcial | sem mudança | Limiares de aceite (decisão 4) |
| V6-24 DoD | Pendente | — | Depende de V6-22/23 e das decisões |

"Pronta para revisão" = aceite do TASKS-V6-001 coberto por teste automatizado; só vira **Concluída** após revisão/merge do PR e aprovação do responsável.

### 0.5 Próximos passos recomendados

1. Responsável: fazer push de `feat/v0.6-data-ops-completion`, abrir PR para `develop` e responder às decisões da seção 3.
2. V6-18: exibir no frontend o diagnóstico `DATA_MIGRATION_AMBIGUOUS` (`path`, `reason`) — hoje a prévia local (`migrationV6.ts`) bloqueia por conta própria.
3. V6-13/V6-19: teste de correlação `dataOutcomes[].operationId` ↔ `trace[].operationId`.
4. V6-05: reconciliar `archlabV2Client` (tipos manuais) com `schema.d.ts`.
5. V6-22/V6-23: evidência v0.1–v0.3 e limiares de performance.

## 1. Estado encontrado (sessão 1, 2026-11-06)

| Item | Valor |
| --- | --- |
| Integração | `origin/develop` @ `1cf1648` (merge do PR #103) |
| PRs v0.6 mesclados | #90 (docs), #91 (OpenAPI P0), #92 (CI), #93 (gate audit), #94 (backend), #95 (frontend + migração), #96–#98 (integração/migração backend), #99 (regressão v0.4/v0.5), #100 (limites), #101 (perfil API), #102 (perfil ETL), #103 (transforms ETL) |
| PRs v0.6 abertos | Nenhum identificado (todas as branches `*/v0.6-*` estão contidas em `develop`, exceto commits de merge cruzado) |
| Branches remotas v0.6 | `docs/v0.6-data-architecture-planning`, `docs/v0.6-openapi-contract-p0`, `fix/v0.6-contract-validation`, `feat/v0.6-contracts`, `feat/v0.6-backend`, `feat/v0.6-frontend`, `test/v0.6-integration`, `feat/v0.6-legacy-equivalence`, `test/v0.6-performance`, `test/v0.6-api-profile`, `test/v0.6-etl-profile`, `feat/v0.6-etl-transforms` |
| Último trabalho do Codex | V6-12 — `FILTER_VALID` e `NORMALIZE_METADATA` executáveis em lotes lógicos (PR #103, 2026-11-06 23:35) |
| Clone local do responsável | `C:\Users\valde\Documents\GitHub\ArchLab` em `feat/v0.5-backend-service-expansion`, último fetch anterior à v0.6: **não contém nenhum commit v0.6**. Fazer `git fetch` antes de trabalhar localmente. |
| Trello | Sem acesso nesta sessão; nenhum cartão foi alterado. |

## 2. Status das tarefas (validado no código, não nos cartões)

Legenda: **Concluída** = aceite do TASKS-V6-001 atendido com teste; **Parcial** = implementado com lacunas de aceite; **Pendente** = não iniciado.

| Tarefa | Status | Evidência | Próxima ação |
| --- | --- | --- | --- |
| V6-01 Matriz tipos × Actions | Parcial | `UnifiedPlanV41.dataActionTarget`, `BehaviorEditorV41.dataTargets`, `incompatibleActionIsRejectedBeforeExecution` | Publicar matriz normativa única em doc e testar a matriz completa (todas as 11 Actions × 5 tipos) no Java |
| V6-02 Budgets, atomicidade, estados | Parcial | Limites em `ArchitectureGraphValidatorV2`, `VirtualDataResourcesV6` (1M registros / 1 GiB); `V6-LIMIT-EVIDENCE.md` | Documento normativo de defaults, atomicidade de bulk parcial e códigos de resultado |
| V6-03 Política de migração `DATABASE` | Parcial | `LegacyDatabaseMigrationV6`, `migrationV6.ts`, fixture `migration-legacy.json` | Decisão de produto para `queryText`/`CALL` ambíguos (hoje bloqueados) |
| V6-04 OpenAPI 3.1 + fixtures | Parcial | `openapi.yaml` (+393 linhas), `examples/v0.6/*`, workflow `openapi-v06-validation.yml` | Separar schema canônico × legado (`Component.oneOf` ainda admite `DATABASE`); fixtures de Document CRUD/FIND, limites inválidos e `dataOutcomes` |
| V6-05 TS gerado × DTOs Java | Parcial | `typegen` em CI | Reconciliar tipos manuais do frontend e DTOs Java com o schema gerado |
| V6-06 Discriminadores/capabilities/validação | Parcial (funcional) | `ComponentTypeV2`, `DataResourceV6`, `DataProcessorV6`, `V2RequestMapper`, `DATA_ACTION_INCOMPATIBLE` | Cobertura de rejeição por combinação; depende do fechamento de V6-01 |
| V6-07 Estado virtual por execução | Parcial (funcional) | `VirtualDataResourcesV6` instanciado por `UnifiedSimulationV41`; teste de isolamento/replay | Documentar que bulk/initial são contagens lógicas sem chaves |
| V6-08 Relational CRUD/BULK | Parcial (funcional) | `relationalInsertIsVisibleToLaterSelect…`, `relationalUpdateDeleteAndDuplicateKey…`, `bulkInsertRejectsAmbiguousSingleKey` | Validar semântica de `DB_SELECT` sem chave sobre registros lógicos de bulk contra CT-6 |
| V6-09 Document GET/PUT/FIND/UPDATE/DELETE | Parcial | `keyValueMissPutHitAndDocumentFindAreObservable` cobre PUT→FIND | Testes de GET/UPDATE/DELETE em Document e ordenação estável do FIND |
| V6-10 Key-Value GET/PUT/DELETE | Parcial (funcional) | Mesmo teste (miss→put→hit) | Teste de DELETE em KV e ausência de TTL implícito |
| V6-11 Pool, fila, timeout | Parcial (funcional) | `poolOneQueuesConcurrentWrites…`, `queuedOperationTimesOut…`, `processingTimeoutLeavesNoVisibleRecord` | Teste de `DATA_QUEUE_FULL` (queueCapacity esgotada) |
| V6-12 ETL EXTRACT/TRANSFORM/LOAD | Parcial (funcional) | `etlExtractTransformLoadConservesCount`, `etlLoadWithoutDependentProducerIsRejected`, PR #103 | `batchSize`/`maxBatches` do pipeline ainda não fracionam a execução em lotes; testes de `FILTER_VALID`/`NORMALIZE_METADATA` ponta a ponta |
| V6-13 `dataOutcomes` | Parcial (funcional) | `UnifiedSimulationV41.dataOutcomes`, schema OpenAPI | Teste de correlação outcome ↔ trace/timeline e de não-alteração dos campos antigos |
| V6-14 Adaptador legado + regressão | Parcial | `POST /api/v1/architectures/migrate-v6`, `ArchitectureMigrationServiceV6Test` | Equivalência de simulação antes/depois da migração; fluxo de escolha explícita para casos ambíguos |
| V6-15 TS, ícones, paleta | Parcial (funcional) | `ComponentIcon.tsx` (Table2, FileJson2, KeyRound, ListFilter, Replace), paleta sem `DATABASE` | Conferir ícones contra UI-V6-001 e teste de paleta sem `DATABASE` |
| V6-16 Formulários de dados | Parcial | Seção "Dados virtuais" em `ArchitectureEditor.tsx` | Validação contextual dos limites (hoje só `min`), mensagens por campo |
| V6-17 Editor de Actions/ETL | Parcial | `BehaviorEditorV41.tsx` (`dataTargets`, campos, transform select) + teste | Testar filtragem de combinações incompatíveis e encadeamento `inputRef` |
| V6-18 Prévia de migração | Parcial | `pendingImport` + confirmar/cancelar; `App.test.tsx` | Casos ambíguos: hoje erro bloqueante; falta fluxo de escolha |
| V6-19 `dataOutcomes` no playback | Parcial (funcional) | `SimulationPlaybackV2.tsx`, `playbackV6.test.tsx` | Causas/correlação por operação |
| V6-20 Export JSON canônico | Parcial (funcional) | `exportV6.ts`, `exportV6.test.ts` (round-trip) | Round-trip integrado com import e backend |
| V6-21 Fixtures CT-6-01..10 | Parcial | Ver tabela de cenários em `V6-INTEGRATION-STATUS.md` | Fixtures JSON replayáveis para os 10 CTs em `docs/api/examples/v0.6` |
| V6-22 Regressão v0.1–v0.5 | Parcial | `V6-REGRESSION-EVIDENCE.md` (v0.4/v0.5 ao vivo) | Evidência v0.1–v0.3 e frontend |
| V6-23 Performance/limites | Parcial | `V6-LIMIT-EVIDENCE.md`, `profile-api.cjs` | Limiares de aceite aprovados e cargas mais amplas |
| V6-24 Revisão final/DoD | Pendente | — | Depende de V6-22/V6-23 e aprovação do produto |

**Nenhuma tarefa está formalmente concluída**: o próprio Codex registrou que o Gate A (V6-01..05) continua aberto por decisões de contrato que dependem do responsável pelo produto.

## 3. Decisões pendentes do responsável (bloqueiam o fechamento de gates)

1. Fronteira de schema canônico × legado: `/api/v1/architectures/validate` deve aceitar `DATABASE` histórico diretamente ou somente após migração explícita?
2. ~~Tratamento de `queryText`/`responseText` e `CALL` histórico.~~ **Decidido em 2026-11-07: converter com equivalência temporal** (`CALL` → `DB_SELECT`, texto mantido na conexão, concorrência 1000). Ver MIGRATION-V6-001.
3. ~~Atomicidade de `DB_BULK_INSERT` ao atingir limite.~~ **Decidido em 2026-11-07: rejeição total (lote atômico).** Ver DATA-BUDGETS-V6-001.
5. ~~`DB_BULK_INSERT` em `KEY_VALUE_STORE`.~~ **Decidido em 2026-11-07: não em P0.** Ver DM-V6-001 §2.1.
4. Limiares de aceite de performance (V6-23).

## 4. Problemas conhecidos

- `Component.oneOf` no OpenAPI ainda aceita `DATABASE` para qualquer requisição (comentário não é regra de schema).
- ~~`batchSize`/`maxBatches` de `ETL_PIPELINE` são validados mas não particionam a execução.~~ Resolvido na sessão 2.
- Initial dataset e bulk são contagens lógicas; `DB_GET`/`DB_FIND` só encontram registros gravados com chave/campo explícitos.
- ~~`etlAction` usa `IllegalArgumentException` para orçamento > 1M registros.~~ Resolvido na sessão 2 (`ETL_RECORD_BUDGET_EXCEEDED`).

## 5. Ambiente desta sessão (Claude)

- Sem credenciais GitHub (somente leitura anônima): não foi possível abrir PRs nem fazer push.
- (Sessão 1) Sem acesso ao Maven Central/npm e sem JDK 25: não foi possível compilar nem executar testes. A sessão 2 executou todos os gates (seção 0.3). Por isso nenhuma alteração de runtime foi feita — apenas este registro.
- Comandos de verificação local (Windows, como o Codex usou):

```text
cd backend && .\mvnw.cmd test
cd frontend && npm run typegen && npm run lint && npm test -- --run && npm run build
cd docs/api/examples/v0.6 && npm install && npm run validate
```

## 6. Próxima tarefa recomendada

1. Responder às decisões da seção 3 (destrava Gate A).
2. Em seguida, sem depender delas: completar testes de V6-09/V6-10/V6-11 (Document GET/UPDATE/DELETE, KV DELETE, `DATA_QUEUE_FULL`) e trocar a exceção de orçamento ETL por outcome `LIMIT_EXCEEDED` — mudanças pequenas no backend, cobertas por `DataActionsV6IntegrationTest`.
3. Depois: particionamento real por `batchSize`/`maxBatches` (V6-12) e fixtures CT-6-01..10 (V6-21).
