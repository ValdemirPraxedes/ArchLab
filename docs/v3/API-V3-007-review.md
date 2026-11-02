# ArchLab v0.3 — Registro de revisão normativa do contrato

> Status: **revisão técnica em andamento; contrato OpenAPI v3 NÃO homologado para implementação**.
> Publicado em branch de documentação para revisão e rastreabilidade.

## Especificações aprovadas pelo responsável do projeto

- ADR-018 a ADR-029: fundamentos arquiteturais.
- EP-V3-001: ExecutionPlan e nove tipos de etapa.
- DM-V3-001: PropertyValue, capacidades, endpoints, operações, recursos e relações.
- CM-V3-001: matriz de compatibilidade e níveis de suporte.
- CE-V3-001: catálogo executável inicial (Client, API, Database, Broker, Queue e Telemetry).
- EV-V3-001: eventos e máquinas de estado.
- SCH-V3-001: scheduler e microetapas/barreiras causais.
- EC-V3-001: 32 eventos e payloads Java 25 propostos.
- DT-V3-001: dispatcher e EffectBatch com commit lógico atômico.
- RS-V3-001: resultados principal/global, métricas, trace e integridade.
- API-V3-001: estrutura da API REST OpenAPI 3.1.

## Revisões técnicas do artefato API-V3-002 a API-V3-007

O artefato produzido fora do repositório usa quatro endpoints `/api/v3`:
- `POST /api/v3/architectures/validate`
- `POST /api/v3/execution-plans/validate`
- `POST /api/v3/scenarios/validate`
- `POST /api/v3/simulations`

**API-V3-007, validação local:** 156 schemas, 32 payloads discriminados com 32 fixtures, 38 tipos públicos de trace, 96 mutações negativas de contrato rejeitadas e testes anteriores passando.

**Não homologado:** estes testes comprovam estrutura JSON Schema, não execução do motor nem semântica dos handlers. A matriz evento/efeitos é ilustrativa.

## Bloqueadores para homologação

1. Publicar o YAML integral e as fixtures testadas no repositório; este registro **não substitui** esses arquivos.
2. Validação independente com ferramenta OpenAPI 3.1.
3. Geração `openapi-typescript` e build/testes do frontend.
4. Matriz concreta dos 32 handlers: `ScheduledEvent → DispatchDecision → EffectBatch → TraceRecord → StateDelta`.
5. Replay real do runtime Java e regressões golden de v1/v2.
6. Limites operacionais finais permanecem provisórios.

## Política de branches

PR deste trabalho tem como base `develop`. Não realizar merge automático na `main`. Mudanças de implementação devem vir em PRs separados depois de homologar os contratos.
