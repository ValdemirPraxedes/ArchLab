# ArchLab — editor visual v0.4

O editor React + TypeScript usa os três endpoints canônicos `/api/v1` para validar arquitetura, validar cenário e simular. A geração de tipos parte de `../docs/api/openapi.yaml`.

## Requisitos
Node.js 22 e npm; backend local na porta 8080.

## Executar
```bash
cd frontend
npm install
npm run dev
```
Acesse http://localhost:5173.

## Editor

- Adicione um dos seis tipos de cliente, API, Database ou Broker; configure latência e perfil em cada conexão.
- A matriz oferece HTTP REST, SOAP HTTP, HTTP genérico, GraphQL HTTP, gRPC unary, CoAP e WebSocket para cliente/API → API; `DATABASE_QUERY` para API → Database; Queue, Topic e MQTT QoS 0 para cliente/API → Broker e Broker → API.
- Configure destinos e assinaturas no Broker. Uma assinatura usa uma conexão Broker → API do mesmo perfil do destino.
- Escolha `REQUEST_RESPONSE`, `WEBSOCKET_SESSION` ou `MESSAGE_PUBLISH`. Para WebSocket, edite mensagens e fechamento na conexão. Para publicação, selecione destino e quantidade.
- A reprodução usa o trace e o relógio virtual do backend; mostra recursos, mensagens, métricas e os resultados principal e global.

## Verificar

```bash
npm test
npm run lint
npm run build
```

Os exemplos executáveis e a verificação de contrato ao vivo estão em `../docs/api/examples/v0.4`.

O cliente REST usa `/api/v1` via proxy do Vite para http://localhost:8080. Sob outra URL, configure `VITE_API_BASE_URL` em `.env.local`.
