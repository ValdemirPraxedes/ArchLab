# API-V3-009 — Inventário verificável de artefatos e testes

Estado: **aprovado para prosseguir; contrato integral ainda não publicado nem homologado**.

## Artefatos verificados no ambiente local

- Caminho: `archlab-v3-contract/openapi-v3.yaml`
- Formato: OpenAPI `3.1.0`
- Tamanho: **116723 bytes**
- SHA-256: `74603413b3d1d22ab7933760ca27228cf80b0d85470401741f8c0b6314ef2e78`
- Schemas: **156**
- Rotas: quatro operações POST sob `/api/v3`
- Fixtures JSON presentes: **51**, contando recursivamente o diretório examples.

## Execução local dos sete scripts

Todos terminaram com exit code 0:
- `test_contract.py`
- `test_review.py`
- `test_api_v3_003.py`
- `test_api_v3_004.py`
- `test_api_v3_005.py`
- `test_api_v3_006.py`
- `test_api_v3_007.py`

**Limitação:** os scripts são verificações locais de estrutura, JSON Schema e projeção sintética. Não comprovam conformidade pelo validador oficial OpenAPI 3.1, typegen TypeScript, replay do motor Java ou compatibilidade semântica do domínio.

## Próximo portão (pendente)

1. Publicar **o YAML exato com o hash acima** em `docs/api/openapi-v3.yaml` na branch `docs/v3-openapi-contract-review`.
2. Publicar as fixtures e testar `openapi-typescript` com `npm run build` no frontend.
3. Rodar validador independente OpenAPI 3.1.
4. Confirmar fixtures de trace, replay e golden v1/v2 pelo backend Java.
5. Homologar contrato e somente depois abrir PR separado para implementação.

Nenhum arquivo YAML/fixture foi adicionado por esta revisão; este commit documenta as evidências reproduzíveis.
