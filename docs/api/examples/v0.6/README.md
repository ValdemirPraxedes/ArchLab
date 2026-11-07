# Contrato v0.6 — fixtures e verificações

> **Status:** exemplos de contrato e implementação P0 parcial. A homologação v0.6 continua pendente.

## Arquivos

- `relational-insert-select.json`: SimulationRequest P0 com Service e Relational Database.
- `key-value-actions.json`: nó e sequência de ações de chave/valor.
- `etl-actions.json`: Pipeline com EXTRACT → TRANSFORM → LOAD.
- `etl-identity-simulation.json`: requisição completa de ETL `IDENTITY` usada no perfil da API.
- `invalid-action.invalid.json`: Action de DB_SELECT apontando para Key-Value; **erro semântico**, embora o JSON seja estruturalmente válido.
- `migration-legacy.json`: entrada histórica `DATABASE` e plano ilustrativo de migração explícita.

- `ct/CT-6-01.json` … `ct/CT-6-10.json`: fixtures de aceite da v0.6 (V6-21). Cada arquivo tem `cases[]` com `endpoint`, `request`, `expect` (status, `dataOutcomes` por `actionId` em ordem, `fields` por JSON Pointer, `bodyContains`) e `replay` (repete a requisição e exige resposta idêntica). São executadas contra o backend por `Ct6FixtureReplayTest` (`cd backend && ./mvnw test`) e validadas estruturalmente contra o OpenAPI por `npm run validate`; `schemaValid: false` marca casos que o próprio contrato rejeita.

- `contract-cases.json`: 14 documentos `Architecture` válidos e inválidos para a fronteira canônica × histórica (V6-04), conferidos pelo schema e pelo backend (`ContractCasesV6Test`).
- `normative/N6-01..08.json`: exemplos normativos de budgets, custo, atomicidade e estados (V6-02, DATA-BUDGETS-V6-001).
- `migration/M6-01..04.json`: arquiteturas legadas com relatório de conversão esperado e equivalência de simulação (V6-03/V6-14, MIGRATION-V6-001).
- `capability-matrix-v6.json`: matriz normativa tipo × Action (V6-01).
- `canonical-export-p0.json`: golden da exportação do editor (V6-20).

## Validação local

```bash
cd docs/api/examples/v0.6
npm install
npm run validate
```

O script faz parse do YAML, verifica discriminadores e usa JSON Schema/Ajv 2020 para exemplos. Arquiteturas com `schemaVersion: "0.6"` rejeitam `API` e `DATABASE`; documentos históricos sem marcador continuam legíveis para migração explícita. Os testes de integração Java verificam operações P0 e o frontend gera os tipos TypeScript.

Para repetir o perfil local da API, inicie o backend na porta 18081 e execute `npm run profile:api`. Use `ARCHLAB_PROFILE_KIND=etl` para o cenário ETL, `ARCHLAB_PROFILE_ITERATIONS` (1..1000) e `ARCHLAB_PROFILE_CONCURRENCY` (1..32) para variar a carga. O script confere os resultados de cada cenário e respostas idênticas entre execuções; medidas e limites estão em `docs/implementation/V6-LIMIT-EVIDENCE.md`.

## Gates ainda pendentes

1. Completar a reconciliação entre DTOs Java, tipos manuais e schema, incluindo resultado local de `ETL_TRANSFORM`.
2. Homologar a política de migração quando `DATABASE_QUERY` ou `CALL` históricos tornam a conversão ambígua.
3. Validar as regras declarativas `FILTER_VALID` e `NORMALIZE_METADATA` em cenários CT-6 completos e concluir CT-6-01 a CT-6-10.

`npx --yes @redocly/cli@2.76.0 lint docs/api/openapi.yaml` roda no CI e valida o OpenAPI com dois avisos aceitos (`info-license` e `LegacyApiMigrationV5` não referenciado). O validador de fixtures v0.4 também passou.

**Fronteira (decisão 1):** `Component` é a união de leitura (canônicos + `Api`/`Database` deprecated). Com `schemaVersion: "0.6"`, `Architecture` aceita apenas `CanonicalComponentV6`; o editor exporta esse marcador e não oferece `DATABASE` na paleta. Sem o marcador, a API mantém a compatibilidade histórica, e a conversão passa por `migrate-v6`.
