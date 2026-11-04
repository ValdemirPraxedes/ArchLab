# ArchLab — Decisões de Arquitetura v0.4

> **Status:** decisões ADR-V4-001 a ADR-V4-006 aprovadas em 2026-11-04; detalhamento técnico/contrato pendente. Documento de planejamento: não autoriza alterações de implementação. Base: [Requisitos v0.4](../requirements/mvp-v0.4.md).

## Contexto

O ArchLab v0.3 consolidou seis tipos visuais de clientes reutilizando o comportamento Client. Na v0.4, os dez perfis aprovados precisam de três modalidades semânticas: requisição/resposta, sessão WebSocket e mensageria assíncrona por Broker. A evolução deve preservar o editor, o motor determinístico e cenários antigos.

O projeto possui `archlab-api` (Spring Boot/Java 25) e `archlab-simulation-core` (Java 25, sem Spring), com agendador, relógio virtual, transporte, estados e resultados. O contrato atualmente documentado tem endpoints `/api/v1` e `/api/v2`. **Não criar endpoints `/api/v4`.**

## ADR-V4-001 — Motor único e comportamentos semânticos especializados

**Decisão aprovada:** manter um único motor de simulação com um agendador e transporte compartilhados. Agrupar as regras de comportamento de acordo com a semântica de:
- **Requisição/resposta:** REST, SOAP, HTTP genérico, GraphQL (HTTP), gRPC unary e CoAP;
- **Sessão bidirecional:** WebSocket;
- **Mensageria mediada por Broker:** Queue, Topic e MQTT.

Reutilizar a execução de requisição/resposta já existente onde compatível; representar as diferenças relevantes por propriedades, eventos/metadados e estados observáveis. Não criar dez motores nem implementar protocolos de rede reais.

**Nomes de implementação candidatos (não normativos):** `RequestResponseBehavior`, `WebSocketBehavior`, `BrokerBehavior`. A decomposição definitiva dependerá do código existente.

**Consequências:** cada perfil produz resultados observáveis próprios sem duplicar infraestrutura; módulos de domínio não dependem do frontend ou de Spring.

## ADR-V4-002 — Agendador determinístico compartilhado

**Decisão aprovada:** preservar o relógio virtual e o agendamento discreto para mensagens de requisição/resposta, sessões WebSocket, publicações, filas, entregas e processamento assíncrono.

Todos os eventos, incluindo eventos derivados, precisam manter ordenação determinística mesmo com timestamps iguais. Não usar tempo de parede nem threads reais para modelar concorrência. Agendar ações futuras; jamais concluir a execução apenas porque um componente respondeu enquanto houver operações derivadas ou recursos relevantes ativos.

**Consequências:** a mesma arquitetura, parâmetros e cenário devem produzir sequência e resultados idênticos; os controles de playback do frontend continuam funcionando.

## ADR-V4-003 — Estados independentes por operação e recurso

**Decisão aprovada:** modelar estados independentes de requisições, mensagens/entregas, sessões WebSocket e execução global. Preservar a distinção entre **conclusão da operação principal** e **conclusão global**.

Exemplos de estados semânticos (não enums definitivos):
- Requisição: `CREATED → SENT → PROCESSING → COMPLETED`;
- Sessão WebSocket: `CONNECTING → OPEN → CLOSING → CLOSED`;
- Mensagem: `CREATED → ACCEPTED → QUEUED/ROUTED → DELIVERED → PROCESSED`, conforme perfil.

Falhas, timeouts e pendências encerradas devem ter resultado terminal explícito. A conclusão da publicação não cancela entregas em andamento; sessão WebSocket aberta impede encerramento global. Trace e métricas preservam causalidade, participantes e perfil.

**Consequências:** o motor precisará considerar recursos ativos e resultados terminais, não apenas fila vazia. Reutilizar identidades e estados já presentes quando possível.

## ADR-V4-004 — Broker como componente de domínio

**Decisão aprovada:** adicionar **um Broker genérico** como componente do cenário, sem serviço externo de mensageria.

O estado virtual de cada execução representa destinos lógicos (filas/tópicos), consumidores/assinaturas, mensagens pendentes, entregas e posição round-robin por fila. As regras são:
- **Queue:** uma entrega por mensagem a um consumidor elegível, escolha determinística;
- **Topic:** uma entrega lógica por assinatura elegível;
- **MQTT:** publicação/assinatura com correspondência exata de tópicos, QoS 0.

Uma Queue sem consumidor aguarda até o limite virtual e registra término explícito. Topic/MQTT sem assinantes registram publicação com zero entregas. Estado é mantido apenas durante a simulação. Não adicionar nós próprios de fila/tópico, Worker, armazenamento persistente, retry avançado, DLQ ou implementação de broker real.

**Consequências:** API existente pode consumir mensagens; dados de assinatura e ligação precisam de contrato validável sem duplicar configurações.

## ADR-V4-005 — Transporte virtual único e separado dos comportamentos

**Decisão aprovada:** manter o transporte responsável por identificar/validar a conexão aplicável, calcular latência, agendar entrega e registrar fatos do transporte; não realizar comunicação de rede real.

O comportamento do componente/perfil, não o transporte, decide o significado de uma chamada RPC, a validade da sessão WebSocket ou quais destinatários recebem uma publicação Topic/MQTT.

**Consequências:** latência, agendamento, rastreabilidade e validação podem ser compartilhados pelos três modelos, evitando regras de negócio dentro do transporte.

## ADR-V4-006 — API pública única sob /api/v1 (revisada e aprovada)

**Decisão aprovada:** o ArchLab manterá **somente a versão pública `/api/v1`**, consolidando os recursos das APIs v1 e v2 e as novas capacidades da v0.4. **Não criar APIs públicas `/api/v4` nem conservar `/api/v2` ao final da migração.**

A versão do produto (v0.4) **não determina a versão da rota da API**.

### Plano de consolidação

1. Inventariar endpoints, requests, responses, códigos de erro, funcionalidades, DTOs e consumidores existentes de `/api/v1` e `/api/v2`.
2. Definir um **contrato canônico v1** e avaliar mudanças de formato para preservar, sempre que viável, clientes existentes; documentar quaisquer incompatibilidades reais.
3. Consolidar `POST /api/v1/architectures/validate` e `POST /api/v1/simulations`, incluindo capacidades de grafos/cenários e simulação existentes; adicionar `POST /api/v1/scenarios/validate` para a funcionalidade correspondente da antiga v2, caso seja mantida como operação separada no desenho contratual.
4. Atualizar o OpenAPI canônico em **`docs/api/openapi.yaml`**. Não criar `openapi-v4.yaml` como contrato público. O histórico do arquivo `openapi-v2.yaml` permanece acessível pelo Git; sua retirada da árvore ativa ocorrerá apenas após concluir a migração.
5. Migrar chamadas do frontend e demais consumidores sob controle do projeto, e adaptar/aposentar os controllers/DTOs versionados de v2 sem descartar funcionalidades do motor.
6. Validar cenários legados (v0.1, v0.2, v0.3) e novos (v0.4) por testes de contrato, integração e regressão **antes de remover os endpoints `/api/v2/**`**.
7. Retirar rotas, handlers e documentação ativa de `/api/v2` **somente após** comprovação da migração; sinalizar incompatibilidades para consumidores externos, se identificados.

**Atenção:** uma rota com o mesmo prefixo `/api/v1` não é automaticamente retrocompatível se seu payload mudar. As mudanças incompatíveis devem ser identificadas e testadas, sem prometer compatibilidade inexistente. A retirada de `/api/v2` é uma decisão intencional de consolidação, não alteração silenciosa.

### Endpoints-alvo propostos para detalhamento de contrato

| Rota pública | Responsabilidade |
| --- | --- |
| `POST /api/v1/architectures/validate` | Validar arquitetura e compatibilidade de componentes/conexões |
| `POST /api/v1/scenarios/validate` | Validar cenário e operação inicial |
| `POST /api/v1/simulations` | Executar simulação e retornar trace, timeline, métricas e resultados |

As estruturas finais e a existência de variantes de compatibilidade serão especificadas na etapa OpenAPI; a tabela não aprova uma modelagem definitiva de DTO.

**Ordem de trabalho:** consolidar a API v1 no início da implementação de backend v0.4, com testes de migração, **antes de ampliar o motor com novos perfis**. Implementação do backend antes da integração correspondente no frontend.

## Prioridades de qualidade e restrições

- Backend primeiro; frontend somente após o contrato e a execução necessários estarem validados.
- Preservar fluidez do editor e os seis clientes específicos da v0.3.
- Evitar abstrações especulativas e ampliação para componentes fora do escopo.
- Exigir testes determinísticos, regressão, defesa técnica, UML e diagramas de sequência fiéis ao código.
- Planejamento e documentação na branch `docs/v0.4-communication-profiles-planning`; abrir PR **somente ao final**, mediante solicitação do responsável.

**Especificação de contrato vinculada:** [API-V4-001 — Contrato canônico da API pública v1](../api/API-V4-001.md), com diretrizes aprovadas e pendências normativas explícitas. A aprovação deste documento **não autoriza alterar imediatamente** `docs/api/openapi.yaml` ou remover rotas existentes.

**Detalhamento aprovado na Etapa 10:** [DM-V4-001 — Modelo de domínio](DM-V4-001.md) e [VAL-V4-001 — Validação e limites](VAL-V4-001.md). Ambos registram invariantes e a separação de estados, mas mantêm classes Java finais, schemas OpenAPI e valores numéricos pendentes.

**Contrato e limites aprovados na Etapa 11:** [API-V4-002](../api/API-V4-002.md). Os valores numéricos, defaults, discriminadores e resultados globais ali aprovados complementam as ADRs; falta convertê-los em OpenAPI normativo validado e testar compatibilidade.

## Pendências para a próxima etapa

- Inventário detalhado e reconciliação de esquemas v1/v2, identificando colisões e campos incompatíveis.
- Contrato canônico OpenAPI v1 para perfis, Broker, WebSocket, cenários, trace e métricas.
- Identificadores e DTOs para correlação, recursos ativos e conclusão principal/global.
- Validações formais, limites numéricos, ordenação, timeouts e compatibilidade de migração.
- Tarefas pequenas e ordenadas de backend, depois frontend.
