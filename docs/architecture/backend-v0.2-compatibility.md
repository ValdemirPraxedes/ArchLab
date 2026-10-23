# Backend v0.2 — Contratos e preservação da API v1 (BE-01)

**Escopo:** preparação técnica e testes de regressão. Este documento complementa, mas não substitui, `mvp-v0.2.md`, `ADR-v0.2.md`, `openapi.yaml` e `openapi-v2.yaml` em `develop`.

## Contratos públicos

| Interface | Política |
| --- | --- |
| `POST /api/v1/architectures/validate` | Preservar JSON de entrada, regras e formato `valid/violations` |
| `POST /api/v1/simulations` | Preservar processamento obrigatório de um Client, uma API e um Database |
| `POST /api/v2/architectures/validate` | Novo DTO/validador de grafo v2; não encaminhar pela validação da v1 |
| `POST /api/v2/scenarios/validate` | Separar Architecture e Scenario e validar referências sem executar |
| `POST /api/v2/simulations` | Novo adaptador de entrada/saída v2 sobre o mesmo DES do core |

## Camadas e ordem de implementação

1. **BE-01:** congelar comportamento público v1 com testes HTTP de regressão; revisar o esquema v2 e explicitar fronteiras.
2. **BE-02 a BE-04:** novos modelos v2, grafo, integrações e cenário em Java puro, separados dos DTOs v1.
3. **BE-05 a BE-08:** expandir o `SimulationEngine` existente para ativação e transporte de chamadas síncronas aninhadas, correlação, estados, trace e métricas dinâmicas. Preservar caminho de execução v1 por adaptador/entrada legada, não por motor duplicado.
4. **BE-09:** implementar controladores, DTOs e mapeamento v2, Problem Details e cobertura completa S01–S15; executar também a suíte de regressão v1.

## Invariantes v1 protegidos

- Validação de arquitetura válida: HTTP 200, `valid=true`, nenhuma violação.
- Arquitetura sem Database: HTTP 200 com `valid=false` no endpoint de validação; HTTP 422 em simulação.
- Cenário versionado: total **260 ms**, comunicação **60 ms**, espera API **170 ms** (sobreposta); sete atividades nos intervalos `[0,20]`, `[20,50]`, `[50,60]`, `[60,210]`, `[210,220]`, `[220,240]`, `[240,260]`.
- Trace público ordenado por `timestampMs` e `sequence`, com eventos de estado, mensagem e processamento; `requestId`, `exchangeId` e `messageId` correlacionados.
- Rótulos legados `REST`, `SOAP` e `TCP` continuam aceitos na v1. Os novos `HTTP_REST`, `SOAP_HTTP` e `DATABASE_QUERY` pertencem ao contrato v2.
- JSON malformado continua HTTP 400 em `application/problem+json`.

## Verificação do contrato v2

`openapi-v2.yaml` já foi consultado em `develop` e define três endpoints POST, uma `ArchitectureV2` separada de `ScenarioV2` e `SimulationResponseV2` com `execution`, `initialState`, `trace`, `timeline`, `metrics` e `messageRepresentations`.

**Pendente em BE-01:** executar verificação automática do OpenAPI 3.1 e geração `openapi-typescript` no ambiente de desenvolvimento; registrar saídas reais. Não declarar essa verificação aprovada apenas porque o documento foi lido.

## Testes

Nova suíte `V1PublicContractRegressionTest` exercita HTTP real em porta aleatória, não usa mocks do core e cobre compatibilidade de entrada, saída, status, timelines e trace. Executar no diretório `backend`:

```powershell
.\mvnw clean verify
```

Nenhuma alteração nos endpoints ou DTOs públicos da v1 deve ser feita como parte desta tarefa. O PR da BE-01 deve permanecer sem merge até aprovação explícita.
