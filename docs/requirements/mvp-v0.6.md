# ArchLab v0.6 — Data Architecture & Processing

> **Status:** proposta de planejamento para revisão e aprovação. Não autoriza implementação nem merge.
> **Base:** v0.5 em `develop`; manter um único motor de eventos discretos, clock virtual e `/api/v1`.
> **Decisão de produto:** o componente genérico `DATABASE` será removido da paleta e dos novos documentos; tipos específicos o substituirão. Arquivos históricos terão migração explícita.
> **Objetivo:** modelar tipos de armazenamento, operações de dados e pipelines ETL com efeitos observáveis e reproduzíveis sobre latência, filas, throughput e gargalos.

## 1. Escopo e princípios

- Modelar **características de categorias de banco**, não executar PostgreSQL, MongoDB ou SQL reais.
- Distinguir **componente** (papel e capacidades), **Action** (operação executada), **conexão** (perfil e latência), **estado virtual por execução** (registros, índices, transações) e **resultado** (tempo, volume, status e correlação).
- Reaproveitar `ArchitectureV2`, Behaviors/Actions e `UnifiedSimulationV41`, sem engine próprio por banco.
- Reutilizar `CACHE`, `OBJECT_STORAGE` e `SEARCH_ENGINE` da v0.5, sem recriá-los com nomes diferentes.
- Todos os resultados são estimativas baseadas em premissas, não previsões de comportamento de um SGBD real.

## 2. Catálogo de componentes

| ID | Tipo canônico proposto | Comportamento diferencial mínimo | Prioridade |
| --- | --- | --- | --- |
| DB6-01 | `RELATIONAL_DATABASE` | Operações por linhas, índice lógico, concorrência e transação simplificada | P0 |
| DB6-02 | `DOCUMENT_DATABASE` | Documentos com campos/tags e consultas por atributos restritos | P0 |
| DB6-03 | `KEY_VALUE_STORE` | GET/PUT/DELETE por chave, latências de acesso e capacidade | P0 |
| DB6-04 | `COLUMN_FAMILY_DATABASE` | Chave de partição e consulta por partição com custo diferenciado | P1 |
| DB6-05 | `GRAPH_DATABASE` | Nós/arestas e travessias com profundidade finita | P2 |
| DB6-06 | `TIME_SERIES_DATABASE` | Escrita temporal e consulta por intervalo/agregação com janela limitada | P2 |
| DB6-07 | `DATA_WAREHOUSE` | Cargas em lote e consulta analítica com custo por volume | P1 |
| DB6-08 | `DATA_LAKE` | Conjuntos de dados e operações de ingestão/varredura lógica | P2 |

Tipos adicionais de processamento:

| ID | Tipo proposto | Papel mínimo | Prioridade |
| --- | --- | --- | --- |
| DP6-01 | `ETL_PIPELINE` | Orquestrar EXTRACT → TRANSFORM → LOAD como Actions dependentes | P0 |
| DP6-02 | `DATA_TRANSFORMER` | Limpeza, mapeamento e transformação descritiva de lotes | P0 |
| DP6-03 | `BATCH_PROCESSOR` | Processar registros em lotes limitados, observando throughput | P1 |
| DP6-04 | `STREAM_PROCESSOR` | Consumir eventos finitos e produzir saídas por janela | P2 |
| DP6-05 | `DATA_INGESTION` | Receber volumes lógicos de diferentes fontes | P1 |
| DP6-06 | `DATA_QUALITY_VALIDATOR` | Aplicar regras declarativas de qualidade e produzir aceitos/rejeitados | P1 |
| DP6-07 | `CDC_CONNECTOR` | Emitir alterações lógicas ordenadas, com limites finitos | P2 |

**Priorização:** os tipos são catálogo candidato para v0.6; P0 representa o primeiro incremento recomendado, e P1/P2 dependem de revisão de viabilidade. Inclusão do catálogo não implica implementação integral simultânea.

### 2.1 Remoção do DATABASE genérico

- `DATABASE` não aparece na paleta nem é permitido na criação/serialização de novas arquiteturas v0.6.
- Importações históricas com `type: "DATABASE"` passam por adaptador versionado, com **mapeamento padrão sugerido** para `RELATIONAL_DATABASE` apenas quando a semântica das operações é compatível; caso ambíguo, solicitar escolha explícita ao usuário.
- Preservar ID, conexões, propriedades compatíveis, Actions e correlação. Propriedades incompatíveis exigem aviso e escolha; nunca descartar silenciosamente.
- Validar e comparar cenário anterior e migrado por regressão; oferecer prévia das alterações antes de aplicar. Falha de migração não modifica o canvas.
- A API pública continua `/api/v1`. O formato legível de entrada legada deve ser isolado do contrato canônico de saída.
- Não remover o suporte legado antes de implementar e aprovar seu adaptador e suíte de testes.

## 3. Actions propostas

| Grupo | Actions candidatas | Semântica mínima |
| --- | --- | --- |
| CRUD | `DB_SELECT`, `DB_INSERT`, `DB_UPDATE`, `DB_DELETE` | Volume lido/alterado, resultado e custo temporal |
| Chave/Documento | `DB_GET`, `DB_PUT`, `DB_FIND` | Operações adaptadas ao tipo e filtro declarativo limitado |
| Transações | `DB_BEGIN_TRANSACTION`, `DB_COMMIT`, `DB_ROLLBACK` | Contexto transacional finito, confirmação ou reversão |
| Lotes | `DB_BULK_INSERT`, `DB_AGGREGATE` | Quantidade e tempo por lote/agregação |
| Índices | `DB_CREATE_INDEX`, `DB_QUERY_INDEX`, `DB_EXPLAIN` | Índice virtual, custo e plano estimado explicável |
| Concorrência | `DB_LOCK`, `DB_UNLOCK` | Contenção simplificada com timeout e liberação terminal |
| ETL | `ETL_EXTRACT`, `ETL_TRANSFORM`, `ETL_LOAD` | Leitura, transformação e escrita por lotes correlacionados |
| Qualidade | `DATA_VALIDATE` | Contagem de aceitos/rejeitados por regras declarativas |

**P0 de Actions:** CRUD, GET/PUT/FIND conforme tipo, BULK_INSERT e as três Actions de ETL. Índices, transações, locks e agregações avançadas são P1/P2. Proibir SQL arbitrário, scripts livres e queries em bancos reais.

## 4. Propriedades observáveis

Propriedades básicas: `recordCount`, `averageRecordBytes`, `readLatencyMs`, `writeLatencyMs`, `maxConcurrentOperations`, `connectionPoolSize`, `queueCapacity`, `failurePolicy`, `initialDataProfile`, `maxVirtualRecords` e `maxVirtualBytes` (nomes finais sujeitos ao contrato).

Propriedades específicas só aparecem quando fazem sentido: `indexDefinitions` para relacional/documento, `partitionKey` para column-family, `batchSize` e `transformCostPerRecord` para ETL, janela temporal para séries e stream.

Resultados: contagem lida/escrita/alterada/rejeitada, bytes lógicos, latência de fila e execução, ocupação, índice utilizado, transação/lock quando aplicável, status, timestamp virtual e identificadores de causa.

## 5. Requisitos funcionais

| ID | Requisito | Aceite |
| --- | --- | --- |
| RF-6-001 | Paleta tipada | Nenhum `DATABASE` genérico criável; tipos específicos têm ícones, edição e validação por capacidades |
| RF-6-002 | Operações persistentes durante a execução | Writes alteram estado virtual isolado por simulação e leituras subsequentes observam estado confirmado |
| RF-6-003 | Semântica por tipo | Mesmo nome de Action não mascara diferenças de chave, documento, tabela, partição e processamento |
| RF-6-004 | ETL causal | Extract → Transform → Load com volume, erro e dependências explícitas |
| RF-6-005 | Carga e contenção | Capacidade, filas, pool e falhas causam tempos/estados reproduzíveis |
| RF-6-006 | Limites | Sem execução infinita; teto de registros, bytes, eventos, lote, profundidade e tempo virtual |
| RF-6-007 | Correlação e métricas | Timeline mostra cada operação e transformação com início/fim, componente, resultado e causa |
| RF-6-008 | Compatibilidade | Migração legada `DATABASE` explícita, reversível antes de aplicar e testada |
| RF-6-009 | Contrato | Atualizar OpenAPI e geração TS após aprovação de ADR; preservar `/api/v1` |
| RF-6-010 | UX | Formular propriedades e Actions conforme capacidade do tipo; erros acionáveis |
| RF-6-011 | Determinismo | Mesmo cenário e seed produzem os mesmos estados e resultados |
| RF-6-012 | Sem I/O real | Nenhum acesso a banco, arquivo ou serviço externo durante simulação |

## 6. Cenários de aceitação

- **CT-6-01:** Client → Service → Relational DB; INSERT seguido de SELECT encontra o registro, com custo e rastreabilidade.
- **CT-6-02:** Client → Service → Document DB; PUT e FIND por atributo permitido retornam o conjunto esperado.
- **CT-6-03:** Service → Key-Value Store; GET inexistente, PUT e novo GET exibem miss e hit lógico.
- **CT-6-04:** Service → banco com duas operações simultâneas e pool limitado; uma operação espera na fila.
- **CT-6-05:** Scheduler → ETL Pipeline → fonte Document DB → Transformer → Relational DB; batch finito, contadores conservados.
- **CT-6-06:** Falha em TRANSFORM interrompe ou desvia LOAD conforme política explícita; erros ficam na timeline.
- **CT-6-07:** Importar cenário legado com `DATABASE`, mostrar prévia, converter para `RELATIONAL_DATABASE` e preservar IDs/conexões/semântica; saída não contém `DATABASE`.
- **CT-6-08:** Arquivo legado ambíguo falha com diagnóstico ou aguarda escolha; canvas anterior não se altera.
- **CT-6-09:** Mesmo input/seed gera mesmo trace e métricas; execução seguinte começa com estado virtual limpo.
- **CT-6-10:** Cenário acima de limite de lote/registros termina com erro controlado, sem travar interface.

## 7. Fases, dependências e limites

1. Aprovar catálogo e modelo de migração (requisitos + ADR).
2. Aprovar matriz de capacidades/Action e contrato OpenAPI.
3. Implementar backend P0 com estado virtual, limites, validador e regressão.
4. Implementar frontend P0 com paleta, migração prévia, editor contextual e playback.
5. Integrar testes de ponta a ponta e cenários de carga determinísticos.
6. Planejar P1/P2 somente após evidência de correção dos casos P0.

**Fora de escopo:** bancos ou SQL reais, query planner real, replicação/consistência distribuída completas, CDC real, processamento infinito, conectores externos, armazenamento persistente de dados simulados, execução de código arbitrário, promessa de benchmark fidedigno de provedores.

## 8. Identidade visual e ícones

- O catálogo de ícones Lucide de todos os oito tipos de dados e sete componentes de processamento está definido em [UI-V6-001](../architecture/UI-V6-001.md).
- O mesmo símbolo deve aparecer na paleta e no canvas, com os padrões de acessibilidade e tamanho da v0.5.
- `DATABASE` mantém o ícone antigo somente durante prévia de migração; não é uma opção criável.
- O frontend deve validar exports da versão Lucide instalada e cobrir o mapa de ícones por testes.

## 9. Gate de aprovação

Antes de implementar: aprovar inventário P0, semântica de operações, consistência entre tipos, política de migração `DATABASE`, limites numéricos e contrato de resultados. Nenhum merge automático.
