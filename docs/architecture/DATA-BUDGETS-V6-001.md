# DATA-BUDGETS-V6-001 — Budgets, custo virtual, atomicidade e estados de resultado (v0.6 P0)

> **Status:** normativo para P0 (V6-02). Aprovado pelo responsável pelo produto em 2026-11-07: o lote é atômico (decisão 3) e não há bulk em Key-Value (decisão 5).
> **Fonte executável:** cada regra abaixo tem um exemplo em `docs/api/examples/v0.6/normative/N6-XX.json`. O exemplo é executado contra `/api/v1` por `Ct6FixtureReplayTest`, repetido até dar resposta idêntica, e tem a requisição e a resposta validadas contra o OpenAPI por `validate.cjs`. Se o documento e o exemplo divergirem, vale o exemplo, e a divergência é um defeito.

## 1. Princípios

1. **Finito por construção.** Toda execução tem teto de registros, bytes, lotes, eventos e tempo virtual. Nenhum caminho depende de I/O real.
2. **Erro controlado, nunca truncamento silencioso.** Quem passa de um limite recebe um código explícito, e nada é cortado para caber.
3. **Atômico por operação.** Uma Action de dados aplica todo o seu efeito ou nenhum. Não existe escrita parcial em P0.
4. **Determinístico.** Para a mesma entrada, resposta byte-idêntica. O estado virtual nasce limpo em cada execução.

## 2. Três camadas de limite

| Camada | Quando | Efeito | Resposta |
| --- | --- | --- | --- |
| **A. Validação** | antes de executar (`/scenarios/validate`, `/simulations`) | nada executa | `valid=false` com `DATA_LIMIT_EXCEEDED` (ou 400 `INVALID_REQUEST` se o JSON viola o schema) |
| **B. Runtime de dados** | durante a execução, por Action | a Action falha sem efeito; as demais seguem as regras de dependência | `dataOutcomes[]` com `status=FAILED`/`TIMED_OUT`/`REJECTED`/`SKIPPED` e `errorCode` |
| **C. Envelope de execução** | relógio virtual ou eventos esgotados | a simulação inteira é encerrada, sem resultado parcial | HTTP 422 `EXECUTION_LIMIT_EXCEEDED` |

### 2.1 Camada A — limites de validação

| Onde | Campo | Mínimo | Máximo | Exemplo |
| --- | --- | --- | --- | --- |
| Recurso de dados | `initialRecordCount` | 0 | 1.000.000 | N6-06 |
| Recurso de dados | `averageRecordBytes` | 1 | 1.048.576 (1 MiB) | N6-06 |
| Recurso de dados | `initialRecordCount × averageRecordBytes` | — | 1.073.741.824 (1 GiB) | N6-06 |
| Recurso de dados | `readLatencyMs`, `writeLatencyMs` | 0 | 1.000.000.000 | — |
| Recurso de dados | `maxConcurrentOperations`, `connectionPoolSize` | 1 | 1.000 | — |
| Recurso de dados | `queueCapacity` | 0 | 10.000 | — |
| `ETL_PIPELINE` | `batchSize` | 1 | 100.000 | — |
| `ETL_PIPELINE` | `maxBatches` | 1 | 1.000 | — |
| `ETL_PIPELINE` | `batchSize × maxBatches` | — | 1.000.000 | N6-08 |
| `DATA_TRANSFORMER` | `transformCostPerRecordMs` | 0 | 1.000.000.000 | — |
| Action de dados | `recordCount`, `limit`, `batchSize` | 1 | 100.000 | N6-06 |
| Action de dados | `averageRecordBytes` | 1 | 1.048.576 | N6-06 |
| Action de dados | `maxRecords` | 1 | 1.000.000 | — |
| `ETL_TRANSFORM` `FILTER_VALID` | `invalidRecordCount` | 0 | 1.000.000 | — |

Os limites exatos são aceitos (N6-06, `exact-limits-are-valid`). O editor usa os mesmos números (`dataLimitsV6.ts`, testado contra o `openapi.yaml`).

### 2.2 Camada B — limites de runtime por recurso

Cada recurso tem, por execução, no máximo **1.000.000 registros lógicos** e **1 GiB lógico**. Uma escrita que ultrapassaria qualquer um deles termina com `LIMIT_EXCEEDED` / `DATA_LIMIT_EXCEEDED` e não altera o estado (N6-02, N6-03).

- `DB_PUT`/`DB_UPDATE` sobre chave existente descontam os bytes do registro anterior antes de comparar com o limite: substituir por um registro do mesmo tamanho cabe mesmo com o recurso cheio (N6-03).
- `DB_DELETE` devolve registros e bytes (N6-03).
- No ETL, lotes acima de `maxBatches` resultam em `ETL_BATCH_LIMIT_EXCEEDED`, e mais de 1.000.000 registros em `ETL_RECORD_BUDGET_EXCEEDED` (CT-6-10).

### 2.3 Camada C — envelope de execução

| Limite | Valor |
| --- | --- |
| Relógio virtual | 3.600.000 ms |
| Eventos de trace / eventos agendados | 50.000 |
| Recursos virtuais por execução | 1.000 |

Ultrapassar o envelope encerra a simulação com 422 `EXECUTION_LIMIT_EXCEEDED` (N6-07: um bulk de 100.000 registros com `writeLatencyMs=100` exigiria 10.000.000 ms).

## 3. Custo virtual

O relógio é inteiro, em milissegundos. Todos os custos somam no mesmo relógio (`UnifiedSimulationV41`).

| Etapa | Fórmula | Exemplo |
| --- | --- | --- |
| Mensagem em uma conexão | `latencyMs` da conexão, por mensagem (ida e volta) | N6-01 |
| Leitura (`DB_GET`, `DB_SELECT`, `DB_FIND`) | `readLatencyMs × recordCount` (padrão 1) | N6-01: `2 × 1 = 2` |
| Escrita (`DB_INSERT`, `DB_UPDATE`, `DB_DELETE`, `DB_PUT`) | `writeLatencyMs × 1` | N6-01: `3` |
| `DB_BULK_INSERT` | `writeLatencyMs × recordCount` | N6-01: `3 × 50 = 150` |
| Espera na fila | tempo entre a chegada e a obtenção de um slot `min(connectionPoolSize, maxConcurrentOperations)` | CT-6-04 |
| `ETL_EXTRACT` | `readLatencyMs × registros extraídos` | N6-08: `1 × 10 = 10` |
| `ETL_TRANSFORM` | `transformCostPerRecordMs × registros de entrada` | N6-08: `2 × 10 = 20` |
| `ETL_LOAD` | `writeLatencyMs × registros carregados` | N6-08: `1 × 7 = 7` |

**Bytes lógicos (`logicalBytes`):** a escrita informa `registros × averageRecordBytes` (o da Action ou, se ausente, o do recurso); a leitura informa `encontrados × averageRecordBytes do recurso`. Os bytes não alteram o tempo.

**Leitura sem chave:** o custo é o de `recordCount` registros (padrão 1), não o do número de registros encontrados. `readCount` é limitado por `limit` (padrão 100.000).

## 4. Atomicidade

| Operação | Regra | Exemplo |
| --- | --- | --- |
| `DB_INSERT`, `DB_PUT`, `DB_UPDATE`, `DB_DELETE` | Registro único: aplica tudo ou nada | N6-03, N6-04 |
| `DB_BULK_INSERT` | **Lote atômico:** se o lote inteiro não cabe, nada é gravado; `rejectedCount` = tamanho do lote e `writtenCount` = 0. O espaço exato é aceito. | N6-02 |
| `ETL_LOAD` | Carga atômica do lote recebido | CT-6-10 |
| Timeout (fila ou processamento) | A operação expirada não grava; o registro não fica visível | N6-05 |
| Falha a montante | `ETL_LOAD` fica `SKIPPED` (`ETL_UPSTREAM_FAILED`); as demais Actions dependentes ficam `FAILED` (`DEPENDENCY_FAILED`) sem `dataOutcome` | N6-04, N6-08, CT-6-06 |

`resultCode=PARTIAL` está reservado no contrato e **não é produzido em P0**. Escrita parcial exigiria uma nova decisão de produto e um campo explícito, como uma `failurePolicy`.

## 5. Estados de resultado

| `status` | `resultCode` | `errorCode` típico | Efeito no estado | Dependentes |
| --- | --- | --- | --- | --- |
| `SUCCEEDED` | `INSERTED`, `STORED`, `UPDATED`, `DELETED`, `FOUND`, `EXTRACTED`, `TRANSFORMED`, `LOADED` | — | aplicado | executam |
| `SUCCEEDED` | `NOT_FOUND` | — | nenhum (leitura sem resultado, ou UPDATE/DELETE de chave inexistente) | **executam** (N6-04) |
| `FAILED` | `FAILED` | `DUPLICATE_KEY`, `ETL_TRANSFORM_FAILED`, `ETL_INVALID_REJECTION_COUNT` | nenhum | `DEPENDENCY_FAILED` (N6-04) |
| `FAILED` | `LIMIT_EXCEEDED` | `DATA_LIMIT_EXCEEDED`, `ETL_BATCH_LIMIT_EXCEEDED`, `ETL_RECORD_BUDGET_EXCEEDED` | nenhum | `DEPENDENCY_FAILED` |
| `REJECTED` | `LIMIT_EXCEEDED` | `DATA_QUEUE_FULL` | nenhum; a operação não entra na fila | `DEPENDENCY_FAILED` |
| `TIMED_OUT` | `TIMED_OUT` | `DATA_TIMEOUT` | nenhum | `DEPENDENCY_FAILED` |
| `SKIPPED` | `FAILED` | `ETL_UPSTREAM_FAILED` | nenhum (somente `ETL_LOAD`) | seguem `SKIPPED` |

Todo `dataOutcome` aparece também como `ACTION_EVENT` no trace, com o mesmo objeto `outcome`, `operationId` e timestamp (V6-13). No trace, uma rejeição por fila aparece como `ACTION_EVENT` com `status=FAILED` e `cause=DATA_QUEUE_FULL`.

## 6. Valores padrão

| Origem | Valores |
| --- | --- |
| Novo recurso no editor | `initialRecordCount=0`, `averageRecordBytes=512`, `readLatencyMs=1`, `writeLatencyMs=1`, `maxConcurrentOperations=1`, `connectionPoolSize=1`, `queueCapacity=10` |
| `DATABASE` migrado | Iguais aos do novo recurso, exceto `readLatencyMs` e `writeLatencyMs`, que recebem o `processingMs` histórico |
| Novo `ETL_PIPELINE` | `batchSize=100`, `maxBatches=1` |
| Novo `DATA_TRANSFORMER` | `transformCostPerRecordMs=0` |
| Action de dados, campos omitidos | `collection="default"`, `recordCount=1`, `limit=100000`, `maxRecords=1000000`, `averageRecordBytes` = o do recurso, `timeoutMs` ausente/0 = sem prazo |
| Tamanho de lote ETL efetivo | `batchSize` da Action > `batchSize` do pipeline > 100.000 |

## 7. Exemplos normativos

| ID | Regra |
| --- | --- |
| N6-01 | Custo de leitura, escrita e bulk, com timestamps exatos |
| N6-02 | Bulk atômico no limite de registros; espaço exato aceito |
| N6-03 | Limite de bytes; substituição e DELETE liberam espaço |
| N6-04 | `NOT_FOUND` é sucesso; `DUPLICATE_KEY` propaga `DEPENDENCY_FAILED` |
| N6-05 | Timeout de processamento e de fila sem gravação |
| N6-06 | Orçamentos de validação acima do limite e no limite exato |
| N6-07 | Envelope de 3.600.000 ms → 422 `EXECUTION_LIMIT_EXCEEDED` |
| N6-08 | Custo e conservação no ETL; contagem inválida; orçamento do pipeline |
