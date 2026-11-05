# OpenAPI v0.4.1 — validação executada

Contrato canônico: [`openapi.yaml`](openapi.yaml), versão `0.4.1`. As três rotas públicas permanecem sob `/api/v1`.

## Verificações reproduzíveis

Na raiz do repositório:

```powershell
npx --yes @redocly/cli lint docs/api/openapi.yaml
frontend/node_modules/.bin/openapi-typescript.cmd docs/api/openapi.yaml -o backend/target/schema-v4.1.d.ts
cd docs/api/examples/v0.4
npm install
npm run validate
$env:ARCHLAB_API_URL='http://127.0.0.1:18081'; npm run verify:live
```

O lint Redocly terminou sem erros; emite um aviso `info-license` porque o repositório não declara licença. O parser OpenAPI 3.1 e o gerador `openapi-typescript` concluíram. `npm run validate` aprovou os exemplos antigos e os dois exemplos embutidos no contrato, inclusive `mixedCommunicationRequest`. A verificação ao vivo aprovou 13 requisições positivas e 3 negativas contra o backend e validou cada resposta 200 pelo schema `SimulationResponse`, sequência do trace e limites do relógio virtual.

## Alterações do contrato

- `UnifiedScenario.entry` inicia uma única conexão; `scenario.kind` permanece nas três variantes legadas.
- `Api.behaviors` contém ações declarativas por gatilho. `BehaviorAction.oneOf` continua discriminado por `kind`.
- `SimulationResponse.trace` inclui `ACTION_EVENT` e correlação opcional nos eventos. `execution` expõe `mode: UNIFIED` e `responseStatusCode` quando há `RESPOND` explícito.
- `rootOperations` registra a operação inicial. `mainStatus` e `status` diferenciam conclusão principal e global.
- `DurationMs` de propriedades pode chegar a 1 bilhão de milissegundos; o orquestrador rejeita a execução quando o relógio ultrapassa 3.600.000 ms, com erro 422. São limites de camadas diferentes.

## Compatibilidade

O controlador escolhe o adaptador pelo formato da requisição: arquitetura v1 direta, `{architecture,scenario.kind}` de v0.4 ou `{architecture,scenario.entry}` de v0.4.1. Não infere nem converte automaticamente um payload sem discriminador/entrada. As variantes antigas preservam seus validadores, motores e respostas; testes de regressão e exemplos ao vivo comprovam a compatibilidade observada.
