# Exemplos de contrato v0.4

Cada JSON é uma requisição completa para `POST /api/v1/scenarios/validate` ou `POST /api/v1/simulations`. Os arquivos `.invalid.json` são casos negativos intencionais.

| Arquivo | Cobertura |
| --- | --- |
| `http-rest.json`, `soap-http.json`, `http-generic.json` | Perfis HTTP |
| `graphql-http.json`, `grpc-unary.json`, `coap.json` | Outros perfis síncronos |
| `database-query.json` | Integração legada API → Database |
| `websocket-session.json` | Sessão bidirecional com duas mensagens |
| `queue-two-consumers.json` | Queue com dois consumidores elegíveis |
| `queue-no-consumer.json` | Queue sem consumidor, para teste posterior de timeout virtual |
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

O verificador confirma formato, entrada, destino e coerência básica das assinaturas. Resultados de execução como round-robin, fan-out e timeout dependem de testes do backend após implementação.
