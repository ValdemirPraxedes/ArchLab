package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
/** Internal journal/snapshot codec, without exposing a new HTTP route. */
public final class InternalEventCodecV3 {
    public SimulationEventV3 decode(JsonNode n){return switch(SimulationEventV3.Type.valueOf(text(n,"eventType"))){
            case EXECUTION_START_DUE -> {object(n,"eventType","rootOperationId");yield new SimulationEventV3.ExecutionStartDue(id(n,"rootOperationId"));}
            case ROOT_OPERATION_START_DUE -> {object(n,"eventType","rootOperationId","entryComponentId","entryOperationId");yield new SimulationEventV3.RootOperationStartDue(id(n,"rootOperationId"),id(n,"entryComponentId"),id(n,"entryOperationId"));}
            case TERMINATION_CHECK_DUE -> {object(n,"eventType","reason");yield new SimulationEventV3.TerminationCheckDue(id(n,"reason"));}
            case HORIZON_DUE -> {object(n,"eventType","horizonAtMs");yield new SimulationEventV3.HorizonDue(new DurationMsV3(number(n,"horizonAtMs")));}
            case ACTIVATION_START_DUE -> {object(n,"eventType","activationId");yield new SimulationEventV3.ActivationStartDue(id(n,"activationId"));}
            case STEP_START_DUE -> {object(n,"eventType","activationId","stepExecutionId","stepId","cursorId");yield new SimulationEventV3.StepStartDue(id(n,"activationId"),id(n,"stepExecutionId"),id(n,"stepId"),id(n,"cursorId"));}
            case STEP_COMPLETION_DUE -> {object(n,"eventType","stepExecutionId","completionToken");yield new SimulationEventV3.StepCompletionDue(id(n,"stepExecutionId"),id(n,"completionToken"));}
            case ACTIVATION_CONTINUATION_DUE -> {object(n,"eventType","activationId","cursorId","expectedCursorVersion","outcomeRef");yield new SimulationEventV3.ActivationContinuationDue(id(n,"activationId"),id(n,"cursorId"),number(n,"expectedCursorVersion"),id(n,"outcomeRef"));}
            case MESSAGE_SEND_DUE -> {object(n,"eventType","messageId","attemptId","pathId");yield new SimulationEventV3.MessageSendDue(id(n,"messageId"),id(n,"attemptId"),id(n,"pathId"));}
            case MESSAGE_ARRIVAL_DUE -> {object(n,"eventType","messageId","attemptId","destinationEndpointId","pathSegmentId");yield new SimulationEventV3.MessageArrivalDue(id(n,"messageId"),id(n,"attemptId"),id(n,"destinationEndpointId"),id(n,"pathSegmentId"));}
            case MESSAGE_DELIVERY_FAILURE_DUE -> {object(n,"eventType","messageId","attemptId","failureCode","pathSegmentId");yield new SimulationEventV3.MessageDeliveryFailureDue(id(n,"messageId"),id(n,"attemptId"),id(n,"failureCode"),id(n,"pathSegmentId"));}
            case RESPONSE_SEND_DUE -> {object(n,"eventType","interactionId","attemptId","responseMessageId");yield new SimulationEventV3.ResponseSendDue(id(n,"interactionId"),id(n,"attemptId"),id(n,"responseMessageId"));}
            case RESPONSE_ARRIVAL_DUE -> {object(n,"eventType","interactionId","attemptId","responseMessageId","callerActivationId");yield new SimulationEventV3.ResponseArrivalDue(id(n,"interactionId"),id(n,"attemptId"),id(n,"responseMessageId"),id(n,"callerActivationId"));}
            case INTERACTION_DEADLINE_DUE -> {object(n,"eventType","interactionId","timerId","deadlineAtMs");yield new SimulationEventV3.InteractionDeadlineDue(id(n,"interactionId"),id(n,"timerId"),new DurationMsV3(number(n,"deadlineAtMs")));}
            case ATTEMPT_DEADLINE_DUE -> {object(n,"eventType","interactionId","attemptId","timerId","deadlineAtMs");yield new SimulationEventV3.AttemptDeadlineDue(id(n,"interactionId"),id(n,"attemptId"),id(n,"timerId"),new DurationMsV3(number(n,"deadlineAtMs")));}
            case RETRY_DUE -> {object(n,"eventType","interactionId","previousAttemptId","retryOrdinal","policyId");yield new SimulationEventV3.RetryDue(id(n,"interactionId"),id(n,"previousAttemptId"),number(n,"retryOrdinal"),id(n,"policyId"));}
            case CANCELLATION_DELIVERY_DUE -> {object(n,"eventType","cancellationRequestId","target","sourceInteractionId");yield new SimulationEventV3.CancellationDeliveryDue(id(n,"cancellationRequestId"),reference(required(n,"target")),id(n,"sourceInteractionId"));}
            case BRANCH_START_DUE -> {object(n,"eventType","parallelGroupId","branchExecutionId","entryStepId");yield new SimulationEventV3.BranchStartDue(id(n,"parallelGroupId"),id(n,"branchExecutionId"),id(n,"entryStepId"));}
            case BRANCH_TERMINATION_DUE -> {object(n,"eventType","parallelGroupId","branchExecutionId","branchResultRef");yield new SimulationEventV3.BranchTerminationDue(id(n,"parallelGroupId"),id(n,"branchExecutionId"),id(n,"branchResultRef"));}
            case JOIN_EVALUATION_DUE -> {object(n,"eventType","parallelGroupId","joinStepId","expectedGroupVersion");yield new SimulationEventV3.JoinEvaluationDue(id(n,"parallelGroupId"),id(n,"joinStepId"),number(n,"expectedGroupVersion"));}
            case BROKER_ACCEPTANCE_DUE -> {object(n,"eventType","publicationId","brokerId","messageId");yield new SimulationEventV3.BrokerAcceptanceDue(id(n,"publicationId"),id(n,"brokerId"),id(n,"messageId"));}
            case QUEUE_ENQUEUE_DUE -> {object(n,"eventType","queueId","messageId","publicationId");yield new SimulationEventV3.QueueEnqueueDue(id(n,"queueId"),id(n,"messageId"),id(n,"publicationId"));}
            case QUEUE_DELIVERY_DUE -> {object(n,"eventType","queueEntryId","deliveryAttemptId","consumerEndpointId");yield new SimulationEventV3.QueueDeliveryDue(id(n,"queueEntryId"),id(n,"deliveryAttemptId"),id(n,"consumerEndpointId"));}
            case CONSUMER_ACK_ARRIVAL_DUE -> {object(n,"eventType","queueEntryId","deliveryAttemptId","acknowledgementId");yield new SimulationEventV3.ConsumerAckArrivalDue(id(n,"queueEntryId"),id(n,"deliveryAttemptId"),id(n,"acknowledgementId"));}
            case MESSAGE_TTL_EXPIRATION_DUE -> {object(n,"eventType","queueEntryId","timerId","expiresAtMs");yield new SimulationEventV3.MessageTtlExpirationDue(id(n,"queueEntryId"),id(n,"timerId"),new DurationMsV3(number(n,"expiresAtMs")));}
            case REDELIVERY_DUE -> {object(n,"eventType","queueEntryId","previousDeliveryAttemptId","nextAttemptNumber");yield new SimulationEventV3.RedeliveryDue(id(n,"queueEntryId"),id(n,"previousDeliveryAttemptId"),number(n,"nextAttemptNumber"));}
            case RESOURCE_ACQUISITION_DUE -> {object(n,"eventType","resourceId","claimId","owner");yield new SimulationEventV3.ResourceAcquisitionDue(id(n,"resourceId"),id(n,"claimId"),reference(required(n,"owner")));}
            case RESOURCE_RELEASE_DUE -> {object(n,"eventType","resourceId","leaseId");yield new SimulationEventV3.ResourceReleaseDue(id(n,"resourceId"),id(n,"leaseId"));}
            case RESOURCE_STATE_CHANGE_DUE -> {object(n,"eventType","resourceId","changeId","newStateRef");yield new SimulationEventV3.ResourceStateChangeDue(id(n,"resourceId"),id(n,"changeId"),id(n,"newStateRef"));}
            case RESOURCE_WAIT_TIMEOUT_DUE -> {object(n,"eventType","resourceId","claimId","timerId");yield new SimulationEventV3.ResourceWaitTimeoutDue(id(n,"resourceId"),id(n,"claimId"),id(n,"timerId"));}
            case POLICY_TRANSITION_DUE -> {object(n,"eventType","policyInstanceId","transitionId","targetState");yield new SimulationEventV3.PolicyTransitionDue(id(n,"policyInstanceId"),id(n,"transitionId"),id(n,"targetState"));}
            case RESOURCE_WAKEUP_DUE -> {object(n,"eventType","resourceId","claimId","resourceVersion");yield new SimulationEventV3.ResourceWakeupDue(id(n,"resourceId"),id(n,"claimId"),number(n,"resourceVersion"));}
        };}
    public Map<String,Object> encode(SimulationEventV3 event){var result=new LinkedHashMap<String,Object>();result.put("eventType",event.eventType().name());switch(event){
            case SimulationEventV3.ExecutionStartDue e -> {result.put("rootOperationId",e.rootOperationId().value());}
            case SimulationEventV3.RootOperationStartDue e -> {result.put("rootOperationId",e.rootOperationId().value());result.put("entryComponentId",e.entryComponentId().value());result.put("entryOperationId",e.entryOperationId().value());}
            case SimulationEventV3.TerminationCheckDue e -> {result.put("reason",e.reason().value());}
            case SimulationEventV3.HorizonDue e -> {result.put("horizonAtMs",e.horizonAtMs().value());}
            case SimulationEventV3.ActivationStartDue e -> {result.put("activationId",e.activationId().value());}
            case SimulationEventV3.StepStartDue e -> {result.put("activationId",e.activationId().value());result.put("stepExecutionId",e.stepExecutionId().value());result.put("stepId",e.stepId().value());result.put("cursorId",e.cursorId().value());}
            case SimulationEventV3.StepCompletionDue e -> {result.put("stepExecutionId",e.stepExecutionId().value());result.put("completionToken",e.completionToken().value());}
            case SimulationEventV3.ActivationContinuationDue e -> {result.put("activationId",e.activationId().value());result.put("cursorId",e.cursorId().value());result.put("expectedCursorVersion",e.expectedCursorVersion());result.put("outcomeRef",e.outcomeRef().value());}
            case SimulationEventV3.MessageSendDue e -> {result.put("messageId",e.messageId().value());result.put("attemptId",e.attemptId().value());result.put("pathId",e.pathId().value());}
            case SimulationEventV3.MessageArrivalDue e -> {result.put("messageId",e.messageId().value());result.put("attemptId",e.attemptId().value());result.put("destinationEndpointId",e.destinationEndpointId().value());result.put("pathSegmentId",e.pathSegmentId().value());}
            case SimulationEventV3.MessageDeliveryFailureDue e -> {result.put("messageId",e.messageId().value());result.put("attemptId",e.attemptId().value());result.put("failureCode",e.failureCode().value());result.put("pathSegmentId",e.pathSegmentId().value());}
            case SimulationEventV3.ResponseSendDue e -> {result.put("interactionId",e.interactionId().value());result.put("attemptId",e.attemptId().value());result.put("responseMessageId",e.responseMessageId().value());}
            case SimulationEventV3.ResponseArrivalDue e -> {result.put("interactionId",e.interactionId().value());result.put("attemptId",e.attemptId().value());result.put("responseMessageId",e.responseMessageId().value());result.put("callerActivationId",e.callerActivationId().value());}
            case SimulationEventV3.InteractionDeadlineDue e -> {result.put("interactionId",e.interactionId().value());result.put("timerId",e.timerId().value());result.put("deadlineAtMs",e.deadlineAtMs().value());}
            case SimulationEventV3.AttemptDeadlineDue e -> {result.put("interactionId",e.interactionId().value());result.put("attemptId",e.attemptId().value());result.put("timerId",e.timerId().value());result.put("deadlineAtMs",e.deadlineAtMs().value());}
            case SimulationEventV3.RetryDue e -> {result.put("interactionId",e.interactionId().value());result.put("previousAttemptId",e.previousAttemptId().value());result.put("retryOrdinal",e.retryOrdinal());result.put("policyId",e.policyId().value());}
            case SimulationEventV3.CancellationDeliveryDue e -> {result.put("cancellationRequestId",e.cancellationRequestId().value());result.put("target",Map.of("kind",e.target().kind(),"id",e.target().id().value()));result.put("sourceInteractionId",e.sourceInteractionId().value());}
            case SimulationEventV3.BranchStartDue e -> {result.put("parallelGroupId",e.parallelGroupId().value());result.put("branchExecutionId",e.branchExecutionId().value());result.put("entryStepId",e.entryStepId().value());}
            case SimulationEventV3.BranchTerminationDue e -> {result.put("parallelGroupId",e.parallelGroupId().value());result.put("branchExecutionId",e.branchExecutionId().value());result.put("branchResultRef",e.branchResultRef().value());}
            case SimulationEventV3.JoinEvaluationDue e -> {result.put("parallelGroupId",e.parallelGroupId().value());result.put("joinStepId",e.joinStepId().value());result.put("expectedGroupVersion",e.expectedGroupVersion());}
            case SimulationEventV3.BrokerAcceptanceDue e -> {result.put("publicationId",e.publicationId().value());result.put("brokerId",e.brokerId().value());result.put("messageId",e.messageId().value());}
            case SimulationEventV3.QueueEnqueueDue e -> {result.put("queueId",e.queueId().value());result.put("messageId",e.messageId().value());result.put("publicationId",e.publicationId().value());}
            case SimulationEventV3.QueueDeliveryDue e -> {result.put("queueEntryId",e.queueEntryId().value());result.put("deliveryAttemptId",e.deliveryAttemptId().value());result.put("consumerEndpointId",e.consumerEndpointId().value());}
            case SimulationEventV3.ConsumerAckArrivalDue e -> {result.put("queueEntryId",e.queueEntryId().value());result.put("deliveryAttemptId",e.deliveryAttemptId().value());result.put("acknowledgementId",e.acknowledgementId().value());}
            case SimulationEventV3.MessageTtlExpirationDue e -> {result.put("queueEntryId",e.queueEntryId().value());result.put("timerId",e.timerId().value());result.put("expiresAtMs",e.expiresAtMs().value());}
            case SimulationEventV3.RedeliveryDue e -> {result.put("queueEntryId",e.queueEntryId().value());result.put("previousDeliveryAttemptId",e.previousDeliveryAttemptId().value());result.put("nextAttemptNumber",e.nextAttemptNumber());}
            case SimulationEventV3.ResourceAcquisitionDue e -> {result.put("resourceId",e.resourceId().value());result.put("claimId",e.claimId().value());result.put("owner",Map.of("kind",e.owner().kind(),"id",e.owner().id().value()));}
            case SimulationEventV3.ResourceReleaseDue e -> {result.put("resourceId",e.resourceId().value());result.put("leaseId",e.leaseId().value());}
            case SimulationEventV3.ResourceStateChangeDue e -> {result.put("resourceId",e.resourceId().value());result.put("changeId",e.changeId().value());result.put("newStateRef",e.newStateRef().value());}
            case SimulationEventV3.ResourceWaitTimeoutDue e -> {result.put("resourceId",e.resourceId().value());result.put("claimId",e.claimId().value());result.put("timerId",e.timerId().value());}
            case SimulationEventV3.PolicyTransitionDue e -> {result.put("policyInstanceId",e.policyInstanceId().value());result.put("transitionId",e.transitionId().value());result.put("targetState",e.targetState().value());}
            case SimulationEventV3.ResourceWakeupDue e -> {result.put("resourceId",e.resourceId().value());result.put("claimId",e.claimId().value());result.put("resourceVersion",e.resourceVersion());}
        }return Collections.unmodifiableMap(result);}
    private RuntimeEntityRefV3 reference(JsonNode n){object(n,"kind","id");return new RuntimeEntityRefV3(text(n,"kind"),id(n,"id"));}
    private static void object(JsonNode n,String... keys){if(n==null || !n.isObject())throw new IllegalArgumentException("Expected object");var allowed=Set.of(keys);for(var key:n.propertyNames())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown event field: "+key);}
    private static JsonNode required(JsonNode n,String key){if(n==null || !n.isObject())throw new IllegalArgumentException("Expected object");var v=n.get(key);if(v==null || v.isNull())throw new IllegalArgumentException("Missing event field: "+key);return v;}
    private static String text(JsonNode n,String key){var v=required(n,key);if(!v.isTextual())throw new IllegalArgumentException("Expected string");return v.asText();}
    private static long number(JsonNode n,String key){var v=required(n,key);if(!v.isIntegralNumber() || !v.canConvertToLong())throw new IllegalArgumentException("Expected int64");return v.longValue();}
    private static IdentifierV3 id(JsonNode n,String key){return new IdentifierV3(text(n,key));}
}
