# OPENAPI-V4.1-VALIDATION — Verificação inicial do contrato misto

> Status: **verificação estática parcial**, em 2026-11-04. Não homologado para implementação.
> Fonte: [openapi.yaml](openapi.yaml), branch `docs/v0.4.1-mixed-communication-planning`.

## Escopo já realizado

- Atualizado `info.version` para `0.4.1-contract-draft`.
- Mantidos os três endpoints públicos em `/api/v1`.
- `Scenario` agora contém quatro formatos disjuntos propostos: `UnifiedScenario` sem `kind` e os três cenários legados com `kind`.
- Acrescentados `Behavior`, `BehaviorMatcher`, nove tipos de `BehaviorAction`, dependências tipadas, `SessionSetup` e exemplo `mixedCommunicationRequest`.
- Preservados os nomes/discriminadores das integrações v0.4.
- Feita checagem textual automatizada sobre o arquivo revisado: **84 definições de schema**, **251 ocorrências de referências de schema**, **nenhuma referência de schema não encontrada** e **nenhum nome de schema duplicado**; referências de exemplos resolvidas pelos nomes declarados. Essa checagem não substitui o parse YAML nem a validação de JSON Schema.

## Bloqueios antes de declarar contrato validado

1. Executar parse de YAML 1.2/OpenAPI 3.1 com ferramenta apropriada; confirmar preservação de anchors/aliases legados e ausência de duplicação de chaves.
2. Executar lint Redocly (incluindo `oneOf`, `discriminator` e integridade de `$ref`).
3. Validar `components/examples/mixedCommunicationRequest` e os exemplos legados contra `SimulationRequest`, incluindo valores `const`, `required` e `additionalProperties`.
4. Gerar TypeScript com `npx openapi-typescript docs/api/openapi.yaml -o backend/target/schema-v4.1.d.ts` (ajustar diretório conforme ambiente).
5. Confirmar que `BehaviorMatcher` não aceita combinações semanticamente incompatíveis por gatilho; isso exige validador de domínio e testes.
6. Confirmar que `SessionSetup` gera evento causal OPEN_SESSION e fechamento terminal com `autoCloseAtEnd`, evitando recursos OPEN preexistentes.
7. Verificar semântica de `FORK`/`JOIN` e limites globais no validador/engine antes de expor ao frontend.
8. Implementar adapter backend para novo `UnifiedScenario`; a edição de `openapi.yaml` sozinha **não** torna o payload executável.
9. Comparar contrato novo e três variantes antigas com testes de regressão da API.
10. Conciliar valores de tempo da definição `DurationMs` com limite operacional da engine.

## Comandos indicativos para validação local

```bash
npx @redocly/cli lint docs/api/openapi.yaml
npx openapi-typescript docs/api/openapi.yaml -o backend/target/schema-v4.1.d.ts
```

Executar também validação dos exemplos por JSON Schema 2020-12 e testes de contrato Java. **Não afirmar que estes comandos já foram executados.**

## Condição de saída

Só marcar a etapa de contrato como homologada quando parser, lint, exemplos, TypeScript, compatibilidade semântica e testes do adapter estiverem evidenciados. A documentação aqui corresponde a uma proposta no PR, não a uma feature pronta.
