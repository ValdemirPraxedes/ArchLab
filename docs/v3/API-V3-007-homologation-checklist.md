# ArchLab v0.3 — plano de homologação OpenAPI (API-V3-007)

**Status:** documentação aprovada para continuar o trabalho técnico; contrato YAML completo ainda pendente de publicação e homologação.

## Fontes normativas

ADR-018–ADR-029; EP-V3-001, DM-V3-001, CM-V3-001, CE-V3-001, EV-V3-001, SCH-V3-001, EC-V3-001, DT-V3-001, RS-V3-001 e API-V3-001.

## Artefato preparado localmente

O pacote `archlab-v3-openapi-api-v3-007.zip` foi produzido fora do repositório. Ele contém `archlab-v3-contract/openapi-v3.yaml`, examples/, tests/, relatórios e scripts auxiliares. **Os arquivos do ZIP não fazem parte deste PR até serem adicionados individualmente ao Git.**

Resultados reportados para os testes locais: 156 schemas, 32 payloads de eventos tipados, 38 tipos de trace e 96 mutações negativas rejeitadas. Esses resultados NÃO comprovam execução do Java.

## Destino previsto no repositório

- `docs/api/openapi-v3.yaml`
- `docs/examples/v3/` (fixtures da API, eventos, reconstrução)
- `docs/contracts/v3/tests/` (testes de contrato e instruções)
- `docs/v3/` (relatórios e matriz de cobertura)

## Validação antes de homologar

1. YAML parseável + todas as referências `$ref` locais resolvidas.
2. Ferramenta específica OpenAPI 3.1 (ex.: Redocly CLI ou equivalente).
3. `npx openapi-typescript docs/api/openapi-v3.yaml -o frontend/src/api/generated/schema-v3.d.ts` executado a partir da raiz do repositório, com versões Node/dependências compatíveis.
4. Testar schemas `oneOf` dos nove ExecutionStep e 32 EventPayload.
5. Testar consistência do status principal/global, timestamps nulos e completude do trace.
6. Verificar `ScheduledEvent → DispatchDecision → EffectBatch → TraceRecord → StateDelta` para os 32 eventos.
7. Validar replay sintético do snapshot público e interno, e finalmente replay do runtime Java quando implementado.
8. Testes de compatibilidade golden v1/v2, com RF-044.
9. Revisar segurança dos exemplos e limites numéricos provisórios.

## Orientações de integração

- Preservar `docs/api/openapi.yaml` e `docs/api/openapi-v2.yaml`.
- Branch de trabalho: `docs/v3-openapi-contract-review`; PR base: `develop`.
- Não fazer merge automático na `main`.
- Não afirmar que os endpoints v3 foram implementados só porque o documento existe.
- Contrato só pode ser homologado após testes OpenAPI e geração TypeScript reais.

## Pendências de maior risco

- O contrato de trace do runtime precisa cobrir os 32 eventos e suas mudanças internas sem dados externos não rastreados.
- Fixtures sintéticas de reconstrução não equivalem a replay do motor real.
- Posição final do scheduler precisa ser derivável dos fatos/deltas ou documentada como âncora explícita.
- Limites de payload, eventos, profundidade e retorno continuam provisórios.
