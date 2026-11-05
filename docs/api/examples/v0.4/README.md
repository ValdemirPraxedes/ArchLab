# Exemplos de contrato v0.4

Cada JSON é uma requisição completa para `POST /api/v1/scenarios/validate` ou `POST /api/v1/simulations`. Os arquivos `.invalid.json` são casos negativos intencionais.

`mixed-communication.json` usa `UnifiedScenario`: entrada REST, publicação Queue e notificação WebSocket em uma execução. As outras amostras preservam os formatos v0.4 e exercitam a compatibilidade.

| Arquivo | Cobertura |
| --- | --- |
| `mixed-communication.json` | REST → Queue → WebSocket com sessão aberta por evento virtual |
| `http-rest.json`, `soap-http.json`, `http-generic.json` | Perfis HTTP |
| `graphql-http.json`, `grpc-unary.json`, `coap.json` | Outros perfis síncronos |
| `database-query.json` | Integração legada API → Database |
| `websocket-session.json` | Sessão bidirecional com duas mensagens |
| `queue-two-consumers.json` | Queue com dois consumidores elegíveis |
| `queue-no-consumer.json` | Queue sem consumidor, timeout virtual de 30.000 ms |
| `topic-two-subscribers.json` | Topic com duas assinaturas elegíveis |
| `mqtt-qos0.json` | MQTT QoS 0 com tópico exato |
| `mqtt-qos1.invalid.json` | QoS não permitido pelo schema |
| `websocket-too-many-messages.invalid.json` | 101 mensagens, acima do limite de 100 |
| `missing-broker-destination.invalid.json` | Destino ausente na configuração do Broker; violação de domínio |

Para repetir a validação de schema e as verificações de domínio incluídas aqui, a partir desta pasta:

```sh
npm install
npm run validate
```

O verificador confirma formato, entrada, destino e coerência básica das assinaturas. Para conferir as respostas reais dos 13 exemplos positivos e os erros dos 3 negativos, inicie o backend e execute:

```sh
ARCHLAB_API_URL=http://localhost:8080 npm run verify:live
```

`verify:live` valida cada resposta 200 contra `SimulationResponse` do OpenAPI 3.1 e confere sequência do trace e limites do relógio virtual. O backend também testa round-robin, fan-out, timeout e regressão das rotas v1/v2 em `mvn test`.
