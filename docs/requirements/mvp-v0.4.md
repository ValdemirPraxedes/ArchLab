# ArchLab — Planejamento da v0.4: perfis de comunicação

> **Estado: planejamento em andamento.** Este documento consolida as definições aprovadas e registra propostas ainda sujeitas a refinamento. **Não autoriza implementação.** Será atualizado nesta branch durante as discussões; abrir PR para `develop` somente quando o responsável solicitar.

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

## 8. Proposta inicial de grupos de requisitos — ainda sem RFs normativos

| Grupo sugerido | Assuntos a especificar |
| --- | --- |
| RF-01 a RF-04 | Seleção, configuração, compatibilidade e apresentação visual dos perfis |
| RF-05 a RF-08 | Modelos de requisição/resposta |
| RF-09 a RF-12 | WebSocket, sessão, mensagens e encerramento |
| RF-13 a RF-18 | Broker e semânticas MQTT, Queue e Topic |

**Observação:** numeração e conteúdo são apenas uma proposta de organização. Nenhum RF v0.4 foi fechado ou aprovado individualmente até aqui.

## 9. Questões pendentes para as próximas discussões

1. **Compatibilidade aprovada:** detalhar papéis de produtor, consumidor e assinante e regras de configuração sem alterar a matriz aprovada.
2. **Configuração mínima aprovada para seis perfis:** detalhar defaults, limites, contratos de dados e propriedades ainda pendentes para WebSocket e mensageria.
3. **Requisição/resposta:** formalizar as diferenças já aprovadas em eventos, parâmetros e critérios de aceitação, sem duplicar a engine.
4. **WebSocket:** estabelecimento de sessão, direção das mensagens, sequência e conclusão.
5. **Broker:** configuração de filas/tópicos/assinaturas, fila vazia, número de consumidores, fan-out, latências e consumo pela API existente.
6. **Mensageria:** o que significa conclusão da execução principal versus entregas assíncronas? Quais eventos e métricas devem aparecer?
7. **Validação:** regras para rejeitar topologias incompatíveis, conexões duplicadas e cenários inválidos.
8. **Critérios de aceitação:** cenários mínimos observáveis por perfil, testes determinísticos e regressão da v0.3.
9. **Limites técnicos:** impacto no contrato OpenAPI, modelo do frontend e motor Java; necessidade de ADRs e de documentação de integração.
10. **Fatiamento:** confirmar entregas pequenas e dependências antes de criar as tarefas no Trello.

## 10. Método de trabalho documental

- Registrar cada nova decisão neste arquivo, identificando claramente **aprovado**, **proposto** ou **pendente**.
- Não introduzir requisitos extras sem debate e aprovação.
- Manter o escopo da v0.4 distinto do backlog futuro.
- Fazer commits incrementais nesta branch de planejamento.
- **Não abrir PR ou fazer merge até o responsável solicitar.**

## 11. Referências históricas

- [Requisitos v0.3](v0.3-client-requirements.md)
- [Catálogo discutido de clientes e perfis](v0.3-clients.md)
- [Levantamento de serviços e integração](v0.3-services.md)
- [Errata funcional da v0.3](v0.3-errata-client-behavior.md)

As decisões específicas para a v0.4 devem prevalecer sobre sugestões antigas que extrapolem o escopo desta versão.
