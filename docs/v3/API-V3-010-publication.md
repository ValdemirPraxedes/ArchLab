# API-V3-010 — Publicação efetiva do OpenAPI v3

## Publicado

- `docs/api/openapi-v3.yaml` na branch `docs/v3-openapi-contract-review`.
- Arquivo integral API-V3-007, 4.363 linhas, com 156 schemas.
- Hash Git blob verificado após o envio: `d6e0865bfb9ca556af768857c9295cdc7bdce92b`.
- SHA-256 local do artefato de origem: `74603413b3d1d22ab7933760ca27228cf80b0d85470401741f8c0b6314ef2e78`.
- Sete scripts locais de contrato executados individualmente, todos com exit code 0.

## Pendente

- Publicação das 51 fixtures JSON no GitHub.
- Ferramenta independente de validação OpenAPI 3.1 e geração `openapi-typescript`.
- Replay real Java e testes de compatibilidade golden v1/v2.
- PR de implementação separado, após homologação.

**Não confundir** validação estrutural de JSON Schema com homologação semântica do motor.
Não foi feito merge na `develop` nem na `main`.
