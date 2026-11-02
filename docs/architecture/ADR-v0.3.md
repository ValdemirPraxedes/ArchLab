# ArchLab v0.3 — Registro consolidado de decisões de arquitetura

**Status:** consolidação para revisão no repositório  
**Versão:** v0.3 (planejamento / implementação progressiva)  
**Branch-base:** `develop`  
**Tecnologia:** Java 25, Spring Boot 4, Maven multi-módulo, JUnit 5  
**Contrato normativo:** [OpenAPI v3](../api/openapi-v3.yaml)

> **Nota de rastreabilidade:** o planejamento anterior identificou o conjunto **ADR-018 a ADR-029** como aprovado, mas o texto individual original de cada ADR não estava versionado em `docs/architecture/ADR.md` na consulta que antecedeu esta consolidação. Os títulos e a distribuição dos temas entre números abaixo são **organização editorial desta consolidação**; não devem ser tratados como transcrição literal dos ADRs originais. As decisões concretas abaixo refletem os acordos v0.3 recuperados do planejamento e do contrato. Qualquer conflito deve ser resolvido confrontando o planejamento original antes do aceite definitivo.

## Contexto e princípios

A v0.1 modela um fluxo Client → API → Database. A v0.2 amplia o grafo com múltiplos Clients/APIs, chamadas síncronas e cenários sem Database. A v0.3 evolui para um laboratório de execução determinística capaz de representar fluxos síncronos e assíncronos com recursos, condições, branches, filas e telemetria.

**Restrições gerais obrigatórias:**

- Preservar as APIs v1 e v2, suas fixtures e os comportamentos aprovados, inclusive a regra RF-044 de overrides v2.
- Manter o motor no módulo Java puro (`archlab-simulation-core`), sem Spring nem dependência de relógio de parede; expor HTTP somente no módulo `archlab-api`.
- Descrever protocolos por semântica de comunicação, não por execução real de redes, SOAP, HTTP ou infraestrutura externa.
- Permitir testes reproduzíveis com o mesmo cenário, mesma configuração e mesma ordem de eventos.
- Distinguir estritamente *erro de validação*, *erro de execução da aplicação* e *falha simulada do cenário*.
- A primeira fatia executável contempla **Client, API, Database, Broker, Queue e Telemetry**; outros tipos não são automaticamente considerados implementados só porque o modelo pode representá-los.

## ADR-018 — Núcleo de simulação e isolamento por versão

**Decisão consolidada:** implementar o domínio e a execução v3 no core, sem alterar o significado de v1/v2. Adaptadores e endpoints HTTP v3 ficam separados dos contratos anteriores. Compartilhamentos de utilitários só são permitidos quando não alterarem comportamentos existentes.

**Consequências:** evolução incremental, possibilidade de regressão independente e custo adicional de versionamento.

**Verificação:** testes de regressão v1/v2 + builds existentes; golden fixtures e RF-044 preservados.

## ADR-019 — Modelo tipado de domínio e capacidades

**Decisão consolidada:** arquitetura v3 formada por componentes, recursos, propriedades tipadas, endpoints, operações, relações, estado inicial e referências explícitas. Validar que operações e comportamentos são compatíveis com a matriz de capacidades do tipo de componente, em vez de aceitar propriedades arbitrárias.

**Consequências:** falhas antecipadas no contrato, extensibilidade por tipos declarados e validação semântica no core.

**Verificação:** testes de referências inválidas, propriedades incompatíveis, endpoints, operações, recursos e capacidades dos seis componentes.

**Referências:** DM-V3-001, CM-V3-001 e schemas v3.

## ADR-020 — ExecutionPlan como descrição executável

**Decisão consolidada:** cada operação comportamental pode ser representada por um `ExecutionPlan` composto pelos **nove** tipos de passo: `PROCESS`, `CALL`, `PUBLISH`, `CONDITION`, `FORK`, `JOIN`, `DELAY`, `COMPLETE` e `FAIL`. Transições, destinos, limites e políticas devem ser validados antes de simular.

**Consequências:** fluxos complexos descritos por dados sem executar código arbitrário do usuário; o interpretador deve cuidar de cursores, branches e condições.

**Verificação:** testes por tipo de passo, validação de transições/referências e limites de execução; cobertura de quórum em JOIN.

**Referência:** EP-V3-001.

## ADR-021 — Relógio virtual e agendamento determinístico

**Decisão consolidada:** usar um scheduler discreto com **tempo virtual**, ordenação total estável, sequência de desempate e as **fases 0/10/20/30/40** definidas no planejamento SCH-V3-001. Não usar tempo real para decidir a ordem semântica. Empates, latências zero, deadline e `horizonAtMs` devem ser determinísticos.

**Consequências:** resultados reproduzíveis e comparáveis; é necessário explicitar semântica de fase e condição de parada.

**Verificação:** sequências de execução idênticas para entradas idênticas, testes de empate, zero-duration, horizon, limites e deadline.

**Referência:** SCH-V3-001.

## ADR-022 — Estado runtime explícito e transições legais

**Decisão consolidada:** modelar o ciclo de vida de ativações, interações, recursos, requisições, branches e trabalho pendente como estados explícitos; cada evento deve admitir apenas transições legalmente especificadas. Separar estado do domínio, decisão de dispatch e projeção pública.

**Consequências:** melhor diagnóstico, mas mais invariantes a manter e validar.

**Verificação:** transições válidas/ilegais, integração entre espera, resposta e encerramento, sem vazamento de estado parcial.

**Referência:** EV-V3-001.

## ADR-023 — Catálogo fechado de 32 eventos internos

**Decisão consolidada:** usar os **32 tipos de evento interno** previstos em EC-V3-001, com payload tipado e `eventType` discriminador. Cada evento precisa ter regras conhecidas de agendamento e dispatch. O trace público não deve ser confundido com esse catálogo interno.

**Consequências:** auditoria e replay determinísticos; mudanças no catálogo passam por revisão de contrato.

**Verificação:** roundtrip, discriminadores, exclusividade de variantes, evento → efeito, e cobertura de todos os tipos.

**Referências:** EC-V3-001 e `EventPayload` no OpenAPI.

## ADR-024 — Dispatch e commit lógico atômico

**Decisão consolidada:** pipeline `ScheduledEvent → DispatchDecision → EffectBatch → commit lógico → StateDelta + TraceRecord`. Validar o lote de efeitos e aplicar suas alterações como uma unidade lógica, evitando estados intermediários observáveis. Agendamentos derivados respeitam as fases e a ordem causal.

**Consequências:** necessidade de controlar IDs, counters, efeitos e erros de aplicação antes de confirmar mudanças.

**Verificação:** invariantes antes/depois, falha de dispatch sem commit parcial e ordenação estável em cenários concorrentes.

**Referência:** DT-V3-001.

## ADR-025 — Execução síncrona, condições, branches e mensageria

**Decisão consolidada:** chamadas síncronas bloqueiam a ativação solicitante conforme o plano; `FORK/JOIN` coordena branches de acordo com a política declarada; `PUBLISH` produz trabalho assíncrono representado por Broker/Queue, aceites, entregas, ACK, prazo e possíveis novas tentativas dentro das capacidades previstas. Nenhuma mensagem real de rede é enviada.

**Consequências:** a resposta da requisição principal pode ocorrer antes de terminar o trabalho em background; quóruns e timeouts devem ter regras não ambíguas.

**Verificação:** cenários Client/API/Database e Broker/Queue, respostas, timeout, quórum, ACK e trabalho remanescente.

**Referências:** EP-V3-001, CE-V3-001, EV-V3-001.

## ADR-026 — Resultado principal separado do estado global

**Decisão consolidada:** representar separadamente o desfecho da requisição principal e a conclusão global de todas as atividades da simulação. `RESPONDED` não implica `COMPLETED` global. Uma simulação pode estar `IN_PROGRESS` ao atingir horizonte/limite.

**Consequências:** a API e o frontend devem comunicar tempos e status específicos para principal e global, sem inferir conclusão por uma resposta HTTP simulada.

**Verificação:** cenário CE-REF-01: **resposta principal em 120 ms** e **conclusão global em 210 ms**; trabalho em background de 90 ms após a resposta.

**Referência:** RS-V3-001.

## ADR-027 — Trace, snapshots, deltas e replay

**Decisão consolidada:** armazenar/emitir trace público ordenado, `StateDelta` versionado e snapshots coerentes que permitam reconstruir o estado público. Distinguir **`PUBLIC_SNAPSHOT`** (projeção pública) de **`FULL_RUNTIME`** (estado suficiente para reexecução completa), incluindo para este último fila, cursores, timers, claims, policies, recursos, contadores e demais dependências da continuidade.

**Consequências:** reconstrução sintética de snapshots não demonstra replay verdadeiro do runtime; o teste deve exercitar o motor Java.

**Verificação:** estado reconstruído = projeção final; restart/replay determinístico para `FULL_RUNTIME`; integridade de IDs, versões e causalidade.

**Referência:** RS-V3-001.

## ADR-028 — Contrato HTTP e erros da API v3

**Decisão consolidada:** manter OpenAPI **3.1 / JSON Schema Draft 2020-12** com quatro operações:

1. `POST /api/v3/architectures/validate`
2. `POST /api/v3/execution-plans/validate`
3. `POST /api/v3/scenarios/validate`
4. `POST /api/v3/simulations`

Validações respondem HTTP 200 com `valid: true/false` conforme o contrato; simulação estruturalmente inválida usa HTTP 422; falha **simulada** durante execução válida é representada no corpo da resposta HTTP 200, sem mascará-la como HTTP 500 do servidor.

**Consequências:** mapeadores e adaptadores v3 com tratamento de erro consistente e versionamento explícito.

**Verificação:** OpenAPI lint, referências, typegen, testes HTTP e regressão v1/v2. Enquanto endpoints v3 não existirem, resposta HTTP 500 isolada não é evidência de regressão contratual v3.

**Referência:** API-V3-001 e [OpenAPI v3](../api/openapi-v3.yaml).

## ADR-029 — Entrega incremental, qualidade e evidência de aceite

**Decisão consolidada:** implementar backend primeiro, por dependências, em branches a partir de `develop`, com PRs para `develop`; `main` permanece estável e não recebe merges sem aprovação explícita de release. Adotar testes JUnit 5 no Java, integração com types gerados em TypeScript no frontend, fixtures de referência e validação arquitetural com UML/diagrama de sequência/justificativas.

**Consequências:** critérios de aceite por tarefa e evidência de regressão são parte da entrega, não trabalho posterior.

**Verificação:** BE-V3-01 a BE-V3-12 concluídas e rastreáveis; CI e build; contrato de endpoints; seis componentes; 9 steps; 32 eventos; golden fixtures; CE-REF-01.

## Regras de compatibilidade e não objetivos

- Não quebrar os cenários v0.1 e v0.2 nem substituir suas APIs por v3.
- Não considerar suportado um componente além da fatia Client/API/Database/Broker/Queue/Telemetry sem especificação e teste.
- Não introduzir tráfego real, infraestrutura externa, autenticação, persistência ou processamento distribuído real apenas por existirem componentes conceituais.
- Não exigir Python como dependência permanente do backend; eventuais scripts de validação do contrato são ferramentas transitórias.
- A especificação estática do OpenAPI não equivale à validação funcional do motor Java.

## Matriz de implementação

| Tarefas Trello | Foco | ADRs desta consolidação |
| --- | --- | --- |
| BE-V3-01 a 03 | bootstrap, modelo tipado, ExecutionPlan | 018–020 |
| BE-V3-04 a 06 | scheduler, runtime, eventos e dispatch | 021–024 |
| BE-V3-07 e 08 | chamadas, branches, publicação e filas | 020, 022, 025 |
| BE-V3-09 | trace, replay, métricas, resultado principal/global | 026, 027 |
| BE-V3-10 | validação semântica e HTTP v3 | 028 |
| BE-V3-11 e 12 | golden fixtures, regressão, integração e revisão | 018, 029 |

## Pendências antes de marcar o documento como fonte normativa definitiva

1. Localizar ou recuperar os **textos originais** dos ADRs 018–029 para conferir número ↔ decisão e detalhes operacionais. Este arquivo consolida decisões, mas **não garante** os títulos ou mapeamentos históricos.
2. Conferir o documento contra EP-V3-001, DM-V3-001, CM-V3-001, CE-V3-001, EV-V3-001, SCH-V3-001, EC-V3-001, DT-V3-001, RS-V3-001 e API-V3-001 quando suas versões normativas completas forem publicadas.
3. Resolver divergências em PR, com registro explícito de adendos e sem alterar silenciosamente o contrato aprovado.

## Documentação relacionada

- [ADRs históricos v0.1](ADR.md)
- [Contrato OpenAPI v3](../api/openapi-v3.yaml)
- [Portões de homologação do contrato v3](../v3/API-V3-008-validation-gates.md)
- [Inventário e evidências](../v3/API-V3-009-artifact-inventory.md)
- [Quadro Trello de implementação](https://trello.com/b/nGBgLrB9/archlab-desenvolvimento)
