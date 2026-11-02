# BE-V3-10 — HTTP e admissão semântica

POST /api/v3/architectures/validate recebe ArchitectureDefinitionV3. POST /api/v3/execution-plans/validate recebe architecture e executionPlan. POST /api/v3/scenarios/validate e /api/v3/simulations recebem SimulationRequestV3. As três validações retornam 200 com valid true/false; simulação semanticamente inválida retorna 422 ProblemV3 e violations. Falha simulada de uma execução válida retorna 200, principal FAILED, sem converter a falha do domínio em erro HTTP.

O mapper estrito recusa campos desconhecidos, nulls não previstos, tipos incorretos e enumerações desconhecidas com 400. A aplicação existente fornece ProblemV3 para 400/415; o filtro isolado em /api/v3 limita 1 MiB com e sem Content-Length e retorna 413. Os controladores e filtros v1/v2 permanecem separados.

ScenarioValidatorV3 agrega validação de arquitetura, todos os planos e estado inicial, verifica o Client iniciador, operação de entrada, vínculo da conexão, consumidores de fila e roteamento dos tópicos. Não executa o motor nas validações. O catálogo inicial exige consumidor inbound/bidirectional com operação em MESSAGE_QUEUE; TOPIC possui ao menos uma fila distinta. Políticas nativas aceitam ENABLED/DISABLED. Concurrency/maxAttempts/maxDeliveryAttempts são positivos até 100000; propriedades numéricas e horizonte até 10^12; mensagens iniciais de uma fila até 1000. Esses limites de admissão do catálogo protegem soma de tempo/execução e são erros semânticos, preservando o schema estrutural extensível.

V3PublicContractTest usa servidor Spring Boot real, HttpClient e core de produção, sem mocks: sete testes cobrem os quatro endpoints, true/false nas validações, 422, falha simulada200, determinismo, CE-REF-01 120/210, horizonte150, 400/413/415 e chunked payload. Cinco testes de admissão no core cobrem rotas, tópicos, consumidores, política e limites. Onze respostas HTTP reais foram validadas por Ajv2020 contra os schemas do YAML (SimulationResponseV3, ValidationResponseV3 e ProblemV3).

Java 25 Maven clean verify: 212 core +105 API =317 testes, zero falhas/erros/skip, incluindo todas as suítes v1/v2.
