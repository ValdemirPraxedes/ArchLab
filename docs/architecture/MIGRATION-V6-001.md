# MIGRATION-V6-001 — Migração explícita de `DATABASE` histórico (v0.6 P0)

> **Status:** normativo (V6-03, V6-14). Decisão 2 do responsável pelo produto (2026-11-07): converter com equivalência temporal.
> **Onde roda:** `POST /api/v1/architectures/migrate-v6` (backend, `LegacyDatabaseMigrationV6`) e a prévia de importação do editor (`migrationV6.ts`). As duas implementações seguem as mesmas regras, com os mesmos `path`/`reason`, e um teste do editor compara sua saída com a fixture CT-6-07 do backend. Nenhuma validação ou simulação comum migra nada implicitamente.

## 1. Regras de conversão

| Elemento histórico | Resultado canônico v0.6 | Por quê |
| --- | --- | --- |
| Componente `DATABASE` | `RELATIONAL_DATABASE`, com o mesmo `id` e `displayName` | Única equivalência P0 sem ambiguidade de modelo |
| `processingMs` | `readLatencyMs` = `writeLatencyMs` = `processingMs` | Mesmo custo por operação |
| Concorrência | `maxConcurrentOperations` = `connectionPoolSize` = 1000; `queueCapacity` = 10 | O `DATABASE` histórico não tinha fila: CALLs concorrentes eram processados em paralelo |
| Dados | `initialRecordCount` = 0; `averageRecordBytes` = 512 | O legado não modelava registros |
| Conexão `DATABASE_QUERY` | Mantida como está, inclusive `id`, latência e `queryText`/`responseText` | O texto é só representação da mensagem, sem semântica executável |
| Action `CALL` para essa conexão | `DB_SELECT` com o mesmo `id`, `connectionId` e `dependsOn`, mais `collection: "default"` | O `CALL` custava request + `processingMs` + response, e o `DB_SELECT` custa o mesmo (`readLatencyMs × 1`); `NOT_FOUND` é sucesso, como o `CALL` |
| Nó `API` (v0.5) | `SERVICE` (migração v0.5 existente, aplicada antes) | — |
| Documento | `schemaVersion: "0.6"` | Passa a ser canônico |

A entrada nunca é alterada: a conversão trabalha sobre uma cópia, e as Actions só são reescritas depois que todos os `DATABASE`s foram aceitos.

## 2. Casos que continuam bloqueados

| `reason` | Situação | Motivo |
| --- | --- | --- |
| `NON_QUERY_CONNECTION` | Conexão com destino em `DATABASE` que não é `DATABASE_QUERY` | Não há operação de dados equivalente a escolher automaticamente |
| `HISTORICAL_CALL_TIMEOUT` | `CALL` com `timeoutMs` > 0 | O prazo do `CALL` conta a ida e volta pela rede, e o de uma Action de dados conta só fila + processamento; converter mudaria o resultado |

O backend responde HTTP 400 `DATA_MIGRATION_AMBIGUOUS` com `violations[]` (`path`, `details.componentId`, `details.reason`). O editor lista todas as ambiguidades no painel de validação, e o canvas não é alterado (CT-6-08).

## 3. Relatório de conversão

`report.databaseIds` lista os componentes convertidos. `report.notes` traz uma linha por decisão aplicada, por exemplo:

- `orders: processingMs 7 mapped to read/write latency; empty initial records; 512 bytes/record; pool and concurrency 1000 (no historical contention)`
- `orders: service/handle/read CALL -> DB_SELECT (collection default; same virtual timing)`
- `orders: connection data keeps queryText/responseText as message representation (no executable semantics)`

O editor mostra as notas equivalentes, em português, na prévia, e só aplica a conversão depois da confirmação.

## 4. Garantia de equivalência (V6-14)

Para cada fixture `docs/api/examples/v0.6/migration/M6-XX.json`, o `LegacyMigrationEquivalenceV6Test`:

1. migra a arquitetura legada e compara `report` com `expectedReport`;
2. verifica que IDs de componentes, conexões (byte-idênticas), Behaviors, Actions e `dependsOn` foram preservados, e que não sobra `DATABASE` nem `CALL` para os bancos migrados;
3. simula o original e o migrado com o mesmo cenário e exige o mesmo `status`, `mainStatus`, `totalDurationMs` e `responseStatusCode`, além do **mesmo tempo em cada atividade da timeline** (tipo, componente, conexão, início e fim; só o nome da fase muda, de `DATABASE_PROCESSING` para `DATA_OPERATION`);
4. repete a simulação migrada até obter resposta idêntica.

| Fixture | Cenário |
| --- | --- |
| M6-01 | Uma chamada síncrona Service → DATABASE |
| M6-02 | Duas chamadas concorrentes ao mesmo DATABASE (sem fila) |
| M6-03 | Chamada aninhada via `API` legada, com `queryText`/`responseText` |
| M6-04 | Dois DATABASEs em sequência, com processamento local entre as chamadas |

O teste detecta regressões: com o pool reduzido a 1, o M6-02 passaria de 16 ms para 26 ms e falharia.

O que muda, de forma visível e esperada: a simulação migrada passa a expor `dataOutcomes` (`NOT_FOUND`, porque o banco começa vazio), e os eventos usam a fase `DATA_OPERATION` e o intent `DB_SELECT`.
