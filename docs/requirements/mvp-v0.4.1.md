# ArchLab v0.4.1 — Requisitos de cenários com comunicação mista

> Status: referência de requisitos. Backend incorporado em `develop`; frontend implementado na branch `feat/v0.4.1-frontend-unified` para revisão.
> Base: v0.4 em `develop`, especialmente [mvp-v0.4](mvp-v0.4.md), [DM-V4-001](../architecture/DM-V4-001.md), [API-V4-002](../api/API-V4-002.md) e [openapi.yaml](../api/openapi.yaml).

## 1. Problema e objetivo

A v0.4 apresenta ao usuário modalidades globais `REQUEST_RESPONSE`, `WEBSOCKET_SESSION` e `MESSAGE_PUBLISH`. Isso limita a experiência de modelar uma arquitetura na qual uma mesma operação transita entre perfis diferentes.

**Objetivo de produto:** o usuário monta uma única arquitetura com REST, SOAP, HTTP genérico, GraphQL, gRPC unary, CoAP, WebSocket, Queue, Topic e MQTT nas conexões compatíveis, indica onde iniciar a execução e observa todas as operações causadas por essa entrada numa única simulação correlacionada.

O perfil pertence à **conexão**; o comportamento invocado pertence ao **componente**; o cenário de execução especifica **ponto de entrada e parâmetros**, sem impor um tipo global de protocolo.

## 2. Decisões de experiência

- Remover do fluxo principal do editor o seletor obrigatório de tipo de cenário.
- Selecionar um componente e sua operação inicial (ou a conexão de entrada, conforme o caso); não inferir automaticamente que toda conexão de saída será acionada.
- Configurar protocolo e seus atributos ao selecionar uma conexão, mantendo a matriz de compatibilidade v0.4.
- Configurar ações ordenadas ou com dependências no componente, com padrão mínimo utilizável e sem obrigar linguagem de programação ou editor BPMN.
- Preservar arrastar, conectar, renomear, remover, zoom, seleção e fluidez de movimento. Não recriar o editor nem duplicar nós para cada protocolo.
- Apresentar timeline única e inspeção de cada requisição, publicação, entrega, sessão e mensagem.
- Preservar uma operação principal por execução; permitir operações derivadas finitas e concorrentes.

## 3. Requisitos funcionais

| ID | Requisito | Critério de aceitação |
| --- | --- | --- |
| RF-4.1-001 | Arquitetura mista | REST, WebSocket e Queue/Topic/MQTT podem coexistir na mesma arquitetura e integrar uma execução causal sem trocar de cenário. |
| RF-4.1-002 | Ponto de entrada | O usuário escolhe origem/operação inicial válida; sem `kind` global obrigatório na UX. |
| RF-4.1-003 | Perfis por conexão | Compatibilidade e propriedades específicas dos dez perfis permanecem válidas. |
| RF-4.1-004 | Comportamento por componente | Entrada pode disparar etapas locais, chamadas de saída, publicações, respostas e mensagens de sessão; ordem/dependências explícitas. |
| RF-4.1-005 | Chamada síncrona | Chamador só avança nas etapas dependentes após resposta ou falha/timeout da operação chamada. |
| RF-4.1-006 | Mensageria assíncrona | Aceite pelo Broker e entrega/consumo são operações distintas; consumidor pode disparar nova chamada REST ou mensagem WebSocket. |
| RF-4.1-007 | Sessão WebSocket | Sessão tem abertura, mensagens bidirecionais, encerramento e identidade próprios; envio requer sessão ativa. |
| RF-4.1-008 | Correlação | Toda operação derivada possui causa, execução, componente, conexão e perfil; mensagens guardam identificador e origem. |
| RF-4.1-009 | Conclusão principal/global | Resposta principal pode terminar antes de entregas derivadas; execução global aguarda recursos relevantes atingirem condição terminal. |
| RF-4.1-010 | Determinismo | Mesmo grafo e mesmas propriedades/seed, com relógio virtual, produzem eventos equivalentes na mesma ordem. |
| RF-4.1-011 | Falhas | Falhas, timeouts, mensagens sem consumidor e ausência de sessão têm estados e motivos explícitos sem corromper resultados já concluídos. |
| RF-4.1-012 | Resultados | Trace e timeline mostram encadeamento causal completo, latências, processamento, espera e métricas por perfil. |
| RF-4.1-013 | Compatibilidade | Cenários v0.1–v0.4 válidos têm migração explícita, avisos verificáveis e testes de regressão; não há conversão semântica silenciosa. |
| RF-4.1-014 | UX | Editor não perde fluidez por conta da configuração; campos avançados são contextuais e não poluem o painel. |
| RF-4.1-015 | Limites | Execução respeita orçamento finito de eventos, duração, profundidade e recursos, sem ciclos infinitos. |
| RF-4.1-016 | API única | Preservar superfície pública `/api/v1`; alterações incompatíveis precisam de estratégia documentada. |

## 4. Exemplo normativo de comportamento esperado

Arquitetura:
```text
Cliente --REST--> API Pedidos --REST/consulta--> Database
                       |
                    Queue publish
                       v
                     Broker --Queue delivery--> API Notificação
                                                       |
                                                WebSocket SEND
                                                       v
                                               Cliente (sessão ativa)
```

A sessão Cliente ↔ API Notificação deve ter sido aberta explicitamente antes do envio, ou ter sua abertura programada como ação prévia. Não se permite criar uma sessão implicitamente ao tentar enviar mensagem.

Sequência ilustrativa:
1. Cliente dispara `POST /pedidos` na API Pedidos.
2. API Pedidos consulta o banco e espera retorno.
3. API Pedidos publica `PedidoCriado` no Broker e recebe aceite.
4. API Pedidos responde HTTP 201; operação principal conclui.
5. Broker entrega `PedidoCriado` à API Notificação.
6. Consumidor executa ações configuradas e envia mensagem numa sessão WebSocket ativa.
7. Operações derivadas e recursos ativos são finalizados; conclusão global é registrada.

Os tempos não são prescritos; dependem do relógio virtual e das propriedades do cenário. Um envio WebSocket com sessão ausente deve falhar de maneira observável, sem inventar entrega.

## 5. Requisitos não funcionais e exclusões

- Reutilizar scheduler/dispatcher/trace existentes onde possível. Nenhum transporte real, WebSocket real ou Broker externo.
- Não introduzir linguagem de script, BPMN, loops ilimitados, QoS avançado, DLQ ou reconexão automática nesta correção.
- Manter a matriz de conexões da v0.4; expansão de tipos de nó é backlog independente.
- Manter identidade visual dos seis clientes existentes e compatibilidade de configurações.
- Operações não relacionadas à operação inicial não são executadas só por estarem conectadas.

## 6. Casos de teste mínimos

| ID | Cenário | Expectativa |
| --- | --- | --- |
| CT-01 | Cliente → API → Database por REST | Regressão síncrona sem alterações semânticas. |
| CT-02 | Cliente → API A (REST) → API B (gRPC unary) | Duas conexões com perfis diferentes, resposta correlacionada. |
| CT-03 | REST → API → Queue Broker → consumidor | Resposta principal antes do fim do consumidor, se configuração permitir. |
| CT-04 | REST → Topic Broker → dois consumidores | Duas entregas independentes em uma execução. |
| CT-05 | REST → Broker → consumidor → WebSocket | Envio em sessão ativa e conclusão global após processamento/encerramento relevante. |
| CT-06 | WebSocket → API → REST → WebSocket | A mensagem de sessão desencadeia chamada e nova mensagem sem exigir uma segunda simulação. |
| CT-07 | Broker sem consumidor elegível | Política de terminalização/pendência explícita; execução finita. |
| CT-08 | WebSocket SEND sem sessão OPEN | Erro de validação/executável explícito; não criar sessão silenciosamente. |
| CT-09 | Consumidor falha após HTTP 201 | Resposta principal continua concluída; status global expõe erro parcial. |
| CT-10 | Replay do mesmo cenário | Mesma ordem e timestamps virtuais esperados. |
| CT-11 | Arquivo legado de cada variante v0.4 | Resultado equivalente após migração documentada ou erro de migração acionável. |
| CT-12 | Editor com várias conexões e perfis | Movimentação, seleção, edição e zoom sem regressão. |

## 7. Entregas e gates

1. **Planejamento** (este PR): requisitos, decisão arquitetural e contratos conceituais; revisão antes de implementar.
2. **Backend domínio/validação:** plano de ações, causalidade, topologias e compatibilidade de payload legado.
3. **Backend scheduler/execução/resultados:** operações mistas, correlação, conclusão principal/global e testes determinísticos.
4. **API /api/v1:** atualizar OpenAPI canônico, validar schemas e exemplos, geração TS e testes de contrato.
5. **Frontend:** configuração contextual de ações e conexões, ponto de entrada único, painel de execução correlacionada.
6. **Integração:** CT-01 a CT-12, regressões v0.1–v0.4, revisão de UX.

**Bloqueio de início de código:** fechar esquema de `ComponentBehavior`, política de sessões e compatibilidade dos payloads legados no contrato de API. Nenhuma alteração de código está autorizada por este documento isolado.
