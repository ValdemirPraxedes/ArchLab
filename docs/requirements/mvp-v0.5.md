# ArchLab v0.5 — Expansão do catálogo de componentes de serviço

> **Status:** proposta inicial para revisão de produto e arquitetura. Não autoriza implementação ou merge.
> **Base:** `develop` após v0.4.1; [catálogo previamente selecionado](v0.3-services.md); [comunicação mista](mvp-v0.4.1.md).
> **Objetivo:** oferecer tipos de serviço reais e distintos na paleta visual, aproveitando Actions, Behaviors e motor determinístico único.

## 1. Problema e premissas

A v0.4.1 permite ao usuário combinar REST, Broker, WebSocket e Database em uma mesma execução causal. Entretanto, a paleta ainda representa muito do processamento sob o tipo genérico `API`. A v0.5 deve permitir arquiteturas mais expressivas (gateway, BFF, serviços, workers, disparadores, autenticação, webhooks, orquestração e integração empresarial).

**Não** criar um engine por componente. O perfil de comunicação continua na conexão; o tipo de nó define capacidades, defaults, propriedades e experiência contextual. A sequência de Actions continua definindo a atividade executada.

Preservar `/api/v1`, o formato `UnifiedScenario`, IDs/trace correlacionado, mainStatus/globalStatus, relógio virtual e APIs legadas.

## 2. Catálogo e priorização proposta

O catálogo de v0.3 já selecionou dez nomes: nove serviços + Broker. O Broker **já é componente existente**; esta versão expande os outros nove itens, porém diferencia MVP de extensão para evitar nove motores complexos.

| ID | Componente | Prioridade | Responsabilidade de simulação mínima |
| --- | --- | --- | --- |
| SC-01 | **Service** | P0 | Processamento de negócio genérico, entrada e saída, Actions, chamadas e publicação; protocolo por conexão. |
| SC-02 | **API Gateway** | P0 | Receber request, selecionar destino configurado, encaminhar CALL, devolver resultado ou erro; latência/timeout próprios. |
| SC-03 | **BFF** | P0 | Expor interface ao Client, chamar uma ou mais dependências e agregar/adaptar resultado por comportamento; sem engine novo. |
| SC-04 | **Worker** | P0 | Consumir mensagem/atividade assíncrona e executar Actions sem exigir resposta HTTP ao produtor. |
| SC-05 | **Scheduler** | P1 | Gerar evento inicial único ou sequência finita agendada em tempo virtual; não usar cron real ou execução infinita. |
| SC-06 | **Authentication Service** | P1 | Responder verificação fictícia de identidade/token segundo política configurável; não emitir credenciais reais. |
| SC-07 | **Webhook Receiver** | P1 | Receber POST/evento HTTP virtual e disparar Behavior; distinguir de Client e API convencional na UX. |
| SC-08 | **Orchestrator Service** | P1 | Coordenar Actions e dependências `CALL/PUBLISH/FORK/JOIN` entre componentes, inclusive falhas e waits. |
| SC-09 | **Integration Bus / ESB** | P1 | Receber, aplicar transformação lógica simulada (metadados/latência) e encaminhar para destino configurado; não duplicar Broker. |
| EXISTS | **Broker** | Preservar | Reutilizar Queue, Topic e MQTT v0.4/v0.4.1; não redesenvolver nem reclassificar sem justificativa. |

**P0** é o primeiro incremento de entrega; **P1** depende de validação do contrato de capacidades e do núcleo P0. Esta priorização é proposta, não aprovação definitiva.

## 3. Distinção entre comportamento e aparência

- **Service** representa a unidade genérica de processamento, independente de REST ou outro transporte.
- **API** existente deve continuar funcionando. Avaliar mantê-la como tipo com semântica atual e habilitar criação de `Service` sem renomear automaticamente arquivos antigos. Não converter silenciosamente `API` em `SERVICE`.
- **BFF** especializa experiência e defaults de um serviço; não é novo motor nem exige rede real.
- **Gateway** agrega roteamento de chamadas e resposta, mas não é Broker de mensagens.
- **Worker** é orientado a gatilhos assíncronos; não retornar HTTP por padrão ao produtor.
- **Scheduler** é fonte de eventos virtuais com limites explícitos, não automação real.
- **Authentication Service** não é um provedor de autenticação real; resultado de autenticação é dado simulado do cenário.
- **Webhook Receiver** é um papel receptor HTTP, não endpoint real exposto à internet.
- **Orchestrator Service** coordena ações explícitas já existentes e políticas de falha, evitando novo editor BPMN.
- **ESB** encaminha/transforma logicamente, enquanto o Broker gerencia destinos e subscriptions; quando houver filas, usar Broker conectado.

## 4. Requisitos funcionais

| ID | Requisito | Aceite |
| --- | --- | --- |
| RF-5-001 | Paleta de serviços expandida | Todos os tipos aprovados aparecem com identidade visual distinta e criação/remoção/edição. |
| RF-5-002 | Capacidades por tipo | Validar triggers e Actions permitidos em cada papel; sem semântica arbitrária baseada apenas no ícone. |
| RF-5-003 | Perfis por conexão | Preservar os perfis v0.4 e a validação de origem/destino. |
| RF-5-004 | Execução compartilhada | Todos os componentes participam do mesmo clock/event queue/trace. |
| RF-5-005 | Service genérico | Recebe operação, processa, chama múltiplos destinos e publica mensagens por Behavior. |
| RF-5-006 | Gateway | Encaminha operação a destino selecionado por regra explícita; retorna falha de roteamento quando não há match. |
| RF-5-007 | BFF | Permite composição por Actions de chamadas e resposta ao Client. |
| RF-5-008 | Worker | Consome Broker delivery e inicia PROCESS/CALL/PUBLISH sem resposta síncrona implícita ao produtor. |
| RF-5-009 | Scheduler | Dispara eventos agendados de maneira finita e determinística; horizonte virtual configurado. |
| RF-5-010 | Authentication Service | Resultado simulado explícito permite fluxo autorizado/negado; não armazenar segredos. |
| RF-5-011 | Webhook Receiver | Entrada HTTP virtual desencadeia Behaviors configurados e operações derivadas. |
| RF-5-012 | Orchestrator Service | Encadeia chamadas e ações condicionadas às dependências e estados; sem execução arbitrária de todas as arestas. |
| RF-5-013 | ESB | Roteamento e transformação lógica observável, distinto de publish/delivery de Broker. |
| RF-5-014 | Compatibilidade | Execuções de v0.1 a v0.4.1 continuam reproduzíveis; importação preserva tipo API e resultados relevantes. |
| RF-5-015 | UX | Canvas React Flow continua fluido; painéis contextuais mostram propriedades pertinentes ao tipo. |
| RF-5-016 | Observabilidade | Timeline identifica papel/componente e Actions sem perder operação principal, derivadas e correlação. |
| RF-5-017 | Limites | Gatilhos de Scheduler, roteamentos e ações respeitam profundidade, duração e limite de eventos finitos. |

## 5. Matriz preliminar de capacidades

Legenda: R=request recebido; M=mensagem consumida; S=emissão agendada; W=mensagem WebSocket; C=CALL; P=PUBLISH; X=RESPOND; F=FORK/JOIN.

| Tipo | Gatilhos propostos | Actions principais | Observações |
| --- | --- | --- | --- |
| Service | R, M, W | PROCESS, C, P, X, WS_SEND, F | Responder só quando houver caller/request válido |
| API (legado) | R, M, W | Preservar v0.4.1 | Sem mudança automática de tipo |
| Gateway | R | PROCESS, C, X | Destino por rota ou Action; timeout configurado |
| BFF | R | PROCESS, C, X, F | Agregação lógica, não HTTP real |
| Worker | M, S | PROCESS, C, P, F | Não emitir RESPONSE sem request |
| Scheduler | S | disparo de entrada ou publicação via capacidade explícita | Evento finito; fonte de execução virtual |
| Authentication Service | R | PROCESS, X | Decisão fictícia consistente com entradas |
| Webhook Receiver | R | PROCESS, P, C, X | Simula POST, sem listener real |
| Orchestrator Service | R, M, S | C, P, F, PROCESS, X | Encadeamento com dependências e estados |
| ESB | R, M | PROCESS, C, P, X | Transformação e encaminhamento observáveis |

**Atenção:** esta matriz é orientação de refinamento, não contrato definitivo. O suporte aos gatilhos `S` e ramificação condicional, assim como a distinção de capacidades entre Services, exigem ADR e OpenAPI antes de desenvolvimento. Preferir ampliar sistema de Actions existente à criação de um DSL complexo.

## 6. Cenários de aceitação ilustrativos

**CT-5-01 — Gateway e Service:** Web Browser → API Gateway → Service Pedidos → Database → resposta. A rota escolhe uma aresta configurada; a timeline mostra passagem pelo Gateway.

**CT-5-02 — BFF e múltiplas dependências:** Mobile App → BFF → Service A + Service B → resposta agregada; a semântica de JOIN é explícita e o resultado segue limites do modelo, sem transformar respostas reais.

**CT-5-03 — Worker assíncrono:** Browser → Service → Broker Queue → Worker → Database; operação principal pode concluir antes da gravação simulada pelo Worker.

**CT-5-04 — Scheduler:** Scheduler dispara Worker/Service em timestamps virtuais configurados e termina após quantidade finita de disparos.

**CT-5-05 — Authentication:** Client → Gateway → Authentication Service; resultado simulado de autorização permite/nega o prosseguimento segundo regra explícita.

**CT-5-06 — Webhook:** Serviço externo modelado como Client → Webhook Receiver → Broker → Worker. Sem receber HTTP real.

**CT-5-07 — Orquestrador:** Service Orchestrator chama pagamentos, estoque e notificação com dependências e rastreabilidade.

**CT-5-08 — ESB:** Service A → ESB → Service B ou Broker, com tempo de transformação/encaminhamento próprio, sem simular um produto ESB real.

**CT-5-09 — Regressão:** cenário v0.4.1 Browser → API → Broker → API → Database segue executando sem conversão silenciosa de `API`.

## 7. Arquitetura e contratos — decisões ainda necessárias

1. Evoluir `ComponentV2` e `ComponentTypeV2` com novo catálogo, mantendo `API` e subclasses legadas no JSON.
2. Modelar capacidades declarativas (`acceptedTriggers`, `allowedActions`, `allowedConnections`) compartilhadas, sem subclasses de runtime apenas cosméticas.
3. Decidir se roteamento Gateway, composição BFF e transformação ESB são propriedades de Action, Behaviors especializados ou abstrações reutilizáveis; evitar três soluções incompatíveis.
4. Definir um modelo único para disparo `SCHEDULED_TRIGGER` no cenário, limite de ocorrências, intervalo virtual e relação com `mainStatus`.
5. Formalizar regras mínimas de Authentication Service sem credenciais ou OAuth real.
6. Garantir compatibilidade de connections, direção, no-duplicate-edge e perfis, com validador do domínio.
7. Atualizar `docs/api/openapi.yaml` apenas após aprovação das decisões e schema coeso com o backend.
8. Não adicionar `/api/v5` nem serviço HTTP real.

## 8. Plano de execução

- **Etapa A — planejamento:** revisar requisitos deste documento, ADR de capacidade e contrato, aprovar escopo P0/P1.
- **Etapa B — backend base P0:** tipo Service, Gateway, BFF e Worker; validar conexões, eventos e Actions existentes; regressão v0.4.1.
- **Etapa C — backend P1:** Scheduler, Authentication Service, Webhook Receiver, Orchestrator Service, ESB, somente com regras normativas aprovadas.
- **Etapa D — OpenAPI e exemplos:** schemas discriminados, mapeamento API compatível e geração TypeScript.
- **Etapa E — frontend:** paleta, ícones, formulários contextuais, serialização e visualização de resultados.
- **Etapa F — testes finais:** CT-5-01 a CT-5-09, integração, limites e performance do editor.

**Gate:** não implementar automaticamente todos os componentes com ações meramente cosméticas. Cada novo tipo precisa de identidade, capacidades válidas e pelo menos um cenário reproduzível.

## 9. Fora de escopo

Sistemas de rede reais, provisionamento de serviços, OAuth/JWT reais, cron real, filas reais, persistência, retentativas ilimitadas, engenharia de CPU/memória, tecnologias IBM reais, editores BPMN completos, Web Server/Application Server, Legacy Application e novos componentes não aprovados.

## 10. Critérios de aprovação do planejamento

- Confirmar catálogo e prioridade P0/P1;
- Revisar modelagem de Service genérico versus API legada;
- Aprovar abordagem compartilhada de capacidades e de Actions;
- Aprovar política de Scheduler finito e semântica de Gateway/Auth/ESB;
- Definir o contrato normativo antes da implementação.

