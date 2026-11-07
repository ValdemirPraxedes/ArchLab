# Contrato v0.6 — fixtures e verificações

> **Status:** exemplos para revisão contratual; não significa execução pelo backend v0.5.

## Arquivos

- `relational-insert-select.json`: SimulationRequest P0 com Service e Relational Database.
- `key-value-actions.json`: nó e sequência de ações de chave/valor.
- `etl-actions.json`: Pipeline com EXTRACT → TRANSFORM → LOAD.
- `invalid-action.invalid.json`: Action de DB_SELECT apontando para Key-Value; **erro semântico**, embora o JSON seja estruturalmente válido.
- `migration-legacy.json`: entrada histórica `DATABASE` e plano ilustrativo de migração explícita.

## Validação local

```bash
cd docs/api/examples/v0.6
npm install
npm run validate
```

O script faz parse do YAML, verifica discriminadores, usa JSON Schema/Ajv 2020 para exemplos e verifica invariantes básicas. **Não substitui** validação OpenAPI 3.1 por ferramenta especializada, compatibilidade de migração no runtime, smoke tests contra a API Java, nem geração de tipos TypeScript. Não foi executado no ambiente do GitHub por esta alteração documental.

## Gates ainda pendentes

1. Executar o script acima, corrigir problemas do schema/fixtures e executar `npm run validate` dos exemplos v0.4.
2. Executar Redocly/OpenAPI lint e validação de `$ref`, `oneOf` e discriminação.
3. Gerar TypeScript e comparar com tipos manuais; revisar DTOs/mappers Java.
4. Testar conversão explícita `DATABASE` legada, preservando IDs e dependências.
5. Aprovar compatibilidade e semântica antes de iniciar implementação P0.

**Importante:** `DATABASE` aparece no `Component.oneOf` exclusivamente para entrada legada, garantindo que fixtures antigas possam ser lidas. O contrato de criação/exportação canônica e o editor devem rejeitar novos nós genéricos. Um schema de leitura separado poderá formalizar essa diferença na etapa de compatibilidade.
