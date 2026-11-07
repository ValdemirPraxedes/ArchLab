# Regressão histórica v0.1–v0.5 (V6-22)

O objetivo é que nenhuma mudança da v0.6 altere silenciosamente o que as versões anteriores respondiam.

| Arquivo | Papel |
| --- | --- |
| `harvest-corpus.cjs` | Congela o corpus de requests de um checkout: exemplos de request de `openapi.yaml` e `openapi-v2.yaml`, `docs/api/examples/**.json` e blocos JSON dos testes Java. Responses e templates (`%s`) são ignorados, e os corpos repetidos são deduplicados. |
| `corpus-v05.json` | 45 requests congelados da release v0.5 (`origin/main` @ `5e6ae09b`). |
| `diff-baseline.cjs` | Envia cada request aos 6 endpoints de `/api/v1` e `/api/v2` de dois servidores (baseline e candidato) e compara status e JSON canônico. Com `GOLDEN_OUT`, grava os hashes do baseline. |
| `golden-v05.json` | 270 pares `(status, sha256)` registrados contra a release v0.5. |
| `HistoricalRegressionV6Test` (backend) | Repete as 270 chamadas em todo `mvnw test` e compara com o golden, sem precisar do servidor antigo. |

**Única diferença tolerada:** um `dataOutcomes: []` vazio no nível raiz da resposta, campo aditivo da v0.6. Qualquer outra mudança, inclusive em mensagens de erro, quebra o teste: uma mutação de um caractere numa mensagem derrubou 187 das 270 verificações.

## Repetir o diferencial

```text
git worktree add ../archlab-v05 5e6ae09b          # ou um clone na release
(cd ../archlab-v05/backend && ./mvnw -q package -DskipTests)
(cd backend && ./mvnw -q package -DskipTests)
java -jar ../archlab-v05/backend/archlab-api/target/archlab-api-0.1.0-SNAPSHOT.jar --server.port=19050
java -jar backend/archlab-api/target/archlab-api-0.1.0-SNAPSHOT.jar --server.port=19060
cd docs/api/examples/regression
BASELINE_URL=http://127.0.0.1:19050 CANDIDATE_URL=http://127.0.0.1:19060 node diff-baseline.cjs
```

O script precisa do pacote `yaml` instalado em `../v0.6/node_modules` (`cd ../v0.6 && npm install`).

Para registrar uma nova referência intencional, depois de uma mudança aprovada, rode `harvest-corpus.cjs` sobre a nova release e `diff-baseline.cjs` com `GOLDEN_OUT`, e explique a mudança no PR.
