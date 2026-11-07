# PLAN-V6-001 — Plano de entrega e gates da v0.6

> **Status:** plano proposto. Nenhuma etapa autoriza implementação sem revisão e aprovação do responsável pelo produto.

## Sequenciamento

| Etapa | Entregáveis | Gate |
| --- | --- | --- |
| V6-01 — Planejamento | `mvp-v0.6.md`, `ADR-V6-001.md`, `DM-V6-001.md`, este plano | Aprovar P0, migração e limites |
| V6-02 — Contrato | OpenAPI canônico, matriz normativa Actions × tipos, exemplos e validações | Contract review aprovado |
| V6-03 — Core P0 | Estado virtual de dados, comportamento determinístico e métricas | Testes unitários e regressão |
| V6-04 — API | Serialização tipada, validador e adaptador de importação legado | Contract tests e compatibilidade |
| V6-05 — Frontend | Paleta específica, formulários, ETL, prévia de migração e timeline | Testes UI, integração e build |
| V6-06 — Homologação | CT-6-01 a CT-6-10, cenários limite, documentação e demos | Aceite explícito |
| V6-07 — Backlog P1/P2 | Refinamento de bancos especializados, índices, locks, stream/CDC | Novo escopo aprovado |

## Sugestão de divisão em tarefas

1. Congelar inventário P0 e capacidades por tipo.
2. Definir migração `DATABASE` com prévia, diagnóstico e casos de equivalência.
3. Definir DTOs discriminados e erros normativos no OpenAPI.
4. Implementar recurso virtual para relational/document/key-value com limites e métricas.
5. Implementar CRUD e DB_GET/PUT/FIND/BULK tipados.
6. Implementar pipeline EXTRACT/TRANSFORM/LOAD com batch e rastreabilidade.
7. Atualizar validador e serialização no backend.
8. Atualizar UI e editor contextual, incluindo migração segura.
9. Atualizar timeline/playback e testes integrados.
10. Executar teste de regressão de arquivos v0.1–v0.5 e critérios de aceite.

## Critérios de Done

- `DATABASE` não pode ser criado nem exportado em novas arquiteturas.
- Cenários legados válidos têm conversão explícita documentada ou erro acionável, sem perda silenciosa.
- Toda Action pode ser validada por tipo de recurso e cada resultado está correlacionado a seu disparador.
- Cenários com múltiplas operações produzem state/trace determinísticos com orçamento finito.
- Nenhum banco, API ou arquivo real é acessado.
- Lint, testes, builds, OpenAPI e integração aprovados.
- PRs para `develop`; nenhum merge automático sem aprovação.

## Decisões em aberto

1. Aprovar conjunto P0 exato e quais tipos P1/P2 ficarão só documentados.
2. Aprovar defaults da migração de DATABASE e comportamento de arquivos ambíguos.
3. Escolher políticas de fila e consistência de escrita/leitura.
4. Fixar tetos numéricos e parâmetros de custos virtuais.
5. Aprovar nomenclatura e formatos do contrato público.
