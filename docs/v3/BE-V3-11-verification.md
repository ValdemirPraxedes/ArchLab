# BE-V3-11 — matriz, golden e integração real

Execução Java 25: Maven clean verify, 215 testes core +112 API =327, zero falhas/erros/skip. A validação independente Ajv2020 verifica 51 artefatos/fixtures: request CE-REF-01, três resultados do projector, quinze respostas HTTP e 32 payloads de eventos. Backend CI instala validadores isolados e executa tools/validate-v3-results.cjs após Maven. O script falha quando os artefatos reais estão ausentes ou não atendem ao OpenAPI.

ExecutionMatrixV3Test mede cobertura nos journals de produção de 14 execuções. Exige igualdade com o conjunto exato dos 32 eventos e dos nove tipos de step efetivamente executados; não infere cobertura de um switch ou registro de handlers. Em todas as execuções comprova retomada a partir de checkpoint intermediário == execução original, replay interno == snapshot integral e replay público == snapshot público final.

| Família | Eventos efetivamente despachados / cenários |
|---|---|
| Ciclo | ExecutionStartDue, RootOperationStartDue, TerminationCheckDue, HorizonDue — completo e horizonte150 |
| Execução | ActivationStartDue, StepStartDue, StepCompletionDue, ActivationContinuationDue — nove steps, Condition verdadeiro, Fail de branch e dependência |
| Transporte | MessageSendDue, MessageArrivalDue, MessageDeliveryFailureDue, ResponseSendDue, ResponseArrivalDue — RPC e falha ambiental registrada na agenda inicial |
| Políticas | InteractionDeadlineDue, AttemptDeadlineDue, RetryDue, PolicyTransitionDue — deadline principal, timeout de chamada, tentativas e política desabilitada |
| Concorrência | BranchStartDue, BranchTerminationDue, JoinEvaluationDue, CancellationDeliveryDue — ALL com falha e ANY cancelando restante |
| Mensageria | BrokerAcceptanceDue, QueueEnqueueDue, QueueDeliveryDue, ConsumerAckArrivalDue, MessageTtlExpirationDue, RedeliveryDue — seis componentes, ACK timeout e TTL |
| Recursos | ResourceAcquisitionDue, ResourceReleaseDue, ResourceStateChangeDue, ResourceWaitTimeoutDue, ResourceWakeupDue — pool ocupado, indisponível e capacidade zero |

Complementam a matriz os testes já existentes: quatro políticas JOIN, quorum/timeout/nested FORK, fanout, FIFO, rejeição, expiração versus ACK no instante exato, retry e resposta tardia, eventos duplicados, cancelamento e limites. V3PublicContractTest usa HTTP/servidor/core reais sem mocks, incluindo horizontes0/10/50/119/150 e principal IN_PROGRESS sem tempos terminais inventados. Decoder testa os 32 payloads com campos ausentes/desconhecidos e tipos inválidos.

GoldenResultsV3Test compara três digests (snapshot/trace/resultado completo), contagens e durações com docs/examples/v3/golden-results.json versionado. Os testes nunca atualizam os esperados automaticamente. O resultado canônico é comparado no formato JSON de transporte, eliminando diferenças irrelevantes entre IntNode e LongNode sem alterar asserções de valor ou hashes.

A revisão revelou duas correções incorporadas com regressões: preservar resultProfileId/errorProfileId em StepResult e acordar mensagens READY quando um recurso MESSAGE_QUEUE recupera disponibilidade. O cenário de recuperação usa duas mensagens iniciais, interrupção aos150 e recuperação aos200, resposta principal120 e drenagem global290.

Não regressão v1/v2: V1PublicContractRegressionTest mantém260ms/sete atividades/espera170ms; TimelineProjectorTest e RuntimeStatesTest preservam fixtures legadas. V2PublicContractTest verifica HTTP real, metadados de protocolos e overrides RF-044 (inclusive inválidos, parcial/limites), além das suítes Graph/Scenario/NestedCalls/Representation. Nenhuma implementação ou contrato v1/v2 foi alterado nesta entrega.

Artefatos locais após verify: backend/archlab-api/target/v3-results e target/v3-http; relatórios JUnit nos target/surefire-reports. Logs completos da execução em C:/Users/valde/Documents/ChatGPT/ArchLab/logs/be-v3-11-verify.log e be-v3-11-contract.log. CI fornece execução independente Linux/Temurin25 e validação contratual.
