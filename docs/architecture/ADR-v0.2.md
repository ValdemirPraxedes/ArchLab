# ArchLab — Decisões arquiteturais v0.2

> **Estado:** ADR-009 a ADR-016 aprovadas para planejamento, com revisão cruzada aprovada. Histórico técnico v0.1 em [ADR.md](ADR.md). Contrato de referência em [openapi-v2.yaml](../api/openapi-v2.yaml).

## Contexto técnico

Permanecem Java 25, Spring Boot 4 (archlab-api), archlab-simulation-core Java puro, React/TypeScript e motor DES. Novo contrato v2 e evoluções no core não alteram o comportamento público de v1.

## ADR-009 — Posicionamento e níveis de modelagem

**Status: aprovada.**

**Decisão:** Aplicações, serviços e dependências, inspirado no nível container do C4, sem alegar conformidade integral. Modelagem, simulação DES determinística e análise rastreável; resultados não predizem produção.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-010 — Arquitetura como grafo dirigido

**Status: aprovada.**

**Decisão:** Grafo DAG tipado; múltiplos Client/API e Database opcional; conexões Client→API, API→API e API→DB; uma conexão por par; ciclos no grafo completo inválidos. Cenário seleciona Client, aresta inicial e dependências; replies invertem mesma aresta.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-011 — Comportamentos e chamadas síncronas

**Status: aprovada.**

**Decisão:** API executa pre, zero/uma chamada descendente por ativação, espera correlacionada, post e resposta. Conexão é possibilidade, não obrigação; ausência de seleção significa resposta direta.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-012 — Identidade e apresentação

**Status: aprovada.**

**Decisão:** id estável e único, displayName independente (duplicatas permitidas, não vazio, até 80 caracteres), ícones definidos por tipo no frontend; tema claro/escuro por tokens semânticos e preferência local; nada visual afeta motor.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-013 — Contexto, estados e correlação

**Status: aprovada.**

**Decisão:** Um requestId raiz, exchangeId por par request/response, parentExchangeId, messageId individual. Estados separados COMPONENT/REQUEST/EXCHANGE; várias APIs em WAITING; resposta somente libera chamador ao chegar; sequence desempata; contexto isolado.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-014 — Contratos HTTP v2 e compatibilidade

**Status: aprovada.**

**Decisão:** Novos POST /api/v2/architectures/validate, /api/v2/scenarios/validate e /api/v2/simulations. Entradas Architecture e Scenario separadas; manter /api/v1, DTOs e respostas compatíveis; core compartilhado via adaptadores, Problem Details RFC9457.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-015 — Métricas e análise

**Status: aprovada.**

**Decisão:** Trace é fonte de verdade para timeline e métricas; processamento próprio, duração inclusiva, comunicação por conexão, espera por API/exchange, duração total. Não somar esperas sobrepostas; análises explicativas apenas com evidência.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.

## ADR-016 — Protocolos e mensagens

**Status: aprovada.**

**Decisão:** HTTP protocolo aplicação, REST estilo arquitetural, SOAP 1.2 protocolo de mensagem, TCP transporte. Integrações HTTP_REST, SOAP_HTTP e DATABASE_QUERY com templates request/response ilustrativos e inspector; sem tráfego real, execução de payload ou efeito temporal.

**Consequências:** aplicar no modelo de domínio, validação, DTOs e testes da v0.2, sem ampliar escopo para simulações concorrentes, rede real ou persistência.


## Revisão cruzada aprovada — seis refinamentos

1. Scenario.entryComponentId e entryConnectionId obrigatórios; apiExecutions somente com chamadas descendentes; ausência significa resposta direta; duplicatas rejeitadas; configurações válidas para APIs inativas não executam.
2. Uma definição de interação por conexão lógica; templates associados à integração; snapshot de representações executadas disponível no resultado para inspeção independente do editor.
3. Exchange conclui na chegada da resposta ao chamador (não no envio); API COMPLETED após pós-processar e enviar a resposta; Client conclui após recebimento final.
4. Métricas: total envio inicial→chegada final; processamento próprio soma dos intervalos ativos; espera descendente do envio request até chegada response; comunicação apenas travessias; duração inclusiva da API desde chegada request até envio response.
5. V1 preservada por adaptadores; testes garantem contratos públicos e semântica, não apenas duração 260 ms.
6. Erros estruturais, de cenário e de representação distinguidos; payload opcional não exige WSDL/XSD nem execução; status 500 de exemplo não representa falha de motor.

## Invariantes e compatibilidade

- Uma requisição principal por execução; grafo acíclico completo; APIs fazem no máximo uma chamada descendente por ativação.
- Todas as mudanças de estado geram trace determinístico, com timestampMs e sequence; respostas só terminam a exchange correspondente.
- V1 mantém os endpoints /api/v1/architectures/validate e /api/v1/simulations e seus DTOs; conservar cenário 260 ms, espera 170 ms, timeline 7 atividades.
- Introduzir API v2 com três endpoints e Problem Details; payloads representados nunca executados.
- Frontend controla reprodução, não altera o motor; ícones, tema e posição não são campos do núcleo.
- Não introduzir paralelismo, tráfego real, autenticação, cache, persistência, timeout/retry ou falha simulada propagada.

## Ligações

- [Requisitos MVP v0.2](../requirements/mvp-v0.2.md)
- [OpenAPI v2](../api/openapi-v2.yaml)
- [Requisitos históricos v0.1](../requirements/mvp-v0.1.md)
- [ADRs históricas 001–008](ADR.md)

## ADR-017 — Overrides de processamento por conexão de entrada (2026-11-01)

**Status: aprovada como ajuste da v0.2 durante validação do sistema.**

**Contexto.** O backend e frontend v0.2 já possuem tempos de pré/pós-processamento obrigatórios no componente `ApiV2`. Com múltiplas APIs e conexões, uma mesma API pode receber chamadas de custo distinto conforme a origem/interação. Mover os campos obrigatoriamente para todas as conexões quebraria clientes e arquiteturas já existentes.

**Decisão.** Adotar um modelo híbrido, sem alterar os campos obrigatórios atuais do componente:

- `ApiV2.preProcessingMs` e `ApiV2.postProcessingMs` continuam representando o **padrão** do componente.
- `ConnectionV2.apiProcessingOverride` é **opcional**, permitido apenas em arestas `Client→API` e `API→API`. Contém dois valores obrigatórios (`preProcessingMs`, `postProcessingMs`), não negativos, aplicados atomicamente à **API de destino na ativação pela conexão de entrada**.
- Se ausente, usa os tempos padrão da API; não há mistura de valores individuais entre conexão e componente.
- O processamento continua registrado em `PROCESSING_EVENT` da API, com fases `API_PRE_PROCESSING` e `API_POST_PROCESSING`. Comunicação é registrada na conexão; espera é atribuída à API chamadora; métricas próprias pertencem à API destinatária. A referência à conexão de entrada poderá ser obtida do exchange e trace existentes, sem exigir novo tipo de evento.
- DB continua configurado por `processingMs` no componente. Overrides em conexões `API→Database` são inválidos.
- `ScenarioV2` continua escolhendo as conexões, sem novos campos; cada ativação determina os tempos efetivos pela conexão que trouxe o request.
- O contrato `/api/v1` e seus DTOs permanecem intactos; requisições `/api/v2` sem override continuam válidas, conservando resultados precedentes.

**Alternativas rejeitadas.** (1) Mover completamente os tempos para as conexões: mudança incompatível e duplicação obrigatória de configuração. (2) Associar tempo ao transporte: semanticamente incorreto. (3) Introduzir catálogo de operações por API: abrangência excessiva para ajuste corretivo da v0.2.

**Consequências e ordem de implementação.** Primeiro backend (DTO, validação, resolução do valor efetivo, trace/métricas e testes); depois frontend (types, painel da conexão, fallback explícito, editor e testes integrados). Não fazer merge nem avançar Frontend antes de validação da alteração Backend.

**Exemplo.** API B padrão pré=20/pós=10; A→B override pré=40/pós=15; C→B override pré=10/pós=5. O tempo de comunicação em cada ligação permanece `latencyMs`; somente o PROCESSING da API B varia conforme a chamada.

**Critérios de regressão.** Regressão pública v1 260/170/7; fluxos v2 sem override idênticos; override em conexão inválida rejeitado; WAITING e correlação idênticos; cálculo do total e métricas respeitam duração efetiva, sem atribuir processamento à conexão.

**Rastreabilidade:** RF-030, RF-031, RF-037, RF-044; ADR-011, ADR-013, ADR-015, ADR-017.
