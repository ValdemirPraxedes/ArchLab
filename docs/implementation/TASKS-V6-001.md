# TASKS-V6-001 — Backlog implementável da v0.6

> **Status:** tarefas de planejamento, **não iniciadas**. Aprovação de direção de produto não substitui aprovação do OpenAPI normativo.
> **Fontes:** [mvp-v0.6](../requirements/mvp-v0.6.md), [API-V6-001](../api/API-V6-001.md), [BE-V6-001](../architecture/BE-V6-001.md), [FE-V6-001](../architecture/FE-V6-001.md), [UI-V6-001](../architecture/UI-V6-001.md).

## Critérios globais

- Apenas P0 na primeira implementação: Relational, Document, Key-Value, ETL Pipeline e Data Transformer.
- Toda Action executada tem semântica real no estado virtual da mesma simulação, não somente ícone/latência decorativa.
- Preservar `/api/v1`, motor DES e regressões de versões anteriores.
- Cada tarefa exige testes e exemplo validável; nenhuma alteração na `develop` sem PR aprovado.

## Fase A — Contrato e decisões bloqueantes

| ID | Tarefa | Dependência | Aceite |
| --- | --- | --- | --- |
| V6-01 | Consolidar matriz tipos × Actions e compatibilidade de conexão | Planejamento | Matriz fechada, com rejeições documentadas |
| V6-02 | Fixar budgets, custo virtual, atomicidade e estados de resultado | V6-01 | Exemplos normativos e casos-limite |
| V6-03 | Aprovar migração `DATABASE` e `DATABASE_QUERY` histórica | V6-01 | Fixture anterior + relatório de conversão esperado |
| V6-04 | Atualizar OpenAPI 3.1 e fixtures válidas/inválidas | V6-01..03 | Validação de schema, `oneOf`, `$ref`, lint |
| V6-05 | Gerar TS e conferir DTOs Java contra OpenAPI | V6-04 | Contratos coerentes sem nova rota de API |

**Gate A:** contrato aprovado antes de código em runtime/frontend.

## Fase B — Backend e runtime P0

| ID | Tarefa | Dependência | Aceite |
| --- | --- | --- | --- |
| V6-06 | Adicionar discriminadores, capabilities e validação dos tipos P0 | V6-05 | Action incompatível rejeitada |
| V6-07 | Implementar estado virtual de dados por execução e limites | V6-06 | Reprodutibilidade/isolamento |
| V6-08 | Implementar runtime Relational CRUD/BULK | V6-07 | Insert→Select, update/delete e batch |
| V6-09 | Implementar runtime Document GET/PUT/FIND/UPDATE/DELETE | V6-07 | Filtro limitado e resultado ordenado |
| V6-10 | Implementar runtime Key-Value GET/PUT/DELETE | V6-07 | Miss/Put/Hit; sem TTL implícito |
| V6-11 | Modelar pool, slots, fila e timeout virtual | V6-07 | Duas chamadas em pool 1 geram espera e/ou timeout |
| V6-12 | Implementar EXTRACT/TRANSFORM/LOAD por batch | V6-08..10 | Conservação de registros; falha impede load |
| V6-13 | Expor `dataOutcomes` sem alterar resultados antigos | V6-08..12 | Trace, métricas e causalidade íntegros |
| V6-14 | Adaptador legado de `DATABASE` + testes de regressão | V6-06,V6-13 | Preserva IDs, conexões e operações equivalentes |

**Gate B:** backend P0 integrado, testes determinísticos, limites e compatibilidade aprovados.

## Fase C — Frontend P0

| ID | Tarefa | Dependência | Aceite |
| --- | --- | --- | --- |
| V6-15 | Atualizar TypeScript, `ComponentIcon` e paleta | V6-05,V6-06 | P0 criável, DATABASE ausente, Lucide testado |
| V6-16 | Criar formulários contextuais dos recursos de dados | V6-15 | Campos P0 validados |
| V6-17 | Atualizar editor de Actions/ETL com filtros declarativos | V6-15,V6-12 | Somente combinações compatíveis |
| V6-18 | Implementar prévia de migração e confirmação/cancelamento | V6-14,V6-15 | Nenhum canvas alterado em erro/cancelamento |
| V6-19 | Exibir `dataOutcomes` no playback | V6-13,V6-15 | Contadores, bytes e causas corretos |
| V6-20 | Implementar export JSON canônico para round-trip | V6-18 | Export/import sem DATABASE e sem perda de IDs |

**Gate C:** build/lint/testes frontend e integração completa aprovados.

## Fase D — Homologação e fechamento

| ID | Tarefa | Dependência | Aceite |
| --- | --- | --- | --- |
| V6-21 | Escrever fixtures CT-6-01..10 com cenários válidos e inválidos | V6-13,V6-20 | Replay documentado |
| V6-22 | Regressão v0.1–v0.5 em API e frontend | V6-21 | Nenhuma mudança silenciosa |
| V6-23 | Validar performance/limites e perfil de memória | V6-21 | Finitude e erros controlados |
| V6-24 | Revisar documentação, migrações, demo e checklist DoD | V6-22,V6-23 | Aprovação final do responsável pelo produto |

## Estratégia de PRs

- PR A: decisões de contrato e OpenAPI.
- PR B: core P0 e migração backend (pode subdividir por tipo).
- PR C: frontend P0, migração, ícones e playback.
- PR D: integração, regressão e documentação de aceite.

Nenhum merge automático, nenhuma implementação P1/P2 sem nova aprovação.

## Backlog posterior — não bloquear P0

- P1: Column-Family, Data Warehouse, Batch Processor, Data Ingestion, Data Quality, índices, transações.
- P2: Graph, Time-Series, Data Lake, Stream Processor, CDC, locks avançados, explain/aggregation.
- Evolução futura: calibração com métricas reais e infraestrutura/deployment.

## Definition of Done por tarefa

Cada tarefa entrega: modificação localizada, validação de entradas, limites, testes de unidade, integração causal quando aplicável, documentação dos defaults e regressão relevante. Não marcar concluída somente por renderizar ícone ou aceitar JSON.
