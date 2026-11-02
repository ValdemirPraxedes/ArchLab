package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Public trace facts, separately typed from internal scheduled events. */
public record TraceRecordV3(IdentifierV3 recordId,long sequence,long timestampMs,Kind kind,IdentifierV3 executionId,Context context,Detail detail) {
    public TraceRecordV3 {Objects.requireNonNull(recordId);Objects.requireNonNull(kind);Objects.requireNonNull(executionId);Objects.requireNonNull(context);Objects.requireNonNull(detail);if(sequence<0 || timestampMs<0)throw new IllegalArgumentException("Negative trace position");
        boolean valid=switch(kind){
            case EXECUTION_STARTED -> detail instanceof Execution d && d.action().equals("STARTED");
            case ROOT_OPERATION_STARTED -> detail instanceof Execution d && d.action().equals("ROOT_STARTED");
            case ACTIVATION_CREATED -> detail instanceof StateChange;
            case ACTIVATION_STATE_CHANGED -> detail instanceof StateChange;
            case INTERACTION_STATE_CHANGED -> detail instanceof StateChange;
            case ATTEMPT_STATE_CHANGED -> detail instanceof StateChange;
            case STEP_STARTED -> detail instanceof Step d && d.action().equals("STARTED");
            case STEP_FINISHED -> detail instanceof Step d && d.action().equals("FINISHED");
            case STEP_TRANSITION_SELECTED -> detail instanceof Step d && d.action().equals("TRANSITION_SELECTED");
            case MESSAGE_SENT -> detail instanceof Message d && d.action().equals("SENT");
            case MESSAGE_ARRIVED -> detail instanceof Message d && d.action().equals("ARRIVED");
            case MESSAGE_DELIVERY_FAILED -> detail instanceof Message d && d.action().equals("DELIVERY_FAILED");
            case RESPONSE_SENT -> detail instanceof Message d && d.action().equals("RESPONSE_SENT");
            case RESPONSE_ARRIVED -> detail instanceof Message d && d.action().equals("RESPONSE_ARRIVED");
            case RESPONSE_ARRIVED_LATE -> detail instanceof Message d && d.action().equals("RESPONSE_ARRIVED_LATE");
            case JOIN_EVALUATED -> detail instanceof Join d && d.action().equals("EVALUATED");
            case JOIN_SATISFIED -> detail instanceof Join d && d.action().equals("SATISFIED");
            case JOIN_UNSATISFIABLE -> detail instanceof Join d && d.action().equals("UNSATISFIABLE");
            case RESOURCE_ACQUIRED -> detail instanceof Resource d && d.action().equals("ACQUIRED");
            case RESOURCE_RELEASED -> detail instanceof Resource d && d.action().equals("RELEASED");
            case RESOURCE_WAIT_STARTED -> detail instanceof Resource d && d.action().equals("WAIT_STARTED");
            case RESOURCE_STATE_CHANGED -> detail instanceof Resource d && d.action().equals("STATE_CHANGED");
            case RETRY_SCHEDULED -> detail instanceof Retry d && d.action().equals("SCHEDULED");
            case RETRY_STARTED -> detail instanceof Retry d && d.action().equals("STARTED");
            case BROKER_ACCEPTED -> detail instanceof Queue d && d.action().equals("BROKER_ACCEPTED");
            case BROKER_REJECTED -> detail instanceof Queue d && d.action().equals("BROKER_REJECTED");
            case QUEUE_MESSAGE_ENQUEUED -> detail instanceof Queue d && d.action().equals("ENQUEUED");
            case QUEUE_DELIVERY_STARTED -> detail instanceof Queue d && d.action().equals("DELIVERY_STARTED");
            case CONSUMER_ACKNOWLEDGED -> detail instanceof Queue d && d.action().equals("CONSUMER_ACKNOWLEDGED");
            case QUEUE_MESSAGE_ACKNOWLEDGED -> detail instanceof Queue d && d.action().equals("MESSAGE_ACKNOWLEDGED");
            case QUEUE_MESSAGE_EXPIRED -> detail instanceof Queue d && d.action().equals("EXPIRED");
            case PRINCIPAL_RESPONDED -> detail instanceof Execution d && d.action().equals("PRINCIPAL_RESPONDED");
            case PRINCIPAL_TIMED_OUT -> detail instanceof Execution d && d.action().equals("PRINCIPAL_TIMED_OUT");
            case GLOBAL_COMPLETED -> detail instanceof Termination;
            case HORIZON_REACHED -> detail instanceof Termination;
            case LIMIT_REACHED -> detail instanceof Termination;
            case RUNTIME_STATE_DELTA -> detail instanceof RuntimeDelta;
            case SCHEDULER_POSITION_CHANGED -> detail instanceof SchedulerPosition;
        };if(!valid)throw new IllegalArgumentException("Trace kind/detail mismatch");}
    public enum Kind {EXECUTION_STARTED,ROOT_OPERATION_STARTED,ACTIVATION_CREATED,ACTIVATION_STATE_CHANGED,INTERACTION_STATE_CHANGED,ATTEMPT_STATE_CHANGED,STEP_STARTED,STEP_FINISHED,STEP_TRANSITION_SELECTED,MESSAGE_SENT,MESSAGE_ARRIVED,MESSAGE_DELIVERY_FAILED,RESPONSE_SENT,RESPONSE_ARRIVED,RESPONSE_ARRIVED_LATE,JOIN_EVALUATED,JOIN_SATISFIED,JOIN_UNSATISFIABLE,RESOURCE_ACQUIRED,RESOURCE_RELEASED,RESOURCE_WAIT_STARTED,RESOURCE_STATE_CHANGED,RETRY_SCHEDULED,RETRY_STARTED,BROKER_ACCEPTED,BROKER_REJECTED,QUEUE_MESSAGE_ENQUEUED,QUEUE_DELIVERY_STARTED,CONSUMER_ACKNOWLEDGED,QUEUE_MESSAGE_ACKNOWLEDGED,QUEUE_MESSAGE_EXPIRED,PRINCIPAL_RESPONDED,PRINCIPAL_TIMED_OUT,GLOBAL_COMPLETED,HORIZON_REACHED,LIMIT_REACHED,RUNTIME_STATE_DELTA,SCHEDULER_POSITION_CHANGED}
    public sealed interface Detail {String kind();}
    public record Context(IdentifierV3 rootOperationId,IdentifierV3 componentId,IdentifierV3 activationId,IdentifierV3 branchExecutionId,IdentifierV3 stepExecutionId,IdentifierV3 interactionId,IdentifierV3 attemptId,IdentifierV3 messageId,IdentifierV3 causationRecordId,IdentifierV3 sourceEventId) {
        public static Context source(IdentifierV3 id){return new Context(null,null,null,null,null,null,null,null,null,id);}
        public Context withSource(IdentifierV3 id){if(sourceEventId!=null && !sourceEventId.equals(id))throw new IllegalArgumentException("Trace source mismatch");return new Context(rootOperationId,componentId,activationId,branchExecutionId,stepExecutionId,interactionId,attemptId,messageId,causationRecordId,id);}
    }
    public enum JoinPolicy {ALL,ANY,FIRST_SUCCESS,QUORUM}
    public enum RuntimeDeltaEntityKind {CURSOR,TIMER,LEASE,WAIT_CLAIM,POLICY_STATE,STEP_RESULT,BRANCH_CURSOR,WORK_ITEM,SCHEDULED_EVENT,ID_COUNTER}
    public enum RuntimeDeltaOperation {CREATE,UPDATE,REMOVE}
    public enum SchedulerPositionStage {ENVIRONMENT,BEFORE_DEADLINES,DEADLINES,AFTER_DEADLINES,TERMINATION}
    public record StateChange(String entityKind,IdentifierV3 entityId,String previousStatus,String nextStatus,String reasonCode,long previousVersion,long nextVersion) implements Detail {
        public StateChange {if(entityKind==null || entityKind.isEmpty())throw new IllegalArgumentException("entityKind required");Objects.requireNonNull(entityId);if(previousStatus!=null && previousStatus.isEmpty())throw new IllegalArgumentException("Empty previousStatus");if(nextStatus==null || nextStatus.isEmpty())throw new IllegalArgumentException("nextStatus required");if(reasonCode==null || reasonCode.isEmpty())throw new IllegalArgumentException("reasonCode required");if(previousVersion<0)throw new IllegalArgumentException("Negative previousVersion");if(nextVersion<0)throw new IllegalArgumentException("Negative nextVersion");}
        public String kind(){return "STATECHANGE";}
    }
    public record Message(IdentifierV3 messageId,String action,IdentifierV3 endpointId,IdentifierV3 attemptId) implements Detail {
        public Message {Objects.requireNonNull(messageId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");}
        public String kind(){return "MESSAGE";}
    }
    public record Step(IdentifierV3 stepExecutionId,IdentifierV3 stepId,String action,String outcomeCode) implements Detail {
        public Step {Objects.requireNonNull(stepExecutionId);Objects.requireNonNull(stepId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");if(outcomeCode!=null && outcomeCode.isEmpty())throw new IllegalArgumentException("Empty outcomeCode");}
        public String kind(){return "STEP";}
    }
    public record Join(IdentifierV3 parallelGroupId,String action,JoinPolicy policy) implements Detail {
        public Join {Objects.requireNonNull(parallelGroupId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");Objects.requireNonNull(policy);}
        public String kind(){return "JOIN";}
    }
    public record Resource(IdentifierV3 resourceId,String action,IdentifierV3 occupancy) implements Detail {
        public Resource {Objects.requireNonNull(resourceId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");}
        public String kind(){return "RESOURCE";}
    }
    public record Termination(String reasonCode,String status) implements Detail {
        public Termination {if(reasonCode==null || reasonCode.isEmpty())throw new IllegalArgumentException("reasonCode required");if(status==null || status.isEmpty())throw new IllegalArgumentException("status required");}
        public String kind(){return "TERMINATION";}
    }
    public record Execution(String action,IdentifierV3 operationId) implements Detail {
        public Execution {if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");}
        public String kind(){return "EXECUTION";}
    }
    public record Retry(IdentifierV3 interactionId,IdentifierV3 attemptId,String action) implements Detail {
        public Retry {Objects.requireNonNull(interactionId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");}
        public String kind(){return "RETRY";}
    }
    public record Queue(IdentifierV3 queueEntryId,String action,IdentifierV3 deliveryAttemptId) implements Detail {
        public Queue {Objects.requireNonNull(queueEntryId);if(action==null || action.isEmpty())throw new IllegalArgumentException("action required");}
        public String kind(){return "QUEUE";}
    }
    public record RuntimeDelta(RuntimeDeltaEntityKind entityKind,IdentifierV3 entityId,RuntimeDeltaOperation operation,IdentifierV3 deltaId) implements Detail {
        public RuntimeDelta {Objects.requireNonNull(entityKind);Objects.requireNonNull(entityId);Objects.requireNonNull(operation);Objects.requireNonNull(deltaId);}
        public String kind(){return "RUNTIME_DELTA";}
    }
    public record SchedulerPosition(SchedulerPositionStage stage,long round,long dispatchSequence) implements Detail {
        public SchedulerPosition {Objects.requireNonNull(stage);if(round<0)throw new IllegalArgumentException("Negative round");if(dispatchSequence<0)throw new IllegalArgumentException("Negative dispatchSequence");}
        public String kind(){return "SCHEDULER_POSITION";}
    }
}
