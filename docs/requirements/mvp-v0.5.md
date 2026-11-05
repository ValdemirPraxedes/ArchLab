# ArchLab v0.5 — Expansão do catálogo de componentes de serviço

> **Status:** proposta inicial para revisão de produto e arquitetura. Não autoriza implementação ou merge.
> **Base:** `develop` após v0.4.1; [catálogo previamente selecionado](v0.3-services.md); [comunicação mista](mvp-v0.4.1.md).
> **Objetivo:** oferecer tipos de serviço reais e distintos na paleta visual, aproveitando Actions, Behaviors e motor determinístico único.

## 1. Problema e premissas

A v0.4.1 permite ao usuário combinar REST, Broker, WebSocket e Database em uma mesma execução causal. Entretanto, a paleta ainda representa muito do processamento sob o tipo genérico `API`. A v0.5 deve permitir arquiteturas mais expressivas (gateway, BFF, serviços, workers, disparadores, autenticação, webhooks, orquestração e integração empresarial).

**Não** criar um engine por componente. O perfil de comunicação continua na conexão; o tipo de nó define capacidades, defaults, propriedades e experiência contextual. A sequência de Actions continua definindo a atividade executada.

Preservar `/api/v1`, `UnifiedScenario`, correlação, estados e relógio virtual. `API` deixa de ser componente criável na v0.5; importações antigas com `type: API` passam por migração explícita para `SERVICE`.

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
- **Decisão aprovada:** `SERVICE` substitui o componente genérico `API` na paleta, criação e serialização de novas arquiteturas. API representa uma interface de comunicação exposta por um Service, não um nó. Arquiteturas antigas com `type: API` são aceitas apenas pelo adaptador de importação, migradas explicitamente para `SERVICE`, preservando IDs, conexões, Behaviors/Actions e processamento; a UI deve informar a conversão.
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
| RF-5-014 | Compatibilidade e migração | Importações v0.1–v0.4.1 com `type: API` são convertidas explicitamente para `SERVICE` (IDs, conexões, Actions e tempos preservados); novos cenários só serializam `SERVICE`. Regressão semântica obrigatória. |
| RF-5-015 | UX | Canvas React Flow continua fluido; painéis contextuais mostram propriedades pertinentes ao tipo. |
| RF-5-016 | Observabilidade | Timeline identifica papel/componente e Actions sem perder operação principal, derivadas e correlação. |
| RF-5-017 | Limites | Gatilhos de Scheduler, roteamentos e ações respeitam profundidade, duração e limite de eventos finitos. |

## 5. Matriz preliminar de capacidades

Legenda: R=request recebido; M=mensagem consumida; S=emissão agendada; W=mensagem WebSocket; C=CALL; P=PUBLISH; X=RESPOND; F=FORK/JOIN.

| Tipo | Gatilhos propostos | Actions principais | Observações |
| --- | --- | --- | --- |
| Service | R, M, W | PROCESS, C, P, X, WS_SEND, F | Responder só quando houver caller/request válido |
| API (somente entrada legada) | R, M, W | Migrar para regras de Service na importação | Não aparece na paleta nem em novos arquivos v0.5 |
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

**CT-5-09 — Migração/regressão:** importar cenário v0.4.1 Browser → API → Broker → API → Database, registrar `API → SERVICE` explicitamente, preservar IDs e execução causal e reexportar sem `type: API`.

## 7. Arquitetura e contratos — decisões ainda necessárias

1. Evoluir `ComponentV2` e `ComponentTypeV2` com novo catálogo; novos JSONs utilizam `SERVICE`, enquanto o adaptador de entrada/migração lê temporariamente `API` para arquivos antigos.
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
- **Etapa D — OpenAPI e exemplos:** schemas discriminados, `SERVICE` canônico, `API` deprecated somente na entrada legada e geração TypeScript.
- **Etapa E — frontend:** paleta, ícones, formulários contextuais, serialização e visualização de resultados.
- **Etapa F — testes finais:** CT-5-01 a CT-5-09, integração, limites e performance do editor.

**Gate:** não implementar automaticamente todos os componentes com ações meramente cosméticas. Cada novo tipo precisa de identidade, capacidades válidas e pelo menos um cenário reproduzível.

## 9. Fora de escopo

Sistemas de rede reais, provisionamento de serviços, OAuth/JWT reais, cron real, filas reais, persistência, retentativas ilimitadas, engenharia de CPU/memória, tecnologias IBM reais, editores BPMN completos, Web Server/Application Server, Legacy Application e novos componentes não aprovados.

## 10. Critérios de aprovação do planejamento

- Confirmar catálogo e prioridade P0/P1;
- Verificar remoção de `API` da paleta e migração explícita `API → SERVICE` na importação;
- Aprovar abordagem compartilhada de capacidades e de Actions;
- Aprovar política de Scheduler finito e semântica de Gateway/Auth/ESB;
- Definir o contrato normativo antes da implementação.


## 11. Ampliação do catálogo aprovada pelo responsável pelo produto (2026-11-05)

O responsável pelo produto confirmou a inclusão de **seis novos componentes lógicos** na v0.5. Eles se somam aos nove componentes de serviço já selecionados no planejamento anterior. `Database`, `Broker` e tipos Client continuam; o antigo tipo `API` é substituído por `SERVICE` para novos modelos, com adaptação explícita de arquivos históricos. A inclusão no catálogo está aprovada; os contratos e comportamentos detalhados ainda exigem refinamento.

| ID | Novo componente aprovado | Responsabilidade simulada mínima | Prioridade proposta |
| --- | --- | --- | --- |
| SC-10 | **Cache** | Operações de leitura/escrita/invalidação, cache hit/miss, TTL virtual e latência. Não é Database persistente. | P0 |
| SC-11 | **Object Storage** | PUT/GET/DELETE de objetos, tamanho e tempos virtuais de operação; sem armazenar arquivos reais. | P0 |
| SC-12 | **Notification Service** | Disparo de notificações lógicas por e-mail/SMS/push e estados de envio/falha simulados; sem provedores externos reais. | P1 |
| SC-13 | **Search Engine** | Indexação e consulta lógica com latências distintas e estado de índice simulado; sem servidor Elasticsearch/OpenSearch. | P1 |
| SC-14 | **Service Registry / Discovery** | Registro/consulta lógica de instâncias, descoberta de destinos e indisponibilidade virtual. | P1 |
| SC-15 | **Rules Engine** | Avaliação determinística de regras e resultados (por exemplo elegível/não elegível), permitindo que Behavior/Actions ramifiquem o fluxo somente após definir semântica explícita. | P1 |

As prioridades P0/P1 acima são **sugestões de faseamento**; a seleção dos seis itens para a versão foi aprovada sem obrigar que todos sejam implementados simultaneamente.

### 11.1. Classificação de infraestrutura — fora da v0.5 de serviços

- **Load Balancer:** classificado prioritariamente como componente de infraestrutura/roteamento de tráfego. Embora possa ser representado logicamente, sua semântica de distribuição entre instâncias exige modelagem de deployment, instâncias e health checks que não pertence à expansão inicial de serviços. **Não incluir na v0.5.**
- **CDN:** classificado prioritariamente como infraestrutura de entrega e cache de borda; exige nós de borda/origem e topologia geográfica ou de rede. **Não incluir na v0.5.**
- Ambos podem ser revisitados numa futura fase de **infraestrutura/deployment**, sem confundir API Gateway com Load Balancer nem CDN com Cache genérico.

### 11.2. Critérios adicionais de aceite

| ID | Requisito | Aceite |
| --- | --- | --- |
| RF-5-018 | Cache com TTL virtual | Cache hit evita uma chamada ao destino; cache miss executa fallback configurado; expiração usa relógio virtual. |
| RF-5-019 | Armazenamento de objetos | Serviço pode enviar operação PUT/GET/DELETE ao Object Storage; timeline registra conclusão ou falha. |
| RF-5-020 | Notificações | Um Worker ou Service pode solicitar notificação e observar estado simulado sem integração real. |
| RF-5-021 | Busca/indexação | Simular indexação de documento e consulta, distinguindo tempos e efeitos previstos no modelo. |
| RF-5-022 | Descoberta de serviço | Consultas ao Registry retornam destino elegível ou indisponibilidade de maneira determinística; não reescrever automaticamente conexões existentes. |
| RF-5-023 | Regras de negócio | Rules Engine devolve decisão configurada e rastreável; nenhum JavaScript ou expressão arbitrária de usuário deve ser executado. |

### 11.3. Novos cenários de aceitação

- **CT-5-10:** Client → Gateway → Service → Cache; em miss, Service consulta Database, atualiza Cache e responde; nova chamada dentro do TTL produz hit.
- **CT-5-11:** Service → Object Storage, com PUT seguido de GET e tempos virtuais reproduzíveis.
- **CT-5-12:** Broker → Worker → Notification Service, permitindo falha simulada sem desfazer resposta principal.
- **CT-5-13:** Worker indexa documento no Search Engine; Service consulta posteriormente e obtém resultado lógico.
- **CT-5-14:** Service consulta Service Registry; destino inexistente ou indisponível produz falha explícita, não roteamento mágico.
- **CT-5-15:** Service chama Rules Engine e recebe decisão; eventual ramificação condicional deve ser definida no contrato de Actions antes da implementação.

### 11.4. Limites de escopo

Não modelar provedores reais de cache, storage, SMS, busca, registry ou motor de regras. Simular operações e estados em memória com limite finito e resultados determinísticos. Não criar engine separado para cada tipo. Cache, Search Engine e Object Storage podem exigir estados virtuais específicos **dentro do contexto de execução**, além de Actions genéricas; isso deve ser especificado no ADR e contrato. Em especial, uma única simulação que contenha múltiplas entradas ou requisições precisa de regras explícitas de ordenação para demonstrar cache hit/miss e descoberta.

**Atualização de aprovação:** o catálogo agora inclui **15 componentes de serviço selecionados para v0.5** (nove do planejamento anterior + seis novos). O Broker segue componente existente preservado, e Load Balancer/CDN não fazem parte dessa lista.
