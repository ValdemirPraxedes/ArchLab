# Relatório consolidado — backend v0.3

Escopo: os 12 cards BE-V3-01..12 do quadro ArchLab | Desenvolvimento. Base develop; decisões técnicas e merges intermediários autorizados pelo usuário. Entrega executada em ordem de dependências, com PR independente por card, Java25/SpringBoot4/Maven/JUnit5, core puro e APIs v1/v2 preservadas. Os textos completos dos cards foram consultados; o OpenAPI e o ADR consolidado fornecem os contratos versionados. As notas históricas de ausência em documentos anteriores não representam o estado desta entrega.

## Cards, commits e PRs

Cada PR lista o diff e seus checks. Commits locais e publicados por conector podem ter SHAs distintos; a árvore remota foi comparada exatamente com a árvore Git local antes de abrir cada PR.

| Card | Entrega e aceite | Commit local / publicado | PR develop | Testes no marco |
|---|---|---|---|---|
| 01 | Fundação, valores, módulos e isolamento |3dcf72c / e638db4|[#50](https://github.com/ValdemirPraxedes/ArchLab/pull/50)|129|
| 02 | Seis tipos, recursos, endpoints, propriedades e domínio |7761052 / e2aea6d|[#52](https://github.com/ValdemirPraxedes/ArchLab/pull/52)|168|
| 03 | Nove steps e validator de plano |4b464b8 /155f125|[#53](https://github.com/ValdemirPraxedes/ArchLab/pull/53)|202|
| 04 | Scheduler causal determinístico |4a80e43 /5ddadfd|[#54](https://github.com/ValdemirPraxedes/ArchLab/pull/54)|213|
| 05 | Estados, transições e32eventos tipados |1215ff1 /0c17b2b|[#55](https://github.com/ValdemirPraxedes/ArchLab/pull/55)|251|
| 06 | Dispatcher/EffectBatch atômico e claims |3523982 /33c2080|[#56](https://github.com/ValdemirPraxedes/ArchLab/pull/56)|265|
| 07 | Interpretador, RPC, condições, delays e outcomes |be1a90e /c952c7a|[#57](https://github.com/ValdemirPraxedes/ArchLab/pull/57)|277|
| 08 | FORK/JOIN, filas, broker, publicação, TTL/retry/ACK |51d65b9+0459106 /b92e601|[#58](https://github.com/ValdemirPraxedes/ArchLab/pull/58)|298|
| 09 | Trace, replay público, métricas e integridade |b958330 /6337954|[#59](https://github.com/ValdemirPraxedes/ArchLab/pull/59)|305|
| 10 | Quatro endpoints, admissão e Problem Details |9f15ec2 /a7f9c2f|[#60](https://github.com/ValdemirPraxedes/ArchLab/pull/60)|317|
| 11 | Golden, matriz32eventos/9steps, contratos reais |1784b11 /7cf5b9a|[#61](https://github.com/ValdemirPraxedes/ArchLab/pull/61)|327|
| 12 | UML/sequência, rastreabilidade e cliente/schema TS v3 |[commits da entrega final](https://github.com/ValdemirPraxedes/ArchLab/pull/62/commits)|[#62](https://github.com/ValdemirPraxedes/ArchLab/pull/62)|327 backend +86 frontend|

## Aceite por componente

| Componente | Comportamento verificado |
|---|---|
| Client | Entrada, disponibilidade inicial, resposta120ms, deadline/falha/cancelamento, estado parcial |
| API | Nove steps, chamadas aninhadas/sem espera, condições, limites, delays, ativação independente de branch |
| Database | Native DATABASE_QUERY, processing40ms na referência, pool/claims FIFO, liberação e indisponibilidade |
| Broker | Admissão atômica, TOPIC fanout, rejeição/filas cheias, PUBLISH NONE/BROKER_ACK/CONSUMER_ACK |
| Queue | Reserva/enqueue/concurrency, delivery/ACK, TTL, retry/backoff/dead-letter, mensagens iniciais, recuperação |
| Telemetry | Native TELEMETRY_COLLECT, pool, processamento90ms, ACK e continuação após resposta principal |

CE-REF-01: principal120ms, global210ms e background90ms. Horizonte150: somente30ms de Telemetry observados, final global ainda aberto. FORK/JOIN libera e drena separadamente; contexto de branch, claims/versões, IDs e sequência são determinísticos. Nenhuma dependência externa de infraestrutura é necessária para executar esses testes.

## Evidências finais

- Java25 Maven clean verify:327 JUnit (215 core/112 API), zero falhas, erros ou skips. Suítes integradas não mockam o core.
- ExecutionMatrixV3Test:14 execuções reais cobrem todos32 eventos e nove steps; cada cenário verifica retomada intermediária, replay interno e público.
- GoldenResultsV3Test: três fixtures versionadas preservam digest de snapshot, trace e resultado completo para sucesso, falha e horizonte.
- Ajv2020/OpenAPI:51 artefatos/fixtures validados, incluindo JSON de respostas reais, estados parciais e32payloads.
- v1:260ms,170ms de espera e sete atividades; v2: grafos/chamadas/protocolos/representações e overrides RF-044 preservados.
- Frontend: lint, typegen dos três contratos,86 testes e build TypeScript/Vite passaram; schema-v3.d.ts é gerado, não editado manualmente.
- Integração Node contra jar Java25 real: scripts v1 e v3 passaram. API temporária foi encerrada ao final.
- Redocly lint OpenAPI v3 válido, três avisos preexistentes explicitamente ignorados.

CI relevante: [BE09 Backend41394959181](https://github.com/ValdemirPraxedes/ArchLab/actions/runs/41394959181), [BE09 OpenAPI41394959238](https://github.com/ValdemirPraxedes/ArchLab/actions/runs/41394959238), [BE10 Backend41395393459](https://github.com/ValdemirPraxedes/ArchLab/actions/runs/41395393459), [BE11 Backend41396139927](https://github.com/ValdemirPraxedes/ArchLab/actions/runs/41396139927), [BE11 OpenAPI41396139847](https://github.com/ValdemirPraxedes/ArchLab/actions/runs/41396139847), [checks finais da PR62](https://github.com/ValdemirPraxedes/ArchLab/pull/62/checks). O merge final só ocorre depois de todos os checks relevantes passarem.

Logs locais completos em C:/Users/valde/Documents/ChatGPT/ArchLab/logs: be-v3-11-verify.log, be-v3-11-contract.log, be-v3-12-frontend.log, be-v3-12-typegen.log, be-v3-12-integration.log e be-v3-12-openapi-lint.log. Relatórios JUnit e JSON reais estão nos target dos módulos após verify. Trello recebe em cada card evidência de PR/commit/testes/CI e é movido a Concluído somente após os portões passarem.

## Limites e pendências

Não há pendência bloqueante dos critérios dos12cards no perfil inicial. O resultado HTTP exporta PUBLIC_SNAPSHOT; checkpoint integral é uma API Java interna, com fingerprint e retomada/replay testados. Não há exportação HTTP FULL_RUNTIME, e suas flags de reconstrução integral/playback permanecem false. Os caminhos críticos usam a alternativa permitida unavailableReason, sem durações calculadas. Políticas e propriedades suportadas/limites de admissão estão documentados em BE-V3-02..10. Essas limitações são explícitas no resultado e não são declaradas como recursos implementados.

O ADR-v0.3.md é uma consolidação editorial: conferir os textos históricos originais quando forem disponibilizados continua sendo rastreabilidade documental, não uma prova inventada de sua recuperação. Nenhuma integração em main, release, autenticação, infraestrutura externa ou redesign do editor foi realizada. A validação do usuário é solicitada apenas após todos os12cards integrados e respectivos checks verdes.

Detalhes finais e diagramas: [BE-V3-12-architecture-review.md](BE-V3-12-architecture-review.md). Contrato: [openapi-v3.yaml](../api/openapi-v3.yaml). Fixture executável: [ce-ref-01-request.json](../examples/v3/ce-ref-01-request.json).
