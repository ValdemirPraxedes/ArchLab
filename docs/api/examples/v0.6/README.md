# Contrato v0.6 — fixtures e verificações

> **Status:** exemplos de contrato e implementação P0 parcial. A homologação v0.6 continua pendente.

## Arquivos

- `relational-insert-select.json`: SimulationRequest P0 com Service e Relational Database.
- `key-value-actions.json`: nó e sequência de ações de chave/valor.
- `etl-actions.json`: Pipeline com EXTRACT → TRANSFORM → LOAD.
- `etl-identity-simulation.json`: requisição completa de ETL `IDENTITY` usada no perfil da API.
- `invalid-action.invalid.json`: Action de DB_SELECT apontando para Key-Value; **erro semântico**, embora o JSON seja estruturalmente válido.
- `migration-legacy.json`: entrada histórica `DATABASE` e plano ilustrativo de migração explícita.

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

Neste checkpoint, `npx --yes @redocly/cli lint docs/api/openapi.yaml` validou o OpenAPI com dois avisos preexistentes (`info-license` e `LegacyApiMigrationV5` não referenciado). O validador de fixtures v0.4 também passou.

**Importante:** `Component.oneOf` ainda permite leitura legada. O marcador `schemaVersion: "0.6"` impõe a restrição canônica em `Architecture`; o editor exporta esse marcador e não oferece `DATABASE` na paleta. Sem o marcador, a API mantém a compatibilidade histórica.
