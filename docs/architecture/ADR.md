# ArchLab — Registro de Decisões de Arquitetura (ADRs)

> Escopo técnico do **MVP v0.1**. Estado: decisões arquiteturais acordadas, contratos e classes exemplificativos sujeitos a refinamento antes da implementação. Atualizado em 2026-10-20.

## Visão geral

```text
React + TypeScript (editor, animação, playback, painel)
                  |
               REST/JSON
                  |
Spring Boot 4 / Java 25 (archlab-api)
                  |
        archlab-simulation-core
      (Java 25 puro, sem Spring)
             |      |      |
           eventos relógio transporte
                  |
        trace + timeline + métricas
```

Backend: Maven multi-module, monólito modular. Nenhum banco, broker ou WebSocket é necessário no MVP. A API calcula toda a simulação e entrega resultado para reprodução local no navegador.

## ADR-001 — Stack e topologia

**Decisão:** frontend React/TypeScript; backend Java 25 + Spring Boot 4; REST/JSON; monólito modular e simulação de eventos discretos (DES). Sem persistência inicial.

**Motivo:** manter API/execução em Java, com domínio independente da interface e extensível para simulações mais elaboradas. Custo aceito: maior configuração do backend que alternativa frontend-only.

**Consequências:** frontend não avança o motor; apenas reproduz resultados. Spring serve como adaptador HTTP. Maven pai gerencia versões; módulos de código `archlab-simulation-core` e `archlab-api`.

## ADR-002 — Tipos explícitos e comportamentos desacoplados

**Decisão:** modelos explícitos `ClientComponent`, `ApiComponent`, `DatabaseComponent`, possivelmente `record` e `sealed interface Component`. Processadores de comportamento separados: `ClientBehavior`, `ApiBehavior`, `DatabaseBehavior`; registro de resolução `BehaviorRegistry`.

**Motivo:** propriedades válidas por tipo, sem grande condicional central nem conhecimento de Spring. `sealed` funciona para conjunto controlado de tipos, mas precisará ser repensado para plugins dinâmicos.

**Invariante:** componentes/modelo de domínio não conhecem motor, infraestrutura HTTP ou canvas; comportamentos interagem pelo `SimulationContext`.

## ADR-003 — Eventos discretos e relógio virtual

**Decisão:** motor processa acontecimentos pontuais agendados em `EventQueue` (por exemplo `PriorityQueue`) ordenada por `scheduledAtMs` e sequência crescente para desempate. `VirtualClock` usa milissegundos inteiros (`long`) e avança diretamente para o próximo evento.

**Exemplos de eventos:** `RequestSent`, `RequestArrived`, `ProcessingStarted`, `ProcessingCompleted`, `ResponseSent`, `ResponseArrived`, `SimulationCompleted`. Agendar somente transições futuras; fatos instantâneos podem ser registrados diretamente no trace.

**Invariantes:** tempo nunca retrocede; durações negativas são inválidas, zero é permitido; somas detectam overflow; motor tem limite de segurança contra loop de eventos; fim bem-sucedido exige evento terminal, não simplesmente fila vazia. Um motor pode produzir vários registros de trace por evento agendado.

## ADR-004 — Transporte simulado centralizado

**Decisão:** `SimulationTransport` localiza conexão no snapshot, verifica direção e validade, aplica latência, registra envio e agenda chegada na fila via contexto. Nunca invoca diretamente o destinatário nem efetua rede real.

**Responsabilidades:** `ClientBehavior`, `ApiBehavior` e `DatabaseBehavior` executam lógica própria; o transporte cuida da comunicação virtual. Respostas percorrem conexão lógica no sentido inverso somente quando correlacionadas a uma solicitação válida.

**Exemplo:** consulta API→DB enviada em 50 ms, com latência 10 ms, chega em 60 ms. Database responde em 210 ms; resposta chega à API em 220 ms, quando a API sai do estado de espera. A resposta final chega ao Client em 260 ms.

## ADR-005 — Mensagens com intenção semântica

**Decisão:** separar `MessageDirection` (`REQUEST`/`RESPONSE`) de `MessageIntent` (`API_REQUEST`/`DATABASE_QUERY`). Protocolo REST/SOAP/TCP é propriedade da conexão, nunca da mensagem. Não simular payload ou semântica de HTTP/TCP real.

**Modelo proposto:** `SimulationMessage(id, requestId, exchangeId, sourceId, targetId, direction, intent)`. `requestId` correlaciona a solicitação principal; `exchangeId` correlaciona um par de mensagem de ida/volta. Identificadores de mensagens distintas não se repetem.

| Caminho | Direção | Intenção |
|---|---|---|
| Client → API | REQUEST | API_REQUEST |
| API → Database | REQUEST | DATABASE_QUERY |
| Database → API | RESPONSE | DATABASE_QUERY |
| API → Client | RESPONSE | API_REQUEST |

**Regra:** transporte não interpreta a intenção; comportamento destinatário valida combinações de intenção/direção e completa apenas a interação pendente correspondente.

## ADR-006 — Estados separados

**Decisão:** toda execução possui `ExecutionState` privado com três mapas independentes:

- **ComponentRuntimeState** por `componentId`: estados ilustrativos `IDLE`, `PROCESSING`, `WAITING`, `COMPLETED`. É visão agregada do MVP, não a fonte única de concorrência futura.
- **RequestRuntimeState** por `requestId`: `CREATED`, `IN_PROGRESS`, `WAITING_DEPENDENCY`, `COMPLETED`; `FAILED` reservado à evolução.
- **ExchangeRuntimeState** por `exchangeId`: `CREATED`, `WAITING_RESPONSE`, `COMPLETED`.

**Regras:** toda transição validada passa pelo contexto e gera trace; resposta com `exchangeId` diferente ou duplicada não conclui interação; API pós-processa somente depois de `ResponseArrived`; requisição principal só conclui após chegada da resposta ao Client. Duas execuções nunca compartilham mapas mutáveis.

## ADR-007 — Trace por transições e contrato de resultados

**Decisão:** histórico ordenado e imutável de fatos, separado da fila futura. Registros de `StateTransitionTrace`, `MessageTrace` e `ProcessingTrace`, cada um com `sequence` e `timestampMs`. Transição inclui entidade, estado anterior, novo estado e causa. Entradas ordenadas por tempo virtual e sequência. Snapshot inicial + transições permite reconstruir estado em qualquer instante.

**Reconstrução:** por timestamp considera todas as transições daquele instante; por evento pode usar `sequence` para exibir estado intermediário. Trace registra fatos pontuais; `TimelineProjector` deriva intervalos início/fim/duração; `SimulationMetrics` agrega tempos sem dupla contagem.

**Contrato HTTP proposto (não congelado):** `SimulationResult` com `execution`, `initialState`, `trace`, `timeline` e `metrics`. Frontend usa trace para estados, timeline para animação e métricas para relatório, sem executar regras do domínio. DTOs da API são independentes dos modelos internos do core.

**Exemplo de métricas do cenário base:** total 260 ms; API pré 30, API pós 20, DB 150, comunicação 60, espera bloqueante API 170 (sobreposta, não somar novamente).


## ADR-008 — Contratos HTTP, validação e execução

**Status: aprovado em 2026-10-20.**

**Decisão:** disponibilizar dois endpoints REST com `Content-Type: application/json`, ambos recebendo o mesmo `ArchitectureRequest`:

- `POST /api/v1/architectures/validate`: verifica entrada e regras de domínio, **sem iniciar o motor**.
- `POST /api/v1/simulations`: valida novamente a arquitetura, cria snapshot imutável, executa o motor e devolve o resultado completo.

**Entrada compartilhada:** lista de `components` e `connections`. Os tipos de componente são `CLIENT`, `API` e `DATABASE`; Client requer ID e tipo; API possui `preProcessingMs` e `postProcessingMs`; Database possui `processingMs`. Conexão possui `id`, `sourceId`, `targetId`, `protocol` e `latencyMs`. Protocolos aceitos: REST/SOAP em Client→API e TCP simplificado em API→Database. Durações em milissegundos inteiros, sem valores negativos. IDs não vazios e únicos. Coordenadas do canvas não integram o contrato. A API recebe tempos explicitamente; o valor padrão zero pode ser aplicado pelo editor.

**Validação:** entrada HTTP e formato JSON são responsabilidade da camada web/DTO. Regras de topologia, contagem de componentes, unicidade, compatibilidade de conexões e durações pertencem ao core. O frontend pode executar verificações imediatas para UX, mas o core é a autoridade.

**Respostas:**

| Operação | Situação | HTTP | Corpo |
|---|---|---|---|
| Validar | Domínio válido | 200 | `{ "valid": true, "violations": [] }` |
| Validar | Domínio inválido | 200 | `{ "valid": false, "violations": [...] }` |
| Simular | Execução concluída | 200 | `SimulationResult` completo |
| Simular | Domínio inválido | 422 | Problem Details com `violations` |
| Ambas | JSON inválido ou tipos/formato ilegíveis | 400 | Problem Details |
| Ambas | Tipo de mídia não suportado | 415 | Problem Details |
| Ambas | Corpo além do limite configurado | 413 | Problem Details |
| Simular | Falha inesperada do motor | 500 | Problem Details sem dados sensíveis |

**Erros:** adotar Problem Details (RFC 9457) com `type`, `title`, `status`, `detail`, `instance`, código estável em `code` e, quando aplicável, lista `violations` com `code`, `path`, `message` e detalhes opcionais. Não depender de domínio público não registrado para URIs de tipo de problema.

**Resultado completo:** conforme ADR-007, conterá `execution`, `initialState`, `trace`, `timeline` e `metrics`. A API usa DTOs separados dos records internos do core, incluindo discriminadores de tipo nos componentes e nas variantes de trace. Não há persistência nem `GET /simulations/{id}` no MVP. Pausa, velocidade, navegação, inspeção e reinício da reprodução são locais ao frontend, sem endpoints adicionais.

**Consequências:** controle de erros e contratos versionados sob `/api/v1`; possibilidade de documentar a superfície com OpenAPI 3.1 e executar testes de integração/contrato. Endpoints definidos; detalhes finos de DTO, limites numéricos, schema exato de trace e estrutura de violações serão consolidados na especificação OpenAPI.

## Organização do repositório proposta

```text
archlab/
  frontend/                         # React / TypeScript
  backend/
    pom.xml                         # Maven parent
    archlab-simulation-core/        # domínio, validator, engine, events, clock,
                                    # behavior, transport, execution, trace, result
    archlab-api/                    # Spring Boot: REST controllers, DTOs, mappers,
                                    # use cases, configuração
  docs/
    requirements/mvp-v0.1.md
    architecture/ADR.md
```

Dependência permitida: **archlab-api → archlab-simulation-core**; core não importa Spring, HTTP, React nem infraestrutura. Testes unitários JUnit do core não inicializam contexto Spring.

## Critérios técnicos de referência

- Simulação determinística e sem relógio de parede; cenário de referência retorna 260 ms.
- Evento de mesmo timestamp tem desempate por sequência.
- Latência zero não quebra ordenação nem cria loop.
- API permanece aguardando de 50 a 220 ms; início de pós-processamento só após chegada da resposta.
- Trace reconstrói estados e timeline de sete atividades; resultados consistentes com mesmo snapshot.
- Erros de validação de diagrama são distintos de falhas internas do motor, e distintos de falhas **simuladas** (estas fora do escopo v0.1).

## Pontos ainda por detalhar

1. Contratos HTTP finais, DTOs, status de erro e versionamento da API.
2. Valores-limite de duração/eventos e formas de diagnóstico.
3. Política exata de estado agregado de componente em cenários concorrentes futuros.
4. Tipos Java e assinaturas finais, ainda ilustrativas; não implementar abstrações sem necessidade.

## Documentação relacionada

[Requisitos do MVP v0.1](../requirements/mvp-v0.1.md).
