# Validação estática do contrato OpenAPI v3 — 2026-11-01

Base: `develop`, árvore inicialmente limpa. Branch: `fix/openapi-v3-validation`.

## Diagnósticos e correções

Redocly 2.72.1 antes: 4 erros e 13 avisos.

- `security-defined` (4): declarar `security: []` no contrato de planejamento, que não define autenticação.
- `tag-description` (4): documentar as quatro tags.
- `no-required-schema-properties-undefined` (4): declarar propriedades com schemas vazios nos subschemas condicionais de `quorum` e `horizonAtMs`. As restrições continuam nas propriedades do objeto externo; a semântica de presença e ausência permanece.
- `no-illogical-composition-keywords` (2): remover repetição de `SchedulerPositionTraceDetailV3` no `oneOf` e da condição `SCHEDULER_POSITION_CHANGED` no `allOf`. O primeiro corrigia a rejeição indevida de um detalhe válido por corresponder a duas alternativas idênticas.
- `no-unused-components` (2): exceções por ponteiro em `.redocly.lint-ignore.yaml`, preservando `ScheduledEventEnvelopeV3` e `EventEffectTraceCoverageV3`, schemas internos normativos sem uso nos endpoints HTTP.
- `info-license` (1): exceção por ponteiro; o repositório não declara licença. Não atribuir uma licença durante uma correção de contrato.

Depois: zero erros e zero avisos ativos em Redocly 1.34.22 e 2.72.1, com **3 apontamentos explicitamente ignorados**, justificados acima. As exceções não desativam regras globalmente.

## Validações executadas

- `npx --yes @redocly/cli@1 lint docs/api/openapi-v3.yaml --extends=recommended --format=summary`: passou, mesmo comando base do CI.
- `npx --yes @redocly/cli lint docs/api/openapi-v3.yaml --format=summary`: passou.
- `npx --yes openapi-typescript@7 docs/api/openapi-v3.yaml -o <temporário>/archlab-schema-v3.d.ts`: passou com 7.13.0; nenhum DTO gerado foi incorporado ao frontend.
- Ajv 8 / JSON Schema 2020-12, `ajv-formats` e parser YAML em pasta auxiliar externa ao repositório: 156 schemas verificados e compilados; 516 referências locais resolvidas; 38 composições sem alternativas idênticas; 9 tipos de passo e 32 payloads de evento preservados. `int64` tratado como formato de inteiro, sem impor novos limites numéricos.
- `docs/examples/v3/architecture-simple.json`: JSON válido e aceito por `ArchitectureDefinitionV3`.
- 11 verificações positivas/negativas: exemplo, rejeição de propriedade desconhecida, exigência de horizonte em `UNTIL_HORIZON`, proibição de horizonte em `DRAIN_ALL`, exigência de quórum positivo em `QUORUM`, aceitação de detalhe `SCHEDULER_POSITION` e rejeição de propriedade desconhecida nesse detalhe.
- Backend: `./mvnw.cmd test -q`: 124 testes, 20 suites, zero falhas/erros/ignorados.
- Frontend: `npm test`: 76 testes, 17 arquivos, todos passaram. `npm run build`: passou, incluindo typegen v1/v2, TypeScript e Vite.
- `git diff --check`: passou. Contratos v1/v2 e arquivos gerados existentes sem alterações.

A validação é estática. Os testes e o build verificam as versões implementadas; não comprovam funcionamento HTTP ou replay do motor v3. A validação funcional v3 permanece para quando houver implementação.
