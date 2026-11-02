# Frontend v0.3 — plano de execução

Base develop f4e77f7, árvore local limpa antes do início. Todos os14cards completos consultados no Trello. Fontes: ADR-v0.3.md, OpenAPI3.1, backend real V3Controller/mapper/catalog/resultado, UI ReactFlow e temas existentes. Sem novas dependências relevantes. main e releases fora do escopo.

Ordem: FE01 contrato/projeto/versões → FE02 catálogo/canvas → FE03 propriedades/recursos/undo → FE04 endpoints/operations/relations → FE05 nove steps → FE06 cenário/controles → FE07 validações → FE08 simulação → FE09 replay público → FE10 estados → FE11 métricas → FE12 UX/acessibilidade → FE13 integração/regressão → FE14 homologação/documentação.

Cada marco: branch de develop, critérios/testes pertinentes, Em desenvolvimento → Em validação → PR/checks/revisão técnica → merge develop e Concluído. Não marcar capacidade inexistente: somente quatro endpoints síncronos; cancelar request UI não cancela o servidor. PUBLIC_SNAPSHOT disponível, FULL_RUNTIME/playback false, criticalPaths podem estar indisponíveis; tempos nulos não são zero.

FE01: módulo features/v3 independente com tipos derivados do schema, projeto sem dados de layout no wire request, navegação entre versões preservando projetos montados. Cliente existente reutilizado com distinção explícita de erro de rede/HTTP e AbortSignal. Estado atual dos cards será atualizado após execução real dos checks/PRs; demais cards ainda pendentes.

Verificação FE01: lint/typegen/v1v2v3/test/build executados; regressão de troca de versão mantém os dois rascunhos. Integração real v1/v3 contra jar Java25 executada antes da PR. O backend não foi alterado.

FE02: catálogo limitado aos seis tipos do TypeCatalogV3 real. ReactFlow com drag/drop, clique, seleção, movimento e alternativa acessível de lista. IDs independem do nome/posição, remoção bloqueada quando há referências. Nenhum estado de execução inferido antes de existir resultado. Testes do modelo e adaptador ReactFlow cobrem todos os tipos e rejeição de tipo desconhecido.
