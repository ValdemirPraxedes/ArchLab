package br.com.archlab.simulation.core.v3;
import java.util.Objects;
/** Closed payload catalog derived from OpenAPI v3; scheduler metadata remains in its envelope. */
public sealed interface SimulationEventV3 {
    enum Type { EXECUTION_START_DUE,ROOT_OPERATION_START_DUE,TERMINATION_CHECK_DUE,HORIZON_DUE,ACTIVATION_START_DUE,STEP_START_DUE,STEP_COMPLETION_DUE,ACTIVATION_CONTINUATION_DUE,MESSAGE_SEND_DUE,MESSAGE_ARRIVAL_DUE,MESSAGE_DELIVERY_FAILURE_DUE,RESPONSE_SEND_DUE,RESPONSE_ARRIVAL_DUE,INTERACTION_DEADLINE_DUE,ATTEMPT_DEADLINE_DUE,RETRY_DUE,CANCELLATION_DELIVERY_DUE,BRANCH_START_DUE,BRANCH_TERMINATION_DUE,JOIN_EVALUATION_DUE,BROKER_ACCEPTANCE_DUE,QUEUE_ENQUEUE_DUE,QUEUE_DELIVERY_DUE,CONSUMER_ACK_ARRIVAL_DUE,MESSAGE_TTL_EXPIRATION_DUE,REDELIVERY_DUE,RESOURCE_ACQUISITION_DUE,RESOURCE_RELEASE_DUE,RESOURCE_STATE_CHANGE_DUE,RESOURCE_WAIT_TIMEOUT_DUE,POLICY_TRANSITION_DUE,RESOURCE_WAKEUP_DUE }
    Type eventType();
    record ExecutionStartDue(IdentifierV3 rootOperationId) implements SimulationEventV3 {
        public ExecutionStartDue { Objects.requireNonNull(rootOperationId,"rootOperationId"); }
        public Type eventType(){return Type.EXECUTION_START_DUE;}
    }
    record RootOperationStartDue(IdentifierV3 rootOperationId,IdentifierV3 entryComponentId,IdentifierV3 entryOperationId) implements SimulationEventV3 {
        public RootOperationStartDue { Objects.requireNonNull(rootOperationId,"rootOperationId"); Objects.requireNonNull(entryComponentId,"entryComponentId"); Objects.requireNonNull(entryOperationId,"entryOperationId"); }
        public Type eventType(){return Type.ROOT_OPERATION_START_DUE;}
    }
    record TerminationCheckDue(IdentifierV3 reason) implements SimulationEventV3 {
        public TerminationCheckDue { Objects.requireNonNull(reason,"reason"); }
        public Type eventType(){return Type.TERMINATION_CHECK_DUE;}
    }
    record HorizonDue(DurationMsV3 horizonAtMs) implements SimulationEventV3 {
        public HorizonDue { Objects.requireNonNull(horizonAtMs,"horizonAtMs"); }
        public Type eventType(){return Type.HORIZON_DUE;}
    }
    record ActivationStartDue(IdentifierV3 activationId) implements SimulationEventV3 {
        public ActivationStartDue { Objects.requireNonNull(activationId,"activationId"); }
        public Type eventType(){return Type.ACTIVATION_START_DUE;}
    }
    record StepStartDue(IdentifierV3 activationId,IdentifierV3 stepExecutionId,IdentifierV3 stepId,IdentifierV3 cursorId) implements SimulationEventV3 {
        public StepStartDue { Objects.requireNonNull(activationId,"activationId"); Objects.requireNonNull(stepExecutionId,"stepExecutionId"); Objects.requireNonNull(stepId,"stepId"); Objects.requireNonNull(cursorId,"cursorId"); }
        public Type eventType(){return Type.STEP_START_DUE;}
    }
    record StepCompletionDue(IdentifierV3 stepExecutionId,IdentifierV3 completionToken) implements SimulationEventV3 {
        public StepCompletionDue { Objects.requireNonNull(stepExecutionId,"stepExecutionId"); Objects.requireNonNull(completionToken,"completionToken"); }
        public Type eventType(){return Type.STEP_COMPLETION_DUE;}
    }
    record ActivationContinuationDue(IdentifierV3 activationId,IdentifierV3 cursorId,long expectedCursorVersion,IdentifierV3 outcomeRef) implements SimulationEventV3 {
        public ActivationContinuationDue { Objects.requireNonNull(activationId,"activationId"); Objects.requireNonNull(cursorId,"cursorId"); if(expectedCursorVersion<0)throw new IllegalArgumentException("Invalid expectedCursorVersion"); Objects.requireNonNull(outcomeRef,"outcomeRef"); }
        public Type eventType(){return Type.ACTIVATION_CONTINUATION_DUE;}
    }
    record MessageSendDue(IdentifierV3 messageId,IdentifierV3 attemptId,IdentifierV3 pathId) implements SimulationEventV3 {
        public MessageSendDue { Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(pathId,"pathId"); }
        public Type eventType(){return Type.MESSAGE_SEND_DUE;}
    }
    record MessageArrivalDue(IdentifierV3 messageId,IdentifierV3 attemptId,IdentifierV3 destinationEndpointId,IdentifierV3 pathSegmentId) implements SimulationEventV3 {
        public MessageArrivalDue { Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(destinationEndpointId,"destinationEndpointId"); Objects.requireNonNull(pathSegmentId,"pathSegmentId"); }
        public Type eventType(){return Type.MESSAGE_ARRIVAL_DUE;}
    }
    record MessageDeliveryFailureDue(IdentifierV3 messageId,IdentifierV3 attemptId,IdentifierV3 failureCode,IdentifierV3 pathSegmentId) implements SimulationEventV3 {
        public MessageDeliveryFailureDue { Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(failureCode,"failureCode"); Objects.requireNonNull(pathSegmentId,"pathSegmentId"); }
        public Type eventType(){return Type.MESSAGE_DELIVERY_FAILURE_DUE;}
    }
    record ResponseSendDue(IdentifierV3 interactionId,IdentifierV3 attemptId,IdentifierV3 responseMessageId) implements SimulationEventV3 {
        public ResponseSendDue { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(responseMessageId,"responseMessageId"); }
        public Type eventType(){return Type.RESPONSE_SEND_DUE;}
    }
    record ResponseArrivalDue(IdentifierV3 interactionId,IdentifierV3 attemptId,IdentifierV3 responseMessageId,IdentifierV3 callerActivationId) implements SimulationEventV3 {
        public ResponseArrivalDue { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(responseMessageId,"responseMessageId"); Objects.requireNonNull(callerActivationId,"callerActivationId"); }
        public Type eventType(){return Type.RESPONSE_ARRIVAL_DUE;}
    }
    record InteractionDeadlineDue(IdentifierV3 interactionId,IdentifierV3 timerId,DurationMsV3 deadlineAtMs) implements SimulationEventV3 {
        public InteractionDeadlineDue { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(timerId,"timerId"); Objects.requireNonNull(deadlineAtMs,"deadlineAtMs"); }
        public Type eventType(){return Type.INTERACTION_DEADLINE_DUE;}
    }
    record AttemptDeadlineDue(IdentifierV3 interactionId,IdentifierV3 attemptId,IdentifierV3 timerId,DurationMsV3 deadlineAtMs) implements SimulationEventV3 {
        public AttemptDeadlineDue { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(timerId,"timerId"); Objects.requireNonNull(deadlineAtMs,"deadlineAtMs"); }
        public Type eventType(){return Type.ATTEMPT_DEADLINE_DUE;}
    }
    record RetryDue(IdentifierV3 interactionId,IdentifierV3 previousAttemptId,long retryOrdinal,IdentifierV3 policyId) implements SimulationEventV3 {
        public RetryDue { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(previousAttemptId,"previousAttemptId"); if(retryOrdinal<1)throw new IllegalArgumentException("Invalid retryOrdinal"); Objects.requireNonNull(policyId,"policyId"); }
        public Type eventType(){return Type.RETRY_DUE;}
    }
    record CancellationDeliveryDue(IdentifierV3 cancellationRequestId,RuntimeEntityRefV3 target,IdentifierV3 sourceInteractionId) implements SimulationEventV3 {
        public CancellationDeliveryDue { Objects.requireNonNull(cancellationRequestId,"cancellationRequestId"); Objects.requireNonNull(target,"target"); Objects.requireNonNull(sourceInteractionId,"sourceInteractionId"); }
        public Type eventType(){return Type.CANCELLATION_DELIVERY_DUE;}
    }
    record BranchStartDue(IdentifierV3 parallelGroupId,IdentifierV3 branchExecutionId,IdentifierV3 entryStepId) implements SimulationEventV3 {
        public BranchStartDue { Objects.requireNonNull(parallelGroupId,"parallelGroupId"); Objects.requireNonNull(branchExecutionId,"branchExecutionId"); Objects.requireNonNull(entryStepId,"entryStepId"); }
        public Type eventType(){return Type.BRANCH_START_DUE;}
    }
    record BranchTerminationDue(IdentifierV3 parallelGroupId,IdentifierV3 branchExecutionId,IdentifierV3 branchResultRef) implements SimulationEventV3 {
        public BranchTerminationDue { Objects.requireNonNull(parallelGroupId,"parallelGroupId"); Objects.requireNonNull(branchExecutionId,"branchExecutionId"); Objects.requireNonNull(branchResultRef,"branchResultRef"); }
        public Type eventType(){return Type.BRANCH_TERMINATION_DUE;}
    }
    record JoinEvaluationDue(IdentifierV3 parallelGroupId,IdentifierV3 joinStepId,long expectedGroupVersion) implements SimulationEventV3 {
        public JoinEvaluationDue { Objects.requireNonNull(parallelGroupId,"parallelGroupId"); Objects.requireNonNull(joinStepId,"joinStepId"); if(expectedGroupVersion<0)throw new IllegalArgumentException("Invalid expectedGroupVersion"); }
        public Type eventType(){return Type.JOIN_EVALUATION_DUE;}
    }
    record BrokerAcceptanceDue(IdentifierV3 publicationId,IdentifierV3 brokerId,IdentifierV3 messageId) implements SimulationEventV3 {
        public BrokerAcceptanceDue { Objects.requireNonNull(publicationId,"publicationId"); Objects.requireNonNull(brokerId,"brokerId"); Objects.requireNonNull(messageId,"messageId"); }
        public Type eventType(){return Type.BROKER_ACCEPTANCE_DUE;}
    }
    record QueueEnqueueDue(IdentifierV3 queueId,IdentifierV3 messageId,IdentifierV3 publicationId) implements SimulationEventV3 {
        public QueueEnqueueDue { Objects.requireNonNull(queueId,"queueId"); Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(publicationId,"publicationId"); }
        public Type eventType(){return Type.QUEUE_ENQUEUE_DUE;}
    }
    record QueueDeliveryDue(IdentifierV3 queueEntryId,IdentifierV3 deliveryAttemptId,IdentifierV3 consumerEndpointId) implements SimulationEventV3 {
        public QueueDeliveryDue { Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(deliveryAttemptId,"deliveryAttemptId"); Objects.requireNonNull(consumerEndpointId,"consumerEndpointId"); }
        public Type eventType(){return Type.QUEUE_DELIVERY_DUE;}
    }
    record ConsumerAckArrivalDue(IdentifierV3 queueEntryId,IdentifierV3 deliveryAttemptId,IdentifierV3 acknowledgementId) implements SimulationEventV3 {
        public ConsumerAckArrivalDue { Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(deliveryAttemptId,"deliveryAttemptId"); Objects.requireNonNull(acknowledgementId,"acknowledgementId"); }
        public Type eventType(){return Type.CONSUMER_ACK_ARRIVAL_DUE;}
    }
    record MessageTtlExpirationDue(IdentifierV3 queueEntryId,IdentifierV3 timerId,DurationMsV3 expiresAtMs) implements SimulationEventV3 {
        public MessageTtlExpirationDue { Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(timerId,"timerId"); Objects.requireNonNull(expiresAtMs,"expiresAtMs"); }
        public Type eventType(){return Type.MESSAGE_TTL_EXPIRATION_DUE;}
    }
    record RedeliveryDue(IdentifierV3 queueEntryId,IdentifierV3 previousDeliveryAttemptId,long nextAttemptNumber) implements SimulationEventV3 {
        public RedeliveryDue { Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(previousDeliveryAttemptId,"previousDeliveryAttemptId"); if(nextAttemptNumber<1)throw new IllegalArgumentException("Invalid nextAttemptNumber"); }
        public Type eventType(){return Type.REDELIVERY_DUE;}
    }
    record ResourceAcquisitionDue(IdentifierV3 resourceId,IdentifierV3 claimId,RuntimeEntityRefV3 owner) implements SimulationEventV3 {
        public ResourceAcquisitionDue { Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(claimId,"claimId"); Objects.requireNonNull(owner,"owner"); }
        public Type eventType(){return Type.RESOURCE_ACQUISITION_DUE;}
    }
    record ResourceReleaseDue(IdentifierV3 resourceId,IdentifierV3 leaseId) implements SimulationEventV3 {
        public ResourceReleaseDue { Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(leaseId,"leaseId"); }
        public Type eventType(){return Type.RESOURCE_RELEASE_DUE;}
    }
    record ResourceStateChangeDue(IdentifierV3 resourceId,IdentifierV3 changeId,IdentifierV3 newStateRef) implements SimulationEventV3 {
        public ResourceStateChangeDue { Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(changeId,"changeId"); Objects.requireNonNull(newStateRef,"newStateRef"); }
        public Type eventType(){return Type.RESOURCE_STATE_CHANGE_DUE;}
    }
    record ResourceWaitTimeoutDue(IdentifierV3 resourceId,IdentifierV3 claimId,IdentifierV3 timerId) implements SimulationEventV3 {
        public ResourceWaitTimeoutDue { Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(claimId,"claimId"); Objects.requireNonNull(timerId,"timerId"); }
        public Type eventType(){return Type.RESOURCE_WAIT_TIMEOUT_DUE;}
    }
    record PolicyTransitionDue(IdentifierV3 policyInstanceId,IdentifierV3 transitionId,IdentifierV3 targetState) implements SimulationEventV3 {
        public PolicyTransitionDue { Objects.requireNonNull(policyInstanceId,"policyInstanceId"); Objects.requireNonNull(transitionId,"transitionId"); Objects.requireNonNull(targetState,"targetState"); }
        public Type eventType(){return Type.POLICY_TRANSITION_DUE;}
    }
    record ResourceWakeupDue(IdentifierV3 resourceId,IdentifierV3 claimId,long resourceVersion) implements SimulationEventV3 {
        public ResourceWakeupDue { Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(claimId,"claimId"); if(resourceVersion<0)throw new IllegalArgumentException("Invalid resourceVersion"); }
        public Type eventType(){return Type.RESOURCE_WAKEUP_DUE;}
    }
}
