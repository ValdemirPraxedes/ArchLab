# Etapa 12 — Relatório de validação do contrato OpenAPI v1

> **Status em 2026-11-04: rascunho estrutural criado; homologação técnica PENDENTE.** Documento de evidências da evolução de `docs/api/openapi.yaml`. Este relatório não afirma aprovação do contrato completo.

## 1. Alterações realizadas na branch de planejamento

| Parte | Alteração documental | Commit |
| --- | --- | --- |
| 12.1 | Uso das estruturas v2 como base do contrato unificado com três rotas `/api/v1` e schemas canônicos sem sufixo V2 | `f48aa71` |
| 12.2 | Inclusão dos dez perfis, Broker, seis tipos de clientes, CLIENT legado e cenários discriminados | `7736c6c` |
| Correção 12.2 | Eliminação de blocos duplicados e normalização de schemas e referências | `c05ebab` |
| 12.3 | Estados principal/global, métricas por perfil, recursos e correlação | `a7dbb3c` |
| Correções | Exemplos de cenário e status, defaults de processamento | `da9f749`, `df413e8` |

Os nomes de schema e formas de representação acrescentados são **rascunhos de contrato**, sujeitos a validação e ajustes de compatibilidade. Endpoints reais do backend, controladores e frontend **não foram alterados**.

## 2. Verificações estáticas executadas com sucesso

A partir do arquivo no GitHub, foram realizadas verificações de texto estruturado (não equivalentes ao parse de YAML):
- **3** rotas documentadas, todas iniciadas em `/api/v1/`;
- **68** definições de schema reconhecidas, sem nomes duplicados;
- **0** referências internas de schema sem destino na lista de schemas;
- **0** âncoras YAML duplicadas ou aliases sem âncora;
- Discriminadores presentes para os **10** perfis v0.4 e `DATABASE_QUERY` legado;
- Discriminadores presentes para os **3** tipos de cenário;
- Estados `COMPLETED`, `FAILED`, `TIMED_OUT`, `COMPLETED_WITH_ERRORS` e campo conceitual `mainStatus`;
- Campos de limite para trace (50.000), mensagens WebSocket (100) e duração virtual total (3.600.000 ms) presentes;
- Exemplo legado de cenário atualizado para `kind: REQUEST_RESPONSE` e resultado com `mainStatus`.

**Atenção:** estes checks não provam sintaxe YAML válida, semântica OpenAPI 3.1, conformidade dos exemplos ou compatibilidade da geração de clientes.

## 3. Validações obrigatórias ainda não executadas

- [ ] Parse integral do YAML em ferramenta própria.
- [ ] Validação OpenAPI 3.1 (schemas, `oneOf`, `discriminator.mapping`, `$ref`, obrigatoriedade e defaults).
- [ ] Validação automática de **todos** os exemplos de request/response contra seus schemas.
- [ ] Geração de tipos com `openapi-typescript` e verificação TypeScript.
- [ ] Verificação de compatibilidade dos contratos v1 original, v2 e frontend atual.
- [ ] Testes de limites, defaults e código HTTP em DTOs Java e execução backend **após implementação**.
- [ ] Revisão da migração do contrato original v1 (payload direto de arquitetura) versus v2 (envelope `architecture`/`scenario`).
- [ ] Revisão do limite global de tempo frente ao limite legado de duração por propriedade.
- [ ] Validação das invariantes de assinatura e roteamento do Broker no backend.
- [ ] Atualização/remoção controlada de `openapi-v2.yaml` **após migração** e homologação, não agora.

## 4. Critério de saída da Etapa 12

Somente declarar o contrato **validado** após parse, validação OpenAPI, exemplos e typegen passarem e eventuais colisões serem resolvidas. Até lá, `docs/api/openapi.yaml` é **rascunho**, e nenhuma implementação deve tratá-lo como contrato homologado.

Branch: `docs/v0.4-communication-profiles-planning`. Não abrir PR até solicitação explícita.
