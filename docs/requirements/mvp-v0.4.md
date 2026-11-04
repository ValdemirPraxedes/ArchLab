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

## 6. Proposta inicial de grupos de requisitos — ainda sem RFs normativos

| Grupo sugerido | Assuntos a especificar |
| --- | --- |
| RF-01 a RF-04 | Seleção, configuração, compatibilidade e apresentação visual dos perfis |
| RF-05 a RF-08 | Modelos de requisição/resposta |
| RF-09 a RF-12 | WebSocket, sessão, mensagens e encerramento |
| RF-13 a RF-18 | Broker e semânticas MQTT, Queue e Topic |

**Observação:** numeração e conteúdo são apenas uma proposta de organização. Nenhum RF v0.4 foi fechado ou aprovado individualmente até aqui.

## 7. Questões pendentes para as próximas discussões

1. **Compatibilidade:** quais pares de tipos de componentes podem usar cada perfil? O que exigir para origens, destinos e papéis de produtor, consumidor e assinante?
2. **Configuração mínima:** quais campos aparecem no editor por perfil, e quais são parâmetros de execução?
3. **Requisição/resposta:** diferenças semânticas indispensáveis para REST, SOAP, GraphQL, gRPC e CoAP, sem duplicar a engine.
4. **WebSocket:** estabelecimento de sessão, direção das mensagens, sequência e conclusão.
5. **Broker:** configuração de filas/tópicos/assinaturas, fila vazia, número de consumidores, fan-out, latências e consumo pela API existente.
6. **Mensageria:** o que significa conclusão da execução principal versus entregas assíncronas? Quais eventos e métricas devem aparecer?
7. **Validação:** regras para rejeitar topologias incompatíveis, conexões duplicadas e cenários inválidos.
8. **Critérios de aceitação:** cenários mínimos observáveis por perfil, testes determinísticos e regressão da v0.3.
9. **Limites técnicos:** impacto no contrato OpenAPI, modelo do frontend e motor Java; necessidade de ADRs e de documentação de integração.
10. **Fatiamento:** confirmar entregas pequenas e dependências antes de criar as tarefas no Trello.

## 8. Método de trabalho documental

- Registrar cada nova decisão neste arquivo, identificando claramente **aprovado**, **proposto** ou **pendente**.
- Não introduzir requisitos extras sem debate e aprovação.
- Manter o escopo da v0.4 distinto do backlog futuro.
- Fazer commits incrementais nesta branch de planejamento.
- **Não abrir PR ou fazer merge até o responsável solicitar.**

## 9. Referências históricas

- [Requisitos v0.3](v0.3-client-requirements.md)
- [Catálogo discutido de clientes e perfis](v0.3-clients.md)
- [Levantamento de serviços e integração](v0.3-services.md)
- [Errata funcional da v0.3](v0.3-errata-client-behavior.md)

As decisões específicas para a v0.4 devem prevalecer sobre sugestões antigas que extrapolem o escopo desta versão.
