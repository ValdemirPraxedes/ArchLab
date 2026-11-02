# API-V3-008 — Critérios executáveis de homologação do contrato

**Escopo:** revisão de contrato, sem implementação do backend e sem alterar APIs v1/v2.
**Estado:** bloqueado até publicação do `docs/api/openapi-v3.yaml` integral e de suas fixtures. NÃO homologado.

## Evidências locais (2026-11-01/02)

O pacote local de trabalho `archlab-v3-openapi-api-v3-007.zip` contém 156 schemas e 32 variantes `EventPayload`.
Sete scripts `tests/test_*.py` foram executados individualmente com exit code 0. Isso **não** é teste de Java, replay efetivo ou validação independente da OpenAPI.
O comando `python -m unittest discover` **não é o runner correto** para esses scripts: eles não são `unittest.TestCase`.

## Portões para homologação

### G1 — Integridade e versão do documento
- `openapi: 3.1.0`
- exatamente quatro operações POST previstas: `/api/v3/architectures/validate`, `/api/v3/execution-plans/validate`, `/api/v3/scenarios/validate`, `/api/v3/simulations`.
- verificar todas as referências `$ref`, enums, discriminadores e `oneOf`.
- executar um validador OpenAPI 3.1 específico (por exemplo Redocly CLI).

### G2 — Geração TypeScript (frontend)
Executar a partir da **raiz do repositório**, após publicação do YAML e instalação das dependências de desenvolvimento:

```powershell
cd frontend
npx openapi-typescript ../docs/api/openapi-v3.yaml -o src/api/generated/schema-v3.d.ts
npm run build
npm test
```

O `openapi-typescript` aqui é verificação/geração de DTOs, não implementação de novas telas.

### G3 — Schemas e casos negativos
- Nove tipos de `ExecutionStepV3`.
- 32 payloads de evento, com `kind` obrigatório e eventos em fases compatíveis.
- `JOIN QUORUM` exige quórum válido.
- `horizonAtMs` é obrigatório para `UNTIL_HORIZON`.
- Rejeitar propriedades desconhecidas em DTOs fechados.

### G4 — Invariantes de resultado/trace
- Não transformar HTTP de negócio simulado em status HTTP 500 da API.
- `RESPONDED` possui duração de resposta, mesmo se outcome for erro de negócio.
- `IN_PROGRESS` após limite/horizonte pode ter campos de conclusão nulos.
- `COMPLETED` global exige work resolvido e tempos de conclusão consistentes.
- Deltas com versões, registros de trace e ordem referencial consistente.

### G5 — Reconstrução
- Distinguir `PUBLIC_SNAPSHOT` de `FULL_RUNTIME`.
- Para os 32 eventos, registrar e testar `ScheduledEvent → DispatchDecision → EffectBatch → TraceRecord → StateDelta`.
- Testar barreira de deadline e fases 0/10/20/30/40.
- Testar snapshot reconstruído = estado final projetado; teste sintético não prova replay real do motor.
- Para `FULL_RUNTIME`, incluir cursores, timers, leases, wait claims, policies, step results, branch cursors, work items, event queue e ID counters.
- Documentar e verificar se o relógio/posição final do scheduler deriva do journal ou é âncora explícita.

### G6 — Compatibilidade
- `docs/api/openapi.yaml` e `openapi-v2.yaml` permanecem inalterados.
- v1: golden fixtures preservadas; v2: golden fixtures, RF-044 e overrides.
- Nenhum merge na `main` sem autorização de release.

## Ordem da próxima execução

1. Publicar o YAML real e fixtures (não substituir por um schema parcial).
2. Executar G1 e G2 em ambiente com dependências instaladas.
3. Migrar testes normativos definitivos para JUnit 5 e Vitest/TypeScript; Python permanece ferramenta temporária do rascunho.
4. Completar G3–G6 e anexar evidências ao PR.
5. Somente após homologação preparar implementação do motor em PR separado.

## Notas de publicação

O commit deste arquivo **não inclui** `openapi-v3.yaml` nem fixtures. A documentação de controle de aprovação é distinta da publicação dos artefatos completos.
