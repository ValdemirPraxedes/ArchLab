# ArchLab — Planejamento da v0.4: perfis de comunicação

> **Atualização arquitetural aprovada (2026-11-04):** manter somente a API pública `/api/v1`, migrar/remover `/api/v2` e não criar `/api/v4`. Consulte [ADR-v0.4](../architecture/ADR-v0.4.md) e RF-V4-025. Esta revisão prevalece sobre qualquer sugestão anterior de uma API pública v4.\n\n> **Estado: planejamento em andamento.** Este documento consolida as definições aprovadas e registra propostas ainda sujeitas a refinamento. **Não autoriza implementação.** Será atualizado nesta branch durante as discussões; abrir PR para `develop` somente quando o responsável solicitar.

## 1. Objetivo

Evoluir o ArchLab para representar e **simular semanticamente dez perfis de comunicação**, permitindo que o usuário conecte componentes compatíveis no editor, selecione um perfil, clique em **Simular** e observe o comportamento no trace, na timeline e nos resultados.

O objetivo é modelagem e aprendizagem de System Design, **não implementação de protocolos de rede reais**. Preservar a fluidez do editor e o escopo incremental de entregas pequenas adotado na v0.3.

## 2. Decisões aprovadas

### DEC-01 — Dez perfis no objetivo da v0.4

| Perfil | Modelo semântico de referência | Situação |
| --- | --- | --- |
| REST (HTTP) | Requisição/resposta | Selecionado; já existe suporte anterior que poderá ser reutilizado |
| SOAP (HTTP) | Requisição/resposta | Selecionado; já existe suporte anterior que poderá ser reutilizado |
| HTTP genérico | Requisição/resposta | Selecionado |
| GraphQL (HTTP) | Requisição/resposta | Selecionado |
| gRPC unary | RPC com requisição/resposta | Selecionado |
| CoAP | Requisição/resposta, com semântica própria a detalhar | Selecionado |
| WebSocket | Comunicação bidirecional / sessão persistente | Selecionado |
| MQTT | Publicação/assinatura mediada por Broker | Selecionado |
| Queue Messaging | Fila e entrega para consumidor | Selecionado |
| Topic Messaging | Publicação e entrega para assinantes | Selecionado |

**Importante:** a seleção dos dez perfis é o objetivo aprovado da versão, mas não implica que todos compartilhem o mesmo mecanismo de execução ou que estejam implementados hoje.

### DEC-02 — Simulação semântica

**Aprovado:** os perfis representarão operações, estados, eventos e efeitos relevantes **dentro do simulador**, sem requisições HTTP reais, sockets WebSocket reais, servidores MQTT reais ou integrações reais com mensageria.

A simulação deve retratar as diferenças importantes entre modelos, e **não simplesmente trocar o rótulo da conexão**.

### DEC-03 — Um Broker genérico

**Aprovado:** incluir **um único novo componente de infraestrutura chamado Broker**, reutilizado pelos perfis de mensageria MQTT, Queue e Topic.

- Não criar um componente distinto por tecnologia comercial.
- IBM MQ e TIBCO EMS são referências conceituais futuras, **não integrações reais nesta versão**.
- O Broker terá semântica mínima suficiente para simular recebimento, encaminhamento e entrega de mensagens, ainda a especificar.
- O papel de produtor/consumidor/assinante dos componentes existentes e a configuração das ligações serão refinados antes da implementação.

### DEC-04 — Reutilizar componentes existentes

**Aprovado como limite inicial de escopo:** reutilizar os **seis clientes da v0.3** e o componente **API**. Não introduzir novos tipos de clientes ou de serviços nesta versão.

- Os seis clientes atuais compartilham o comportamento funcional de Client introduzido/consolidado na v0.3.
- **Broker é a única nova categoria de componente de infraestrutura prevista neste planejamento.**
- Novos componentes como Service genérico, API Gateway, Worker, BFF, Integration Bus/ESB e tecnologias de mensageria específicas permanecem para o backlog.

### DEC-05 — Validação prática no editor

**Aprovado como direção de produto:** um perfil não estará concluído só por aparecer na lista. A entrega deverá permitir **selecionar perfil, configurar ligação compatível, simular e observar eventos e resultados coerentes**.

Perfis diretos poderão usar topologias como `Cliente → API`. Para mensageria, usar topologia compatível como `Cliente → Broker → API`, após o refinamento das capacidades dos participantes.

## 3. Organização preliminar por comportamento

Esta classificação foi proposta como ponto de partida para refinar o motor, **não é ainda um contrato técnico fechado**.

| Grupo | Perfis | Direção do desenho |
| --- | --- | --- |
| Requisição/resposta | REST, SOAP, HTTP genérico, GraphQL, gRPC unary, CoAP | Reaproveitar mecanismo síncrono onde a semântica permitir, acrescentando diferenças mínimas por perfil |
| Bidirecional | WebSocket | Representar abertura/uso/encerramento de sessão e mensagens nos dois sentidos |
| Mensageria assíncrona | MQTT, Queue, Topic | Introduzir Broker, publicação/envio, recepção, roteamento e entrega por consumidor/assinatura |

**Não assumir**, antes de refinamento, que CoAP equivale integralmente a HTTP ou que WebSocket se reduz a duas chamadas HTTP.

## 4. Comportamentos mínimos propostos para o Broker — ainda a detalhar

Os seguintes princípios foram propostos e aceitos como direção geral; suas regras exatas de execução, filas, eventos e estados **ainda precisam ser especificadas**:

| Perfil | Comportamento semântico pretendido |
| --- | --- |
| Queue Messaging | Broker recebe mensagem, mantém em fila lógica e entrega a **um** consumidor elegível para aquela mensagem |
| Topic Messaging | Broker recebe publicação e entrega cópia para **cada assinatura elegível** |
| MQTT | Broker representa publicação/assinatura por tópico no perfil MQTT, com semântica mínima própria a definir |

### Explicitamente fora do escopo inicial

- Persistência física em disco e recuperação após reinício.
- Dead-letter queue, retries automáticos avançados, cluster e alta disponibilidade do Broker.
- Implementações reais de IBM MQ, TIBCO EMS ou broker MQTT.
- Implementação real de pilhas de protocolo, pacotes, frames, sockets, conexões externas ou servidores de rede.
- Expansão irrestrita para novos tipos de serviços ou infraestrutura.

## 5. Proposta de desenvolvimento incremental — pendente de aprovação detalhada

A estratégia sugerida é dividir a v0.4 em **entregas verificáveis**, preservando a possibilidade de reavaliar o escopo se algum comportamento exigir uma complexidade maior que a prevista.

1. **Perfis de requisição/resposta:** REST, SOAP, HTTP genérico, GraphQL, gRPC unary e CoAP.
2. **WebSocket:** sessão e comunicação bidirecional semântica.
3. **Mensageria e Broker:** MQTT, Queue e Topic com um Broker genérico mínimo.
4. **Integração e regressão:** testar os dez perfis de ponta a ponta, UX e cenários legados.

Essa ordem e os limites de cada incremento **não são ainda tarefas aprovadas**. O objetivo dos dez perfis está aprovado, mas a decomposição técnica depende dos requisitos.

## 6. Matriz de compatibilidade aprovada — DEC-06

**Status: aprovada em 2026-11-04.** Os seis clientes específicos da v0.3 compartilham estas permissões; não é necessário criar regras diferentes por ícone ou nome.

| Perfil | Cliente → API | API → API | Cliente → Broker | API → Broker | Broker → API |
| --- | --- | --- | --- | --- | --- |
| REST (HTTP) | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| SOAP (HTTP) | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| HTTP genérico | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| GraphQL (HTTP) | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| gRPC unary | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| CoAP | Permitido | Permitido | Não previsto | Não previsto | Não previsto |
| WebSocket | **Permitido** | **Permitido** | Não previsto | Não previsto | Não previsto |
| MQTT | Não previsto | Não previsto | Permitido | Permitido | Permitido |
| Queue Messaging | Não previsto | Não previsto | Permitido | Permitido | Permitido |
| Topic Messaging | Não previsto | Não previsto | Permitido | Permitido | Permitido |

A aprovação **inclui explicitamente WebSocket em conexões `API → API`**, além de `Cliente → API`. O sentido da aresta identifica quem inicia a conexão; uma sessão WebSocket ativa poderá trocar mensagens nos dois sentidos, sujeito às regras que ainda serão detalhadas.

### DEC-07 — Regras gerais de conexões aprovadas

1. Exibir no editor apenas os perfis compatíveis com o par origem/destino da conexão.
2. Permitir que qualquer um dos seis clientes existentes inicie chamadas a APIs ou envie mensagens ao Broker, conforme o perfil.
3. Permitir que a API chame outra API, publique mensagens ao Broker e consuma mensagens entregues pelo Broker.
4. Manter proibida a conexão direta `Cliente → Database`.
5. Não oferecer REST, SOAP, HTTP genérico, GraphQL, gRPC unary, CoAP ou WebSocket para arestas destinadas ao Broker neste escopo.
6. Não permitir `Broker → Broker` na v0.4.
7. Não confundir permissão de conexão com implementação idêntica: Queue, Topic e MQTT exigem comportamentos semânticos distintos.
8. Manter as regras já existentes de conexões e validações que não forem explicitamente substituídas por estas decisões. O fluxo `API → Database` e seu protocolo existente permanecem fora da matriz dos dez perfis, sem modificação nesta decisão.

### Pendências específicas — não decidir por inferência

- Como configurar abertura/encerramento de sessão WebSocket, ordem das mensagens, permissões de emissão por cada lado e condição de conclusão da simulação, tanto em Cliente → API quanto API → API.
- Como representar papéis de produtor, consumidor e assinante na topologia `Cliente/API → Broker → API`, incluindo assinatura e entrega por perfil.
- Campos configuráveis por perfil, critérios de simulação, eventos e métricas.
- Limites de simultaneidade e compatibilidade com as restrições estruturais atuais do motor.

## 7. Configuração dos perfis de requisição/resposta — DEC-08

**Status: aprovado em 2026-11-04.** Esta decisão detalha os seis perfis REST, SOAP, HTTP genérico, GraphQL (HTTP), gRPC unary e CoAP, respeitando a matriz aprovada na DEC-06. É uma **configuração semântica**, não execução de protocolos reais.

### DEC-08.1 — Painel contextual de propriedades

Ao selecionar uma conexão, o editor apresenta o perfil de comunicação e propriedades comuns; exibe **somente os campos específicos do perfil selecionado**. Trocar de perfil deve validar os campos e impedir que dados incompatíveis tornem o modelo inválido. Usar valores padrão para permitir simulação básica sem preenchimento extensivo.

As mesmas opções são permitidas para os seis tipos de cliente da v0.3. Configurações precisam ser preservadas no modelo do editor e representadas na simulação sem alterar o tipo visual do cliente.

### DEC-08.2 — Campos comuns

| Propriedade | Regra aprovada |
| --- | --- |
| Nome da conexão | Opcional |
| Perfil de comunicação | Obrigatório, limitado pelos tipos de origem/destino |
| Latência | Reutilizar o comportamento/configuração já existentes |
| Timeout | Configurável quando aplicável ao perfil |
| Tempo de processamento | Permanece propriedade do componente que executa a operação; não confundir com latência de rede |
| Payload | Representação lógica simples e inicialmente opcional, sem payload real |
| Resultado | Simulação registra sucesso, falha ou timeout conforme as regras de execução a detalhar |

**Nota:** números exibidos no protótipo (por exemplo, 50 ms de latência ou 3000 ms de timeout) são valores ilustrativos; os valores padrão normativos e seus limites devem ser definidos na especificação de parâmetros.

### DEC-08.3 — Campos específicos por perfil

| Perfil | Propriedades aprovadas | Intenção semântica |
| --- | --- | --- |
| REST (HTTP) | Método HTTP e caminho/recurso | Requisição, processamento e resposta |
| SOAP (HTTP) | Operação SOAP e ação SOAP opcional | Chamada de operação e resposta, sem processar XML |
| HTTP genérico | Método HTTP e caminho/recurso | Requisição/resposta sem impor modelagem REST |
| GraphQL (HTTP) | Tipo de operação Query/Mutation e nome da operação | Execução semântica da operação, sem interpretar schema GraphQL |
| gRPC unary | Nome de serviço e método | Uma requisição RPC e uma resposta; sem arquivos `.proto` |
| CoAP | Método, recurso e modo CON/NON | Requisição/resposta com confirmação quando aplicável; sem mensagens reais de rede |

O desenho do painel pode utilizar opções usuais de método (GET, POST, PUT, DELETE e PATCH onde adequado). A lista exata de métodos permitidos por perfil, os defaults, os limites e a validação de cada propriedade serão fechados antes da implementação. O CoAP não deve receber automaticamente todos os métodos do HTTP.

### DEC-08.4 — Timeline e semântica

As simulações precisam **demonstrar diferenças relevantes entre os perfis**, ainda que compartilhem mecanismo interno de requisição/resposta:

- REST / HTTP genérico: requisição → processamento → resposta.
- SOAP: chamada da operação → processamento → resposta.
- GraphQL: operação recebida → resolução/execução → resposta.
- gRPC unary: chamada RPC → execução do método → resposta RPC.
- CoAP: solicitação e resposta, com confirmação semântica quando aplicável. Resposta piggybacked e resposta separada devem ser distinguidas quando o detalhamento dos eventos for definido.

Estas sequências são **exemplos conceituais**, não nomes normativos de eventos. Reutilizar eventos do motor com metadados quando suficiente; adicionar eventos próprios somente quando necessário à semântica. Não presumir que toda resposta CoAP ocorra obrigatoriamente após um ACK separado.

### DEC-08.5 — Limites

- Não exigir XML SOAP, schema GraphQL, arquivos proto, payloads reais nem implementação de transporte de rede.
- Preservar fluidez do editor e configurações anteriores, evitando campos irrelevantes.
- Não incluir propriedades de WebSocket, MQTT, Queue ou Topic nesta decisão; serão detalhadas em etapas específicas.
- A definição exata dos DTOs, eventos de trace, validações formais e métricas permanece **pendente de especificação**.

## 8. Simulação semântica WebSocket — DEC-09

**Status: aprovado em 2026-11-04.** Esta decisão aplica-se às conexões `Cliente → API` e `API → API` aprovadas na DEC-06. O WebSocket será representado por **sessão virtual controlada pelo relógio virtual**, sem abrir sockets, conexões externas ou executar frames reais.

### DEC-09.1 — Ciclo de vida da sessão

1. **Abertura:** o componente de origem solicita a conexão; o destino aceita ou rejeita.
2. **Estabelecimento:** ao aceitar, a sessão passa a permitir mensagens independentes em ambos os sentidos.
3. **Troca de mensagens:** origem e destino podem enviar mensagens, inclusive sem requisição precedente; cada mensagem é agendada e entregue segundo a latência da simulação.
4. **Encerramento:** a sessão termina normalmente (automático ao final da sequência ou por evento explícito), por falha ou por timeout.

A direção `Origem → Destino` na aresta identifica **quem inicia a sessão**, e não restringe a direção das mensagens após sua abertura. O fluxo semântico também vale para `API → API`.

### DEC-09.2 — Propriedades da conexão

| Propriedade | Regra aprovada |
| --- | --- |
| Caminho da conexão | Caminho lógico configurável (exemplo ilustrativo: `/ws/events`) |
| Latência | Reutilizar o mecanismo existente, aplicado às mensagens |
| Timeout de abertura | Configurável para estabelecer sessão |
| Timeout de inatividade | Opcional |
| Mensagens da sessão | Sequência configurável de mensagens lógicas, sem payload de rede real |
| Emissor por mensagem | Origem ou Destino |
| Intervalo entre mensagens | Tempo virtual em milissegundos |
| Encerramento | Automático após a última mensagem ou por evento de encerramento configurado |

O editor deverá disponibilizar uma seção contextual **Mensagens da sessão**, preferencialmente mantendo o painel simples e a fluidez da v0.3. Nomes de mensagem como `subscribe`, `subscribed` e `notification` e valores numéricos do protótipo são **exemplos**, não defaults normativos.

### DEC-09.3 — Estados e eventos observáveis

Estados semânticos da sessão: `CONNECTING`, `OPEN`, `CLOSING` e `CLOSED`. Falhas e timeouts devem aparecer explicitamente como resultados de erro; a representação formal desses resultados na máquina de estados será detalhada na especificação técnica.

Eventos **candidatos** à timeline:
- `WS_CONNECT_REQUESTED`
- `WS_CONNECTED`
- `WS_MESSAGE_SENT`
- `WS_MESSAGE_RECEIVED`
- `WS_CLOSE_REQUESTED`
- `WS_CLOSED`
- `WS_TIMEOUT`
- `WS_FAILED`

**Os nomes acima não determinam a criação obrigatória de oito novos eventos internos.** Reutilizar eventos existentes com metadados de perfil sempre que isso mantiver a semântica observável.

### DEC-09.4 — Regras de execução

1. **Uma sessão virtual por conexão WebSocket durante uma execução**, neste escopo.
2. Após abrir, **ambos os participantes podem enviar mensagens** em ordem configurável e por agendamento determinístico no relógio virtual, sem exigir resposta obrigatória para cada envio.
3. A latência aplica-se **a cada mensagem**; não exigir que a mensagem anterior tenha sido recebida para agendar a próxima, desde que seus horários e pré-condições permitam.
4. Se a abertura for rejeitada ou falhar, **não enviar mensagens de aplicação** nessa sessão.
5. Registrar encerramento normal ou por falha/timeout e o resultado correspondente na timeline.
6. Uma simulação com sessão WebSocket **não deve ser considerada globalmente concluída enquanto a sessão permanecer aberta**; falha/timeout terminal também conta como encerramento.
7. Manter cenários v0.3 e demais perfis funcionando sem regressão.

### DEC-09.5 — Limites e detalhamento futuro

Ficam fora do escopo inicial: Ping/Pong semântico detalhado, reconexão automática, múltiplas sessões simultâneas por conexão, frames WebSocket reais e sockets reais.

Ainda precisam ser especificados antes da implementação: valores padrão e limites dos timeouts, validação dos intervalos das mensagens, política de ordenação em tempos virtuais iguais, condição formal da conclusão global, relação com fluxo principal de simulação, campos do contrato API e testes determinísticos. **A aprovação do comportamento não equivale à aprovação de DTOs ou de uma alteração específica no motor.**

## 9. Broker e mensageria semântica — DEC-10

**Status: aprovado em 2026-11-04.** A decisão formaliza o comportamento inicial do **Broker genérico** e dos perfis **Queue Messaging, Topic Messaging e MQTT**. Os três usam simulação semântica e o relógio virtual; nenhuma tecnologia real de mensageria será executada.

### DEC-10.1 — Responsabilidades e topologia

O único componente **Broker** previsto na v0.4 deve receber mensagens, mantê-las quando aplicável em memória virtual, selecionar destinos lógicos/assinantes e agendar entregas. Os componentes existentes **Cliente** (seis tipos da v0.3) e **API** podem publicar/enviar mensagens ao Broker; a **API** existente pode consumir/receber mensagens, sem introdução de um Worker.

As topologias de referência são `Cliente → Broker → API` e `API → Broker → API`, respeitando a matriz de compatibilidade aprovada na DEC-06.

**Fila e tópico são destinos lógicos configurados**, não nós adicionais do canvas. As assinaturas/consumidores devem ser derivados ou associados às conexões reais do editor, sem duplicar desnecessariamente configurações.

### DEC-10.2 — Semânticas aprovadas

| Perfil | Entrada no Broker | Seleção/entrega | Particularidade |
| --- | --- | --- | --- |
| **Queue Messaging** | Envio para fila lógica identificada | **Uma entrega por mensagem** a um consumidor elegível; se houver vários, seleção por **round-robin determinístico** | Não pressupõe exatamente uma vez sob falhas |
| **Topic Messaging** | Publicação em tópico lógico identificado | **Uma cópia lógica por assinatura elegível** | Distribuição aos assinantes; assinaturas definidas no modelo |
| **MQTT** | Publicação em tópico MQTT lógico | Entrega aos assinantes com **correspondência exata** do tópico | **QoS 0** apenas nesta versão; sem reenvio ou garantias superiores |

Não confundir os três perfis: Queue distribui a um consumidor, Topic multiplica por assinaturas elegíveis e MQTT representa publicação/assinatura com a semântica inicial de QoS 0 e tópicos exatos.

### DEC-10.3 — Propriedades no editor

| Propriedade | Queue | Topic | MQTT |
| --- | --- | --- | --- |
| Destino lógico | Nome da fila | Nome do tópico | Nome do tópico |
| Produtor | Cliente ou API | Cliente ou API | Cliente ou API |
| Destinatário elegível | API consumidora | API com assinatura | API com assinatura |
| Latência | Configurável, reutilizando o mecanismo existente | Configurável | Configurável |
| Tempo de processamento | Na API consumidora | Na API consumidora | Na API consumidora |
| Confirmação observável | Recebimento e consumo lógicos | Registro de publicação e entregas | Publicação QoS 0, sem exigir ACK de entrega |

O painel do Broker e das conexões deve ser contextual e simples. Os exemplos de destino como `orders` não são valores padrão normativos; defaults e validações de nomes ainda serão definidos.

### DEC-10.4 — Ciclo de execução e conclusão

1. O produtor publica/envia uma mensagem lógica.
2. O Broker recebe a mensagem e registra a aceitação no estado virtual da execução.
3. O Broker identifica consumidor ou assinantes elegíveis conforme o perfil.
4. O agendador virtual cria as entregas correspondentes (uma para Queue; uma por assinatura elegível para Topic/MQTT).
5. A API destinatária recebe e processa a mensagem.
6. Trace, timeline e resultados registram a aceitação, cada entrega, processamento e eventual erro.

**Conclusão da publicação não é conclusão global.** A publicação pode terminar quando o Broker aceitar a mensagem, mas a **simulação global permanece ativa até todas as entregas agendadas atingirem estado terminal** (processadas, falhadas ou encerradas por timeout), respeitando limites para não ficar indefinidamente aberta. Os conceitos de execução principal e global e seus DTOs precisam de especificação formal antes da implementação.

### DEC-10.5 — Ausência de consumidor elegível

- **Queue:** a mensagem permanece **pendente em memória virtual até um limite de tempo virtual**; ao atingir o limite, registrar resultado terminal explícito (p. ex. mensagem não consumida/timeout). Não concluir silenciosamente.
- **Topic e MQTT:** registrar **publicação sem destinatários**, sem agendar entregas; concluir a publicação normalmente no modelo semântico, deixando explícito que não houve destinatários. Isso não significa confirmação de consumo.
- O limite de espera da Queue, métricas e nomenclatura formal dos estados/eventos ainda serão definidos.

### DEC-10.6 — Determinismo, limites e exclusões

- Entregas e processamento são agendados pelo relógio virtual de modo determinístico, inclusive escolha round-robin.
- A v0.4 simulará **quantidade finita de mensagens**; não implementar loops automáticos em que consumidores republicam mensagens indefinidamente.
- Mensagens existem **somente no estado de memória virtual da execução**, não em disco nem em serviços externos.
- **MQTT começa com QoS 0**, correspondência exata de tópicos; sem wildcards, retained messages ou sessões MQTT persistidas.
- Fora do escopo: reentregas, retries automáticos, dead-letter queue, clustering, persistência em disco e alta disponibilidade; não implementar RabbitMQ, Kafka, IBM MQ, TIBCO EMS ou servidor MQTT real.
- Não afirmar garantias de `exactly-once`, durabilidade ou entrega em rede real.

### DEC-10.7 — Especificações ainda pendentes

Aprovação do comportamento **não fecha** os contratos OpenAPI, nomes finais de eventos, estrutura de filas/assinaturas, defaults, limites de mensagens, duração de timeout, ordenação de eventos no mesmo instante virtual, métricas, regras de erro ou o desenho interno das classes Java. Essas decisões deverão ser formalizadas antes de criar tarefas de implementação.

## 10. Estados, eventos, métricas e conclusão das simulações — DEC-11

**Status: aprovado em 2026-11-04.** Esta decisão consolida como os dez perfis da v0.4 serão observados e concluídos. Os estados e os eventos abaixo representam **semântica do produto**, e não impõem antecipadamente novos tipos internos ou nomes definitivos de DTOs.

### DEC-11.1 — Modelo de execução

Distinguir três conceitos relacionados, porém independentes:

1. **Execução principal:** operação iniciada pelo usuário — requisição, publicação ou abertura de sessão.
2. **Operações derivadas e recursos ativos:** chamadas internas, mensagens, entregas, processamento e recursos que permanecem vivos (por exemplo, sessão WebSocket e mensagem pendente).
3. **Conclusão global:** acontece apenas quando operações derivadas e recursos relevantes atingem estados terminais, com sucesso ou falha explicitamente registrados.

A conclusão da execução principal **não cancela operações assíncronas em andamento**. A conclusão global não deve ser confundida com sucesso: pode haver falhas, timeouts ou pendências encerradas.

### DEC-11.2 — Estados semânticos por modelo

| Modelo | Progressão de referência | Ressalva |
| --- | --- | --- |
| Requisição/resposta | `CREATED → SENT → PROCESSING → COMPLETED` | Falha e timeout são resultados terminais alternativos |
| Sessão WebSocket | `CONNECTING → OPEN → CLOSING → CLOSED` | Rejeição, falha e timeout também encerram a sessão de modo terminal |
| Mensagem/entrega | `CREATED → ACCEPTED → QUEUED/ROUTED → DELIVERED → PROCESSED` | Etapas dependem do perfil; nem todo fluxo requer todos os estados |

**Separar estados de sessão, mensagem/entrega, operação principal e execução global.** Não impor uma máquina de estados única aos dez perfis. Em MQTT QoS 0, `DELIVERED` é **entrega lógica da simulação**, não confirmação protocolar garantida pelo consumidor.

### DEC-11.3 — Eventos e timeline

A timeline deve representar a **sequência causal** do processamento, incluindo:

- Requisição/resposta: envio, recepção, processamento, resposta e conclusão.
- WebSocket: abertura/aceite ou falha, mensagens nos dois sentidos, encerramento/timeout.
- Queue: publicação, aceite pelo Broker, enfileiramento, escolha de consumidor, entrega e processamento (ou término por limite virtual).
- Topic/MQTT: publicação, aceite, identificação das assinaturas elegíveis, entrega lógica individual por assinatura, processamento e conclusão global.

Exemplos de rótulos ilustrativos: `REQUEST_SENT`, `RESPONSE_RECEIVED`, `WS_CONNECTED`, `WS_MESSAGE_SENT`, `BROKER_ACCEPTED`, `MESSAGE_DELIVERED`, `MESSAGE_PROCESSED`, `MAIN_COMPLETED`, `GLOBAL_COMPLETED`.

**Não são nomes normativos de eventos da engine.** Reutilizar eventos existentes e metadados quando suficiente para manter a semântica observável. Os exemplos de instantes virtuais apresentados na discussão não definem defaults.

### DEC-11.4 — Correlação lógica

Cada evento deve poder ser relacionado à sua execução e operação, preservando causa, ordem, participantes e perfil. Campos lógicos propostos:

| Campo lógico | Finalidade |
| --- | --- |
| `executionId` | Identificar a simulação |
| `eventId` | Identificar o evento |
| `timestamp` | Instante do relógio virtual |
| `componentId` | Componente responsável |
| `connectionId` | Conexão associada, quando houver |
| `operationId` | Requisição, mensagem ou operação |
| `parentOperationId` | Operação que originou a derivada |
| `sessionId` | Sessão WebSocket, quando aplicável |
| `profile` | Perfil de comunicação |
| `eventType` | Tipo semântico |
| `status` | Estado ou resultado associado |

Esses campos constituem **modelo lógico**, não exigência de adicionar todos os atributos a cada DTO. Antes de formalizar contratos, mapear e reutilizar identidades/correlações já existentes no backend, evitando redundância.

### DEC-11.5 — Métricas observáveis

Manter métricas anteriores de duração, latência e processamento. Propor métricas adicionais por modelo:

| Modelo | Métricas aprovadas como direção funcional |
| --- | --- |
| Requisição/resposta | Tempo de resposta, processamento, latência e timeouts |
| WebSocket | Tempo de abertura, duração da sessão, mensagens enviadas/recebidas |
| Queue | Quantidade publicada, entregue, processada e pendente; tempo de espera |
| Topic | Publicações, assinantes elegíveis, entregas e falhas |
| MQTT | Publicações, entregas lógicas, publicações sem destinatário e falhas |

Métricas reais de CPU, rede e throughput físico estão fora do escopo, pois o transporte não é real.

### DEC-11.6 — Regras de conclusão principal e global

| Situação | Execução principal | Execução global |
| --- | --- | --- |
| Requisição/resposta recebe resposta | Concluída | Terminal quando não houver operações/recursos relevantes pendentes |
| WebSocket com sessão aberta | Em andamento | Em andamento |
| WebSocket encerrado normalmente, por erro ou timeout | Terminal | Terminal se não restarem pendências |
| Queue com mensagem aceita pelo Broker | Concluída após aceite | Mantém-se ativa enquanto houver entrega/consumo pendente |
| Queue com todas as mensagens processadas | Concluída | Terminal quando não houver outras pendências |
| Topic com múltiplas APIs assinantes | Concluída após aceite | Aguarda entrega/processamento de cada assinatura elegível |
| MQTT sem assinantes | Concluída após aceite | Pode concluir com zero entregas registradas |
| Queue sem consumidor elegível | Concluída após aceite | Aguarda o limite virtual e termina com pendência/timeout explícito |

**Estados terminais não significam sucesso obrigatório.** Registrar resultados globais que diferenciem sucesso, falha, timeout e pendências encerradas.

### DEC-11.7 — Determinismo, controles e limites

1. O relógio virtual governa todos os eventos; a simulação não depende de tempo real.
2. Eventos no mesmo instante devem ter ordenação determinística definida pelo agendador.
3. Operações derivadas mantêm correlação com a principal.
4. Sessões, mensagens e operações pendentes têm limites finitos; não permitir execução infinita.
5. Resultados diferenciam sucesso, falha, timeout e encerramento com pendências.
6. Preservar pausa, continuação, reinício, mudança de velocidade e execução passo a passo.
7. Timeline e resultados apresentam tanto a operação principal quanto as derivadas.
8. Mesma arquitetura e mesmos parâmetros produzem o mesmo resultado, preservadas as premissas determinísticas.

### DEC-11.8 — Detalhamento técnico pendente

Antes de implementar, verificar o backend atual e definir em especificação normativa:

- nomes e tipos definitivos de eventos, DTOs e estados;
- quais IDs e mecanismos de correlação já existem;
- limites numéricos de sessões/mensagens/operações, timeouts e regras de desempate;
- modelo formal de encerramento de recursos, operação principal e conclusão global;
- tratamento de falhas parciais e as métricas exatas;
- testes determinísticos e critérios de aceitação de cada perfil.

A DEC-11 **não aprova automaticamente alterações internas na engine nem novos contratos OpenAPI**: essas mudanças devem ser justificadas e detalhadas no desenho técnico.

## 11. Requisitos funcionais v0.4 e critérios de aceitação — RF-V4-001 a RF-V4-025

**Status: aprovado em 2026-11-04.** Esta seção formaliza as decisões DEC-01 a DEC-11 como requisitos verificáveis. A definição de classes, contratos OpenAPI, nomes finais dos eventos, valores numéricos padrão e estratégia concreta de implementação **permanece pendente de especificação arquitetural**. Os requisitos se aplicam à **simulação semântica**, não à execução real dos protocolos de rede.

### Grupo A — Editor e conexões

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-001** | Disponibilizar os dez perfis nas conexões compatíveis. | O seletor oferece somente os perfis permitidos pela matriz DEC-06 para cada par de componentes; os dez são selecionáveis em ao menos uma topologia suportada. |
| **RF-V4-002** | Validar origem, destino e perfil conforme matriz aprovada. | Uma conexão permitida é aceita; Cliente → Database, Broker → Broker e perfis incompatíveis são rejeitados com explicação, preservando as validações legadas. WebSocket aceita Cliente → API e API → API. |
| **RF-V4-003** | Exibir propriedades contextuais e valores padrão por perfil. | Selecionar uma aresta revela os campos específicos do perfil (DEC-08, DEC-09 e DEC-10); troca de perfil valida dados existentes e aplica defaults válidos, sem exigir contratos de rede reais. |
| **RF-V4-004** | Preservar configurações, tipos e fluidez do editor. | Criar, mover, selecionar, renomear, remover e conectar nós continua fluido; alterar perfil não modifica a identidade visual dos seis clientes nem perde configurações válidas. |

### Grupo B — Requisição/resposta

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-005** | Simular REST, SOAP e HTTP genérico com comportamento existente e propriedades próprias. | Cada perfil gera execução de requisição/processamento/resposta; REST/HTTP apresentam método e caminho, SOAP identifica operação e ação opcional; métricas e trace permanecem acessíveis. |
| **RF-V4-006** | Simular GraphQL Query e Mutation com identificação de operação. | Um cenário Query e outro Mutation executam, registram o tipo/nome lógico da operação e geram resposta; nenhum parser/schema GraphQL real é exigido. |
| **RF-V4-007** | Simular gRPC unary. | Uma chamada lógica a serviço/método produz uma requisição RPC e uma resposta, com resultado/tempo observável; não requer arquivo `.proto` nem rede real. |
| **RF-V4-008** | Simular CoAP com CON/NON e confirmação quando aplicável. | Cenários CON e NON são distinguíveis no trace; confirmações semânticas respeitam casos de resposta no ACK ou resposta separada quando aplicáveis, sem representar sempre um ACK independente. |

### Grupo C — WebSocket

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-009** | Estabelecer sessão WebSocket virtual em Cliente → API e API → API. | As duas topologias abrem uma sessão virtual que passa por CONNECTING e OPEN ou termina explicitamente em rejeição/erro; uma sessão por conexão por execução. |
| **RF-V4-010** | Permitir mensagens configuráveis nos dois sentidos. | Uma sequência com emissões da origem e do destino é executada no relógio virtual; mensagens não exigem resposta anterior e preservam identidade/direção. |
| **RF-V4-011** | Simular latência, timeout e encerramento de sessão. | Latência se aplica a cada mensagem; abertura falha impede mensagens; encerramento automático, explícito ou por erro/timeout encerra a sessão com estado/resultado observável. |
| **RF-V4-012** | Exibir estados e mensagens WebSocket correlacionados na timeline. | O usuário identifica abertura, mensagens emitidas/recebidas, direção, encerramento e correlação com conexão/sessão; conclusão global aguarda término da sessão. |

### Grupo D — Broker e mensageria

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-013** | Disponibilizar um Broker genérico no editor. | O Broker pode ser adicionado, selecionado, movido, configurado e removido, com conexões conforme DEC-06, sem introduzir nós específicos de Queue/Topic/MQTT. |
| **RF-V4-014** | Configurar filas, tópicos e assinaturas como destinos lógicos. | Editor registra nome de fila/tópico e consumidores/assinantes elegíveis associados às conexões reais; não há exigência de broker de rede real. |
| **RF-V4-015** | Simular Queue com um consumidor elegível por mensagem. | Mensagem aceita é enfileirada logicamente e entregue a uma única API consumidora; com múltiplos consumidores, round-robin determinístico; sem alegar entrega exatamente uma vez sob falha. |
| **RF-V4-016** | Simular Topic com uma entrega individual por assinatura elegível. | Uma publicação com duas APIs assinantes gera duas entregas correlacionadas, e o resultado global aguarda a conclusão de ambas. |
| **RF-V4-017** | Simular MQTT QoS 0 com correspondência exata de tópico. | Publicação QoS 0 é registrada, tópicos exatos selecionam assinantes e produzem entregas lógicas; não há QoS 1/2, curingas, retained messages ou sessões MQTT persistidas. |
| **RF-V4-018** | Tratar mensagens sem consumidores elegíveis. | Queue permanece pendente até limite virtual e encerra explicitamente com pendência/timeout; Topic/MQTT registram publicação sem destinatários e zero entregas, sem confirmação fictícia de consumo. |

### Grupo E — Execução e resultados

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-019** | Separar conclusão principal e global. | Aceite de mensagem encerra publicação principal sem finalizar entregas pendentes; sessão aberta impede conclusão global; global termina apenas quando recursos/derivadas estiverem terminais. |
| **RF-V4-020** | Registrar eventos e operações derivadas com correlação causal. | Trace/timeline permitem relacionar operação principal, mensagens, entregas, conexão, participantes, perfil e sessão quando pertinente; reaproveitar IDs existentes quando possível. |
| **RF-V4-021** | Apresentar métricas adequadas por modelo. | Requisição/resposta mostra tempo/latência/processamento; WebSocket mostra abertura/duração/mensagens; mensageria mostra publicações, entregas, processamentos, pendências e falhas aplicáveis. |
| **RF-V4-022** | Garantir determinismo, limites finitos e resultados explícitos. | Mesmo cenário e parâmetros produzem a mesma sequência lógica; timeout/erro/pêndencia são terminalizados explicitamente; pausa, reinício, velocidade e passo a passo continuam funcionais. |

### Grupo F — Compatibilidade e testes

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-023** | Preservar o comportamento aprovado nas versões anteriores. | Cenários válidos da v0.3 continuam executando, incluindo os seis tipos visuais com comportamento Client, API/Database, editor, conexões e controles; nenhuma migração silenciosa de tipos. |
| **RF-V4-024** | Validar os dez perfis com testes automatizados e cenários reproduzíveis. | Para cada perfil existe ao menos um teste de execução e verificação de resultado/timeline; cenários inválidos, timeouts e regressões relevantes também são testados e documentados. |

### Cenários mínimos de aceitação

| Cenário de ponta a ponta | Resultado esperado | Requisitos |
| --- | --- | --- |
| Web Browser → API por REST | Requisição, processamento e resposta | RF-V4-001, 005, 021 |
| Mobile App → API por GraphQL | Query/Mutation identificada e executada | RF-V4-006 |
| CLI → API por gRPC unary | Requisição RPC e resposta | RF-V4-007 |
| Embedded System → API por CoAP CON | Confirmação representada quando aplicável | RF-V4-008 |
| API A → API B por WebSocket | Sessão e mensagens bidirecionais observáveis | RF-V4-009 a 012 |
| Bot → Broker → API por Queue | Entrega a um consumidor elegível | RF-V4-013 a 015 |
| Cliente → Broker → duas APIs por Topic | Duas entregas, uma por assinatura | RF-V4-016, 019 |
| API → Broker → API por MQTT | Publicação QoS 0 e entrega lógica | RF-V4-017 |
| Queue sem consumidor | Espera finita e encerramento explícito | RF-V4-018, 022 |
| Topic sem assinantes | Publicação registrada com zero entregas | RF-V4-018 |

Estes cenários são amostras mínimas e **não restringem a compatibilidade dos demais clientes ou pares de componentes aprovados na DEC-06**.

### Regras transversais de qualidade e implementação

- **Backend primeiro, frontend depois do backend** para mudanças que exigem motor/contratos; entregar em incrementos testáveis.
- Preservar drag-and-drop, seleção, movimento, conexão, edição, remoção e identidade dos seis clientes.
- Perfil não implementado **não pode ser apresentado ao usuário como funcional**.
- Reutilizar funcionalidades existentes quando a semântica permitir; não duplicar motor por perfil.
- Mesmos dados e configurações devem produzir resultados determinísticos.
- Exigir defesa técnica de cada implementação, com decisões, alternativas, UML fiel ao código e diagramas de sequência.

### Divisão inicial de entregas — aprovada como orientação, tarefas pendentes

1. **Entrega 1 — Requisição/resposta:** RF-V4-001 a 008 (incluindo compatibilidade e configuração dos seis perfis síncronos).
2. **Entrega 2 — WebSocket:** RF-V4-009 a 012.
3. **Entrega 3 — Broker e mensageria:** RF-V4-013 a 018.
4. **Entrega 4 — Integração e validação:** RF-V4-019 a 024, com capacidades de execução e correlação **implementadas antecipadamente quando forem pré-requisitos** das entregas anteriores.

A decomposição em tarefas/PRs, dependências detalhadas e contratos técnicos será definida após os ADRs e as especificações. **Nenhuma implementação foi autorizada por esta seção.**

### Grupo G — Consolidação da API pública (requisito adicional aprovado)

| ID | Requisito | Critério de aceitação verificável |
| --- | --- | --- |
| **RF-V4-025** | Consolidar todas as funcionalidades da API v1 e v2 em uma **API pública única `/api/v1`**, incluindo os recursos da v0.4, e retirar a API pública `/api/v2` após migração. | OpenAPI canônico atualizado em `docs/api/openapi.yaml`; endpoints de validação de arquitetura, validação de cenário e simulação disponíveis sob `/api/v1`; frontend migrado; testes de contrato e regressão cobrem recursos de versões anteriores e v0.4; não existem rotas públicas `/api/v2` ou `/api/v4` ao final. Eventuais mudanças incompatíveis nos payloads v1 são identificadas, documentadas e testadas. |

**Decisão arquitetural vinculada:** [ADR-V4-006 — API pública única /api/v1](../architecture/ADR-v0.4.md#adr-v4-006--api-pública-única-sob-apiv1-revisada-e-aprovada).

**Ordem obrigatória:** inventário de contratos e consumidores → definição do contrato v1 consolidado → migração do backend/frontend e testes → retirada final das rotas v2. A consolidação da API v1 é a **primeira frente do backend** da v0.4, antes dos novos perfis. O código do motor legado não deve ser descartado apenas porque carrega sufixo V2.

### Revisão do planejamento de entregas após RF-V4-025

A divisão anterior em quatro entregas permanece como orientação para os dez perfis, mas **é precedida por uma etapa de consolidação da API v1**. As tarefas finais serão detalhadas depois dos ADRs e do contrato canônico, sempre com backend antes do frontend.

## 12. Questões pendentes para as próximas discussões

1. **Compatibilidade aprovada:** detalhar papéis de produtor, consumidor e assinante e regras de configuração sem alterar a matriz aprovada.
2. **Configuração mínima aprovada para seis perfis:** detalhar defaults, limites, contratos de dados e propriedades ainda pendentes para WebSocket e mensageria.
3. **Requisição/resposta:** formalizar as diferenças já aprovadas em eventos, parâmetros e critérios de aceitação, sem duplicar a engine.
4. **WebSocket aprovado:** especificar defaults, validações, eventos internos/DTOs e regras formais de conclusão para o modelo de sessão definido na DEC-09.
5. **Broker aprovado:** detalhar formato de filas/tópicos/assinaturas, limites, eventos, defaults e contratos sobre a semântica definida na DEC-10.
6. **Mensageria:** formalizar a distinção aprovada entre publicação e conclusão global; definir estados terminais, eventos e métricas.
7. **Validação:** regras para rejeitar topologias incompatíveis, conexões duplicadas e cenários inválidos.
8. **Critérios de aceitação:** cenários mínimos observáveis por perfil, testes determinísticos e regressão da v0.3.
9. **Limites técnicos:** impacto no contrato OpenAPI, modelo do frontend e motor Java; necessidade de ADRs e de documentação de integração.
10. **Fatiamento:** confirmar entregas pequenas e dependências antes de criar as tarefas no Trello.

## 13. Método de trabalho documental

- Registrar cada nova decisão neste arquivo, identificando claramente **aprovado**, **proposto** ou **pendente**.
- Não introduzir requisitos extras sem debate e aprovação.
- Manter o escopo da v0.4 distinto do backlog futuro.
- Fazer commits incrementais nesta branch de planejamento.
- **Não abrir PR ou fazer merge até o responsável solicitar.**

## 14. Referências históricas

- [Requisitos v0.3](v0.3-client-requirements.md)
- [Catálogo discutido de clientes e perfis](v0.3-clients.md)
- [Levantamento de serviços e integração](v0.3-services.md)
- [Errata funcional da v0.3](v0.3-errata-client-behavior.md)

As decisões específicas para a v0.4 devem prevalecer sobre sugestões antigas que extrapolem o escopo desta versão.
