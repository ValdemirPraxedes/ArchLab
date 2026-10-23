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
