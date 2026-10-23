# ArchLab — Requisitos funcionais MVP v0.2

> **Status: aprovado para planejamento.** RF-026 a RF-043; ADR-009 a ADR-016 aprovadas; revisão cruzada aprovada. O MVP v0.1 permanece como histórico em [mvp-v0.1.md](mvp-v0.1.md).

## Objetivo

Laboratório visual de arquitetura de software e System Design para modelar sistemas, simular comportamentos determinísticos sob premissas configuráveis e analisar trace, tempo e mensagens. Resultados **não representam medidas nem previsões de produção**.

## Escopo e limites

- Grafo dirigido acíclico; múltiplos Client/API, Database opcional; componentes fora do cenário permitidos.
- Conexões: Client→API e API→API (HTTP/REST ou SOAP 1.2 sobre HTTP); API→Database (consulta abstrata).
- Uma requisição principal; cenário seleciona Client, conexão de entrada e uma dependência descendente por ativação de API, ou nenhuma.
- Execução síncrona, DES, relógio virtual, fila ordenada por timestamp e sequência; respostas retornam pelo enlace lógico invertido.
- Trace hierárquico, timeline dinâmica, métricas sem dupla contagem, conteúdo de mensagens ilustrativo; playback local.
- ID técnico estável e displayName editável; ícones por tipo; tema claro/escuro no frontend.
- **Fora do escopo:** concorrência, carga, filas/mensageria, ciclos, timeouts, retries, propagação automática de falhas, cache, persistência, autenticação, execução real HTTP/SOAP/SQL, WSDL/XSD.

## Requisitos e critérios de aceite

### RF-026 — Ícones por tipo

Client, API e Database com ícones consistentes na paleta e canvas; estados distinguíveis além da cor; sem impacto na simulação.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-012.

### RF-027 — Temas claro e escuro

Temas cobrindo editor, timeline, inspeção e resultados; preferência do sistema inicialmente; escolha manual persistida localmente; sem alterar métricas.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-012.

### RF-028 — Nomes personalizados

ID único e estável distinto de displayName obrigatório de até 80 caracteres Unicode; nomes duplicados aceitos; renomear não altera conexões ou trace.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-012.

### RF-029 — Múltiplos Clients

Arquitetura admite vários Clients; cenário escolhe um entryComponentId e entryConnectionId Client→API; demais Clients não executam.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-011, ADR-014.

### RF-030 — Múltiplas APIs

Várias APIs com tempos próprios; podem ter várias conexões possíveis, mas executar no máximo uma descendente por ativação; APIs inativas não contam.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-011.

### RF-031 — Chamada síncrona API→API

Executa pre→request→WAITING→response correlacionada→post→response; sem bloqueio real ou rede.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-011, ADR-013, ADR-016.

### RF-032 — Database opcional

Permitir zero Database e API sem chamada descendente; Database é terminal; apenas participantes contribuem para métricas.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-011.

### RF-033 — Validação de arquitetura e cenário

IDs únicos; direções Client→API, API→API, API→Database; conexão única por par; sem autochamadas/ciclos em TODO grafo; desconectados aceitos; cenário exige entrada e dependências válidas; backend autoritativo.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-011, ADR-014.

### RF-034 — Cadeias síncronas

Uma requisição principal, uma chamada descendente por API, nenhuma configuração significa resposta direta; ordenação determinística, inclusive durações zero; termina após chegada ao Client.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-011, ADR-013.

### RF-035 — Exchanges hierárquicas

requestId principal, exchangeId por interação, parentExchangeId, messageId individual; WAITING simultâneo nas APIs; resposta só conclui exchange ao chegar ao chamador; rejeitar resposta duplicada.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-013.

### RF-036 — Timeline dinâmica e reprodução

Atividades não fixas; timestampMs + sequence; pause/play/step/speed exclusivamente frontend; histórico e bloqueio de edição preservados.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-013, ADR-015.

### RF-037 — Métricas de execução

Total do envio inicial à resposta final; processamento próprio por componente, comunicação por conexão, espera por API/exchange, duração inclusiva; sem dupla contagem; análise rastreável.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-013, ADR-015.

### RF-038 — Modalidades de integração

HTTP/REST ou SOAP 1.2 sobre HTTP para Client→API/API→API; DATABASE_QUERY abstrata para API→DB; distinção protocolo/estilo; preservar rótulos v1.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-016.

### RF-039 — Representações HTTP/REST

Método GET/POST/PUT/PATCH/DELETE, path, headers, request body e status/headers/body de response; padrões utilizáveis; conteúdo passivo; status 4xx/5xx não falha motor.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-016.

### RF-040 — Representações SOAP

SOAP 1.2 sobre HTTP com operação, envelopes e metadados; XML passivo; sem WSDL/XSD, entidades externas ou execução de SOAP Fault.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-016.

### RF-041 — Consulta Database ilustrativa

Texto de consulta e resposta; sem executar SQL nem exigir SGBD específico; latência e processamento explicitamente configurados.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-016.

### RF-042 — Inspetor de mensagens

Inspecionar request/response, origem/destino, tempo, direção, intenção, requestId/exchangeId/messageId, parentesco e representação; exibição segura sem alterar reprodução.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-013, ADR-015, ADR-016.

### RF-043 — API HTTP v2 compatível

POST /api/v2/architectures/validate, /scenarios/validate, /simulations; execution/initialState/trace/timeline/metrics, snapshot de mensagens; OpenAPI 3.1; DTOs v1 intactos; núcleo comum.

**Aceite:** testes positivos e negativos para as regras acima; IDs e referencias corretos, com falhas reportadas em violações quando aplicável. **ADRs:** ADR-010, ADR-013, ADR-014, ADR-015, ADR-016.


## Regras transversais

1. Snapshot imutável por execução; simulações não compartilham estados mutáveis.
2. Tempos não negativos (zero permitido); validar limites e overflow; desempate por sequence.
3. Ciclos rejeitados no grafo completo; violação de cenário não modifica o diagrama.
4. Configuração de API ausente de apiExecutions significa responder diretamente.
5. Exchanges terminam na **chegada da resposta ao chamador**; só esse chamador retoma processamento.
6. Tempo total é a chegada da resposta final ao Client menos o instante do envio inicial. Esperas aninhadas se sobrepõem a outras atividades e não são somadas ao total.
7. Payloads HTTP/SOAP/SQL são dados passivos com limites e exibição segura; status HTTP 500 não é erro de motor.
8. A API v1 conserva endpoints, tipos e semântica do MVP histórico; a v2 utiliza o mesmo core por meio de adaptadores.
9. Frontend não reexecuta lógica de simulação; pause/velocidade/step não afetam resultados.

## Cenários integrados de aceite

| ID | Teste |
|---|---|
| S01 | Client→API sem Database; API não entra WAITING |
| S02 | Client→API→DB: 260 ms, espera 170 ms, 7 atividades |
| S03 | Client→API A→API B: resposta correlacionada |
| S04 | Client→API A→API B→DB: esperas aninhadas sem dupla contagem |
| S05 | Múltiplos Clients, apenas um iniciador |
| S06 | API com múltiplas conexões, apenas uma selecionada |
| S07 | Componentes desconectados estruturalmente válidos |
| S08 | Ciclo fora do cenário rejeitado |
| S09 | Alterações visuais não alteram tempo |
| S10 | HTTP 500 ilustrativo não falha motor |
| S11 | SOAP 1.2 inspecionável |
| S12 | SQL ilustrativo não executado |
| S13 | Durações zero determinísticas |
| S14 | Exchange incorreta/duplicada rejeitada |
| S15 | Regressão pública da API v1 |

## Rastreabilidade

| ADR | RFs principais |
|---|---|
| ADR-009 | 026, 028, 030, 037 |
| ADR-010 | 029, 030, 032, 033, 034 |
| ADR-011 | 029, 030, 031, 032, 034 |
| ADR-012 | 026, 027, 028 |
| ADR-013 | 031, 034, 035, 036, 037, 042 |
| ADR-014 | 029, 033, 043 |
| ADR-015 | 036, 037, 042 |
| ADR-016 | 031, 038, 039, 040, 041, 042, 043 |

## Contrato e testes

Referência proposta: [openapi-v2.yaml](../api/openapi-v2.yaml). Validar com ferramentas OpenAPI 3.1 e geração TypeScript. Os testes unitários e de integração devem cobrir ambas versões da API, formatos de erro 400/413/415/422/500 e os cenários S01–S15.

**Condição de conclusão:** RF-026 a RF-043 implementados e testados; regressões v1 passando; OpenAPI v2 alinhado ao código; execução backend concluída antes do frontend.
