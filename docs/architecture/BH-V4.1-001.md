# BH-V4.1-001 — Contrato normativo proposto para comportamentos de componentes

> Status: proposta em revisão. Companion de [EX-V4.1-001](EX-V4.1-001.md); **não** é alteração do OpenAPI canônico.
> Esta especificação resolve os pontos de comportamento para orientar o backend, mantendo adaptação legada pendente de prova.

## 1. Princípios

- Cada componente possui **zero ou mais** comportamentos associados a um gatilho; componente sem comportamento mantém semântica legada por adapter documentado.
- A escolha de qual ação é acionada é explícita, determinada por gatilho + match, não pela presença de uma conexão no grafo.
- A configuração é declarativa, finita, tipada, com dependências por IDs; não executar scripts do usuário.
- O perfil técnico é sempre obtido da conexão selecionada; um comportamento nunca redefine REST/Queue/WebSocket de forma conflitante.
- `REQUEST_RECEIVED`, `MESSAGE_CONSUMED`, `WS_MESSAGE_RECEIVED`, `SESSION_OPENED` são gatilhos **propostos**; payload completo deverá ser confrontado com `openapi.yaml`.

## 2. Contrato lógico

```text
Behavior {
  id: nonempty unique per component
  trigger: TriggerKind
  match: TriggerMatcher (discriminated by trigger)
  actions: Action[1..N] (finite)
}
Action {
  id: nonempty unique per behavior
  kind: ActionKind
  dependsOn?: actionId[]   // DAG, only earlier/later IDs existing in same behavior
  connectionId?: string
  params?: type-specific
  onError?: FAIL_OPERATION | CONTINUE | SKIP_DEPENDENTS
}
Entry {
  componentId: string
  connectionId?: string
  operationRef?: string
  eventKind?: string
  inputs?: object
}
```

`onError` defaults to FAIL_OPERATION for synchronous CALL and local PROCESS, and to explicit failure recording for async delivery; no silent retry. For a group of independent actions, their order is deterministic by declaration order then ID. A component may have multiple matchers for different resources. Ambiguous matchers that would fire more than one conflicting RESPONSE for a request must be rejected at validation, not resolved by chance.

## 3. Tipos de ação e compatibilidade

| kind | Required | Allowed context | Completion observable |
| --- | --- | --- | --- |
| PROCESS | durationMs or component default | any executing component | time elapsed |
| CALL | connectionId | API with valid outgoing request/response or legacy DB query | child response / fail / timeout |
| PUBLISH | connectionId, destination reference | allowed producer to Broker, Queue/Topic/MQTT | accepted / fail / timeout |
| RESPOND | response status or inherited default | request handling with active caller | response emitted |
| OPEN_SESSION | connectionId | WebSocket initiator to compatible recipient | OPEN or failure |
| WS_SEND | session reference, payload metadata | either authorized endpoint of OPEN session | message arrived or failed |
| CLOSE_SESSION | session reference | owner/authorized endpoint | CLOSED or failed |
| FORK | references to action IDs | finite behavior | children scheduled |
| JOIN | refs to child operations or actions | explicit fork/derived dependencies | all specified children terminal |

`CALL` to DB must retain database semantics; not force a fake HTTP integration into DB links. Missing matching trigger means no behavior execution, with explicit trace/no-handler status where relevant.

## 4. Conclusão e resposta

- Uma ação `RESPOND` identifica a conclusão do request corrente, mas a operação principal só termina ao efetivamente registrar o envio/retorno no relógio virtual conforme semântica do transporte.
- A publicação que retorna ACCEPTED pode ser seguida de RESPOND. O consumidor não precisa concluir antes da resposta.
- Uma ação `WS_SEND` não deve ser confundida com RESPONSE: não conclui automaticamente a requisição REST original.
- O término global abrange os filhos e recursos relevantes, mesmo que o response principal esteja terminal.
- `AUTO_CLOSE` de sessões é opção explícita e observável: evento de fechamento obrigatório. Não declarar que todo `WS_SEND` automaticamente fecha a sessão.

## 5. Exemplos canônicos de comportamento (pseudocontrato, NÃO OpenAPI executável)

### 5.1. REST recebe pedido e publica mensagem

```yaml
component: orders-api
behavior:
  trigger: REQUEST_RECEIVED
  match: {operation: "POST /pedidos"}
  actions:
    - {id: query, kind: CALL, connectionId: orders-database}
    - {id: send-event, kind: PUBLISH, connectionId: orders-broker,
       destination: pedidos, dependsOn: [query]}
    - {id: response, kind: RESPOND, status: 201, dependsOn: [send-event]}
```

### 5.2. Consumer emite WS_SEND

```yaml
component: notification-api
behavior:
  trigger: MESSAGE_CONSUMED
  match: {destination: pedidos}
  actions:
    - {id: notify, kind: WS_SEND, sessionRef: opened-session, payloadKind: PedidoCriado}
```

O cenário precisa incluir uma ação OPEN_SESSION anterior, ou uma operação de inicialização causal explícita que abre a sessão. O nome `opened-session` é apenas alias estável do recurso, não autorização para presumir sessão OPEN.

## 6. Validações mínimas e mensagens de erro propostas

| Código | Problema |
| --- | --- |
| INVALID_BEHAVIOR_TRIGGER | tipo de componente não aceita gatilho |
| AMBIGUOUS_BEHAVIOR_MATCH | mais de um comportamento incompatível disputa a mesma entrada |
| UNKNOWN_ACTION_KIND | ação inexistente |
| INVALID_ACTION_DEPENDENCY | dependsOn inexistente ou ciclo |
| INVALID_ACTION_CONNECTION | aresta ausente, direção inválida ou protocolo incompatível |
| INVALID_ACTION_CAPABILITY | ação incompatível com componente |
| INVALID_RESPONSE_CONTEXT | RESPOND sem contexto de request |
| INVALID_BROKER_DESTINATION | destino/assinatura inexistente ou incompatível |
| SESSION_NOT_OPEN | WS_SEND sem sessão OPEN |
| INVALID_SESSION_REFERENCE | sessão não vinculada à conexão/participante |
| UNBOUNDED_EXECUTION | ausência de condição finita para cadeia ou sessão |
| EXECUTION_LIMIT_EXCEEDED | orçamento de eventos/duração/recursos ultrapassado |

Códigos são propostas e devem ser reconciliados com o catálogo vigente de `Problem/Violation` antes de se tornarem públicos. Invalidade estática → erro de validação 4xx; falha dinâmica (sessão fechada por timeout, por exemplo) → evento/resultado da simulação. Não retornar 500 para entrada de domínio inválida.

## 7. Testes de contrato prioritários

1. Match de entrada e ações sequenciais em REST.
2. Duas ações independentes elegíveis no mesmo timestamp com ordem estável.
3. CALL espera retorno antes de PUBLISH dependente.
4. PUBLISH desbloqueia RESPOND no aceite; consumidores continuam em paralelo.
5. Broker entrega dois filhos distintos a dois assinantes elegíveis.
6. Consumer aciona WS_SEND somente com sessão aberta.
7. WS_SEND sem sessão gerado após timeout registra falha dinâmica.
8. Loops de dependência e match ambíguo são rejeitados na validação.
9. Snapshot antigo sem `behaviors` é atendido pelo adapter legado sem mudança de significado.
10. Sessão termina por CLOSE explícito ou política configurada e não prende global indefinidamente.

## 8. Gate para OpenAPI

O desenvolvedor backend deve reconciliar os nomes e tipos descritos com os enums de `IntegrationKindV2`, componentes reais (Client, API, Database, Broker), `ArchitectureV2` e `ScenarioV2`. Só então alterar `docs/api/openapi.yaml`, gerar tipos TypeScript, validar exemplos, e iniciar a etapa frontend.

NÃO alterar os contratos v0.4 vigentes nem declarar endpoints novos nesta etapa documental.
