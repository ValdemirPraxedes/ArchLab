# API-V4.1-001 — Plano de evolução de contrato para cenário unificado

> Status: **histórico de planejamento**. A implementação backend e as verificações do contrato estão em [BE-V4.1-IMPLEMENTATION](../architecture/BE-V4.1-IMPLEMENTATION.md) e [OPENAPI-V4.1-VALIDATION](OPENAPI-V4.1-VALIDATION.md). O contrato canônico vigente está em `docs/api/openapi.yaml`.
> Relacionados: [mvp-v0.4.1](../requirements/mvp-v0.4.1.md), [ADR-V4.1-001](../architecture/ADR-V4.1-001.md), [API-V4-002](API-V4-002.md).

## 1. Objetivo

A execução em `POST /api/v1/simulations` deve receber arquitetura com perfis diferentes por conexão e um único ponto de entrada, sem depender de `scenario.kind` global. Preservar `POST /api/v1/architectures/validate` e `POST /api/v1/scenarios/validate` enquanto o contrato final mantiver esses endpoints. Não criar `/api/v4.1`.

## 2. Proposta de payload conceitual (não enviar como DTO vigente)

```json
{
  "architecture": {
    "components": [
      { "id": "client", "type": "WEB_BROWSER" },
      { "id": "orders", "type": "API",
        "behaviors": [
          { "trigger": "REQUEST_RECEIVED", "match": {"path": "/pedidos"},
            "actions": [
              {"id": "db", "kind": "CALL", "connectionId": "db-link"},
              {"id": "publish", "kind": "PUBLISH", "connectionId": "queue-in", "dependsOn": ["db"]},
              {"id": "response", "kind": "RESPOND", "dependsOn": ["publish"], "status": 201}
            ]
          }
        ]
      },
      { "id": "database", "type": "DATABASE" },
      { "id": "broker", "type": "BROKER" },
      { "id": "notification", "type": "API",
        "behaviors": [
          { "trigger": "MESSAGE_CONSUMED", "match": {"destination": "orders"},
            "actions": [
              {"id": "notify", "kind": "WS_SEND", "connectionId": "notification-ws", "sessionRef": "client-notification-session"}
            ]
          }
        ]
      }
    ],
    "connections": [
      {"id":"client-orders", "sourceId":"client", "targetId":"orders", "integration":{"kind":"REST"}},
      {"id":"db-link", "sourceId":"orders", "targetId":"database"},
      {"id":"queue-in", "sourceId":"orders", "targetId":"broker", "integration":{"kind":"QUEUE"}},
      {"id":"queue-out", "sourceId":"broker", "targetId":"notification", "integration":{"kind":"QUEUE"}},
      {"id":"notification-ws", "sourceId":"client", "targetId":"notification", "integration":{"kind":"WEBSOCKET"}}
    ]
  },
  "scenario": {
    "entry": {"componentId":"client", "connectionId":"client-orders", "operation":"POST /pedidos"},
    "initialResources": [
      {"kind":"WEBSOCKET_SESSION", "connectionId":"notification-ws", "alias":"client-notification-session", "state":"OPEN"}
    ]
  }
}
```

**Atenção:** exemplo apenas ilustrativo; `type`, `integration.kind`, `destination`, `sessionRef`, `initialResources` e direções precisam ser conciliados com os discriminadores e a topologia canônica do OpenAPI v0.4. A sessão pré-aberta é **hipótese de modelagem**, não API aprovada. O contrato final não deve permitir materialização arbitrária de recursos sem eventos causais ou regra explícita.

## 3. Mudanças de contrato exigidas

| Tema | Requisito |
| --- | --- |
| Cenário | Novo formato com `entry` e sem `kind` global exigido. |
| Compatibilidade | Aceitar temporariamente variantes antigas mediante adapter explícito e resposta de migração diagnosticável. |
| Modelo de componente | Descrever triggers e ações semanticamente válidos por capacidade. |
| Conexão | Preservar integração discriminada por perfil com validações v0.4. |
| Sessão | Referência/identidade, abertura e término; nunca envio sem sessão OPEN. |
| Mensagem | ID, vínculo de causalidade, Broker/destino, entrega e consumidores. |
| Resultado | `mainStatus` e `globalStatus` claros; timeline correlacionada e eventos tipados. |
| Erro | Erros de trigger, ação, conexão, sessão, dependência, limite e migração identificáveis. |
| Versionamento | `/api/v1` permanece a base; atualizar schemas e clientes com migração controlada. |

## 4. Estratégia sugerida de compatibilidade

1. Mapear os três valores legados de `scenario.kind` para uma entrada e um plano de ações equivalente.
2. Compatibilidade deve preservar o resultado observável de chamadas, sessão e publicação; não converter silenciosamente `MESSAGE_PUBLISH` em chamada síncrona.
3. Se o payload antigo for ambíguo, devolver erro de migração com orientação e caminho para correção; não fabricar operações.
4. Testar importação/execução de amostras antigas para cada variante e cenários v0.1–v0.3.
5. Mudar frontend apenas após OpenAPI normativo, parse/validação e geração TypeScript.
6. Registrar depreciação do formato antigo e prazo/política de retirada antes de remover o adapter.

## 5. Condições de aceitação do OpenAPI

- Parse OpenAPI 3.1 e lint sem erros relevantes.
- Discriminadores de integração permanecem sem `oneOf` ambíguo.
- Schemas para ações, gatilhos, dependências e IDs com `required`, defaults, limites e rejeição de campos incompatíveis.
- Exemplos válidos de REST → Queue → WebSocket e WebSocket → REST → resposta WebSocket.
- Exemplos inválidos de perfil incompatível, sessão inexistente, action cycle e trigger desconhecido com erros 4xx tipados.
- Validação de exemplos, geração `openapi-typescript`, contrato Java e regressão dos endpoints públicos.
- Comportamento de conclusão de sessão persistente formalizado e testado.

## 6. Pendências para detalhar com backend

- Nomes exatos e cardinalidade dos campos `scenario.entry`, `behaviors`, `actions`.
- Modo de configurar a sessão e de ativar gatilhos de mensagem recebida.
- Dependências `CALL`/response e política de falhas.
- Orçamento de execução e validação estática de fluxo.
- Esquema de mensagens e associação entre bindings de Broker e consumidores.
- Compatibilidade com tipos de componentes reais e conexões DB legadas.

Este documento é um contrato de planejamento: **não afirma que o exemplo JSON é executável no backend atual**.


## 7. Revisão 2026-11-04 — alinhamento com `openapi.yaml`

A branch `docs/v0.4.1-mixed-communication-planning` recebeu um **rascunho OpenAPI v0.4.1** diretamente no contrato público canônico `docs/api/openapi.yaml`, sem adicionar rotas. As mudanças são apenas contratuais, **não implementadas no backend**.

### Decisões refletidas no schema

- `Scenario.oneOf` mantém os três legados `RequestResponseScenario`, `WebSocketScenario`, `MessagePublishScenario` e acrescenta `UnifiedScenario` sem `kind` global.
- O `discriminator` global de `Scenario` foi retirado porque o novo formato não possui `kind`. Os legados continuam identificados por valores `const`; a nova variante exige `entry`. O validador de `oneOf` deverá confirmar que os quatro formatos são disjuntos.
- `UnifiedScenario.entry` possui `componentId`, `connectionId` e `operation?`; `sessionSetup[]` abre sessões **por eventos virtuais explícitos**, não cria recursos OPEN arbitrariamente.
- `Api.behaviors[]` é opcional e não substitui o tratamento de payload antigo sem plano de comportamento.
- `BehaviorAction.oneOf` define `PROCESS`, `CALL`, `PUBLISH`, `RESPOND`, `OPEN_SESSION`, `WS_SEND`, `CLOSE_SESSION`, `FORK` e `JOIN` com discriminadores e campos obrigatórios específicos.
- Os identificadores reais de integração existentes no contrato continuam `HTTP_REST`, `SOAP_HTTP`, `DATABASE_QUERY`, `HTTP_GENERIC`, `GRAPHQL_HTTP`, `GRPC_UNARY`, `COAP`, `WEBSOCKET`, `MQTT`, `QUEUE`, `TOPIC`.
- O exemplo `mixedCommunicationRequest` demonstra REST → Broker Queue → WebSocket, com sessão preparada causalmente.

### Compatibilidade e lacunas bloqueantes

1. **O `CanonicalScenarioValidator` atual só entende duas modalidades v0.4 em seu fluxo próprio; o controlador possui outro caminho para requisição/resposta.** O backend terá de reconciliar ambos no adaptador; mudar apenas o YAML não habilita execução.
2. **O `Behavior` da primeira iteração foi anexado a `Api`.** Eventos recebidos por clientes e outras capacidades ainda precisam de refinamento para versões seguintes, sem romper as identidades visuais atuais.
3. **Sessão em `sessionSetup`**: a semântica de preparo, pré-operação e `autoCloseAtEnd` precisa de validação executável, inclusive tempo de abertura, timeout e fechamento antes da conclusão global.
4. **FORK/JOIN**: referências a ações locais são definidas estruturalmente; a referência causal a operações filhas, políticas de erro, budgets e waits precisam de detalhamento no backend e no validador.
5. **Sem prometer compatibilidade automática**: regras de migração de cenários antigos continuam requerendo testes e mensagens de erro explícitas.
6. **Validação OpenAPI ainda pendente**: rodar parser 3.1, Redocly, checagem de todos `$ref`, validar exemplos com schema, gerar `openapi-typescript` e testar mapeamento Java. A mudança de contrato não pode ser declarada pronta para consumo até concluir essa bateria.
7. **Limites**: verificar coerência de `DurationMs` legado (1 bilhão por propriedade) com limites globais de runtime (3.600.000ms) e políticas de `sessionSetup`.

### Gate de transição

O próximo passo técnico é completar a validação do schema OpenAPI em ambiente com parser e geração TypeScript e resolver discrepâncias. **Somente depois** preparar o trabalho backend, mantendo frontend para depois da entrega funcional e contratual.
