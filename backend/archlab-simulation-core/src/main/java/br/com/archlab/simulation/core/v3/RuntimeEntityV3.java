package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Immutable runtime values. Public snapshot fields are kept typed, with mandatory core versions. */
public sealed interface RuntimeEntityV3 {
    enum Kind {ACTIVATION,INTERACTION,ATTEMPT,PARALLEL_GROUP,RESOURCE,QUEUE_ENTRY,PUBLICATION,DELIVERY_ATTEMPT,CURSOR,TIMER,LEASE,WAIT_CLAIM,POLICY_STATE,STEP_RESULT,BRANCH_CURSOR,WORK_ITEM,SCHEDULED_EVENT,ID_COUNTER,COMPONENT,PRINCIPAL,STEP_EXECUTION,CONTROL}
    Kind kind(); IdentifierV3 id(); long version(); String stateName(); RuntimeEntityV3 transition(String target);
    enum ActivationStatus {READY,RUNNING,WAITING,COMPLETED,FAILED,CANCELLED}
    enum InteractionStatus {CREATED,IN_FLIGHT,AWAITING_RESULT,COMPLETED,FAILED,TIMED_OUT,CANCELLED}
    enum AttemptStatus {SCHEDULED,IN_FLIGHT,AWAITING_RESULT,COMPLETED,FAILED,TIMED_OUT,CANCELLED}
    enum ParallelGroupStatus {CREATED,RUNNING,JOIN_SATISFIED,JOIN_UNSATISFIABLE,COMPLETED,CANCELLED}
    enum QueueEntryStatus {READY,IN_DELIVERY,ACKNOWLEDGED,EXPIRED,DEAD_LETTERED,DISCARDED}
    enum PublicationStatus {CREATED,IN_TRANSIT,ACCEPTED,REJECTED,FAILED,TIMED_OUT}
    enum DeliveryAttemptStatus {SCHEDULED,IN_TRANSIT,DELIVERED,AWAITING_ACK,ACKNOWLEDGED,REJECTED,TIMED_OUT,FAILED,CANCELLED}
    enum CursorStatus {READY,RUNNING,WAITING,COMPLETED,FAILED,CANCELLED}
    enum TimerStatus {SCHEDULED,CONSUMED,CANCELLED}
    enum LeaseStatus {HELD,RELEASED}
    enum WaitClaimStatus {WAITING,ACQUIRED,TIMED_OUT,REJECTED}
    enum BranchCursorStatus {READY,RUNNING,WAITING,COMPLETED,FAILED,CANCELLED}
    enum WorkItemStatus {PENDING,RESOLVED}
    enum ComponentStatus {AVAILABLE,UNAVAILABLE}
    enum PrincipalStatus {IN_PROGRESS,RESPONDED,FAILED,TIMED_OUT,CANCELLED}
    enum StepExecutionStatus {READY,RUNNING,WAITING,COMPLETED,FAILED,CANCELLED}
    record Activation(IdentifierV3 activationId,IdentifierV3 componentId,ActivationStatus status,IdentifierV3 currentStepId,long version) implements RuntimeEntityV3 {
        public Activation { Objects.requireNonNull(activationId,"activationId"); Objects.requireNonNull(componentId,"componentId"); Objects.requireNonNull(status,"status");  if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.ACTIVATION;}
        public IdentifierV3 id(){return activationId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Activation(activationId,componentId,ActivationStatus.valueOf(target),currentStepId,Math.addExact(version,1));}
    }
    record Interaction(IdentifierV3 interactionId,InteractionStatus status,List<IdentifierV3> attemptIds,long version) implements RuntimeEntityV3 {
        public Interaction { Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(status,"status"); attemptIds=List.copyOf(attemptIds); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.INTERACTION;}
        public IdentifierV3 id(){return interactionId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Interaction(interactionId,InteractionStatus.valueOf(target),attemptIds,Math.addExact(version,1));}
    }
    record Attempt(IdentifierV3 attemptId,IdentifierV3 interactionId,AttemptStatus status,long version) implements RuntimeEntityV3 {
        public Attempt { Objects.requireNonNull(attemptId,"attemptId"); Objects.requireNonNull(interactionId,"interactionId"); Objects.requireNonNull(status,"status"); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.ATTEMPT;}
        public IdentifierV3 id(){return attemptId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Attempt(attemptId,interactionId,AttemptStatus.valueOf(target),Math.addExact(version,1));}
    }
    record ParallelGroup(IdentifierV3 parallelGroupId,ParallelGroupStatus status,List<IdentifierV3> branchIds,long version) implements RuntimeEntityV3 {
        public ParallelGroup { Objects.requireNonNull(parallelGroupId,"parallelGroupId"); Objects.requireNonNull(status,"status"); branchIds=List.copyOf(branchIds); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.PARALLEL_GROUP;}
        public IdentifierV3 id(){return parallelGroupId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new ParallelGroup(parallelGroupId,ParallelGroupStatus.valueOf(target),branchIds,Math.addExact(version,1));}
    }
    record Resource(IdentifierV3 resourceId,String status,Long capacity,Long inUse,long version) implements RuntimeEntityV3 {
        public Resource { Objects.requireNonNull(resourceId,"resourceId"); if(status==null || status.isEmpty())throw new IllegalArgumentException("status required"); if(capacity!=null && capacity<0)throw new IllegalArgumentException("Invalid capacity"); if(inUse!=null && inUse<0)throw new IllegalArgumentException("Invalid inUse"); if(version<0)throw new IllegalArgumentException("Invalid version"); if(capacity!=null && inUse!=null && inUse>capacity)throw new IllegalArgumentException("Resource over capacity"); }
        public Kind kind(){return Kind.RESOURCE;}
        public IdentifierV3 id(){return resourceId;}
        public String stateName(){return status;}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Resource(resourceId,target,capacity,inUse,Math.addExact(version,1));}
    }
    record QueueEntry(IdentifierV3 queueEntryId,IdentifierV3 queueId,IdentifierV3 messageId,QueueEntryStatus status,long version) implements RuntimeEntityV3 {
        public QueueEntry { Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(queueId,"queueId"); Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(status,"status"); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.QUEUE_ENTRY;}
        public IdentifierV3 id(){return queueEntryId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new QueueEntry(queueEntryId,queueId,messageId,QueueEntryStatus.valueOf(target),Math.addExact(version,1));}
    }
    record Publication(IdentifierV3 publicationId,IdentifierV3 messageId,PublicationStatus status,long version) implements RuntimeEntityV3 {
        public Publication { Objects.requireNonNull(publicationId,"publicationId"); Objects.requireNonNull(messageId,"messageId"); Objects.requireNonNull(status,"status"); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.PUBLICATION;}
        public IdentifierV3 id(){return publicationId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Publication(publicationId,messageId,PublicationStatus.valueOf(target),Math.addExact(version,1));}
    }
    record DeliveryAttempt(IdentifierV3 deliveryAttemptId,IdentifierV3 queueEntryId,DeliveryAttemptStatus status,long version) implements RuntimeEntityV3 {
        public DeliveryAttempt { Objects.requireNonNull(deliveryAttemptId,"deliveryAttemptId"); Objects.requireNonNull(queueEntryId,"queueEntryId"); Objects.requireNonNull(status,"status"); if(version<0)throw new IllegalArgumentException("Invalid version");  }
        public Kind kind(){return Kind.DELIVERY_ATTEMPT;}
        public IdentifierV3 id(){return deliveryAttemptId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new DeliveryAttempt(deliveryAttemptId,queueEntryId,DeliveryAttemptStatus.valueOf(target),Math.addExact(version,1));}
    }
    record Cursor(IdentifierV3 entityId,long version,IdentifierV3 activationId,IdentifierV3 stepExecutionId,IdentifierV3 currentStepId,CursorStatus status,IdentifierV3 waitingFor) implements RuntimeEntityV3 {
        public Cursor { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(activationId,"activationId"); Objects.requireNonNull(stepExecutionId,"stepExecutionId"); Objects.requireNonNull(currentStepId,"currentStepId"); Objects.requireNonNull(status,"status");   }
        public Kind kind(){return Kind.CURSOR;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Cursor(entityId,Math.addExact(version,1),activationId,stepExecutionId,currentStepId,CursorStatus.valueOf(target),waitingFor);}
    }
    record Timer(IdentifierV3 entityId,long version,IdentifierV3 ownerId,DurationMsV3 deadlineAtMs,TimerStatus status,String timerKind) implements RuntimeEntityV3 {
        public Timer { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(ownerId,"ownerId"); Objects.requireNonNull(deadlineAtMs,"deadlineAtMs"); Objects.requireNonNull(status,"status"); if(timerKind==null || timerKind.isEmpty())throw new IllegalArgumentException("timerKind required");  }
        public Kind kind(){return Kind.TIMER;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Timer(entityId,Math.addExact(version,1),ownerId,deadlineAtMs,TimerStatus.valueOf(target),timerKind);}
    }
    record Lease(IdentifierV3 entityId,long version,IdentifierV3 resourceId,IdentifierV3 claimId,LeaseStatus status,long units) implements RuntimeEntityV3 {
        public Lease { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(claimId,"claimId"); Objects.requireNonNull(status,"status"); if(units<1)throw new IllegalArgumentException("Invalid units");  }
        public Kind kind(){return Kind.LEASE;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Lease(entityId,Math.addExact(version,1),resourceId,claimId,LeaseStatus.valueOf(target),units);}
    }
    record WaitClaim(IdentifierV3 entityId,long version,IdentifierV3 resourceId,IdentifierV3 ownerId,long queueOrdinal,WaitClaimStatus status) implements RuntimeEntityV3 {
        public WaitClaim { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(resourceId,"resourceId"); Objects.requireNonNull(ownerId,"ownerId"); if(queueOrdinal<0)throw new IllegalArgumentException("Invalid queueOrdinal"); Objects.requireNonNull(status,"status");  }
        public Kind kind(){return Kind.WAIT_CLAIM;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new WaitClaim(entityId,Math.addExact(version,1),resourceId,ownerId,queueOrdinal,WaitClaimStatus.valueOf(target));}
    }
    record PolicyState(IdentifierV3 entityId,long version,IdentifierV3 policyInstanceId,String status,long counter) implements RuntimeEntityV3 {
        public PolicyState { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(policyInstanceId,"policyInstanceId"); if(status==null || status.isEmpty())throw new IllegalArgumentException("status required"); if(counter<0)throw new IllegalArgumentException("Invalid counter");  }
        public Kind kind(){return Kind.POLICY_STATE;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status;}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new PolicyState(entityId,Math.addExact(version,1),policyInstanceId,target,counter);}
    }
    record StepResult(IdentifierV3 entityId,long version,IdentifierV3 stepExecutionId,String outcome,IdentifierV3 profileId) implements RuntimeEntityV3 {
        public StepResult { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(stepExecutionId,"stepExecutionId"); if(outcome==null || outcome.isEmpty())throw new IllegalArgumentException("outcome required");   }
        public Kind kind(){return Kind.STEP_RESULT;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return null;}
        public RuntimeEntityV3 transition(String target){throw new IllegalStateException("Entity has no lifecycle state");}
    }
    record BranchCursor(IdentifierV3 entityId,long version,IdentifierV3 parallelGroupId,IdentifierV3 branchExecutionId,IdentifierV3 stepId,BranchCursorStatus status) implements RuntimeEntityV3 {
        public BranchCursor { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(parallelGroupId,"parallelGroupId"); Objects.requireNonNull(branchExecutionId,"branchExecutionId"); Objects.requireNonNull(stepId,"stepId"); Objects.requireNonNull(status,"status");  }
        public Kind kind(){return Kind.BRANCH_CURSOR;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new BranchCursor(entityId,Math.addExact(version,1),parallelGroupId,branchExecutionId,stepId,BranchCursorStatus.valueOf(target));}
    }
    record WorkItem(IdentifierV3 entityId,long version,IdentifierV3 ownerId,String workKind,WorkItemStatus status,String observationMode) implements RuntimeEntityV3 {
        public WorkItem { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(ownerId,"ownerId"); if(workKind==null || workKind.isEmpty())throw new IllegalArgumentException("workKind required"); Objects.requireNonNull(status,"status"); if(observationMode==null || observationMode.isEmpty())throw new IllegalArgumentException("observationMode required");  }
        public Kind kind(){return Kind.WORK_ITEM;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new WorkItem(entityId,Math.addExact(version,1),ownerId,workKind,WorkItemStatus.valueOf(target),observationMode);}
    }
    record ScheduledEvent(IdentifierV3 entityId,long version,IdentifierV3 eventId,DurationMsV3 scheduledAtMs,DeterministicSchedulerV3.Phase phase,long sequence,DeterministicSchedulerV3.Epoch causalEpoch,SimulationEventV3 payload) implements RuntimeEntityV3 {
        public ScheduledEvent { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); Objects.requireNonNull(eventId,"eventId"); Objects.requireNonNull(scheduledAtMs,"scheduledAtMs"); Objects.requireNonNull(phase,"phase"); if(sequence<0)throw new IllegalArgumentException("Invalid sequence"); Objects.requireNonNull(causalEpoch,"causalEpoch"); Objects.requireNonNull(payload,"payload");  }
        public Kind kind(){return Kind.SCHEDULED_EVENT;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return null;}
        public RuntimeEntityV3 transition(String target){throw new IllegalStateException("Entity has no lifecycle state");}
    }
    record IdCounter(IdentifierV3 entityId,long version,String counterKind,long nextValue) implements RuntimeEntityV3 {
        public IdCounter { Objects.requireNonNull(entityId,"entityId"); if(version<0)throw new IllegalArgumentException("Invalid version"); if(counterKind==null || counterKind.isEmpty())throw new IllegalArgumentException("counterKind required"); if(nextValue<0)throw new IllegalArgumentException("Invalid nextValue");  }
        public Kind kind(){return Kind.ID_COUNTER;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return null;}
        public RuntimeEntityV3 transition(String target){throw new IllegalStateException("Entity has no lifecycle state");}
    }
    record Component(IdentifierV3 entityId,long version,ComponentStatus status) implements RuntimeEntityV3 {
        public Component {Objects.requireNonNull(entityId);Objects.requireNonNull(status);if(version<0)throw new IllegalArgumentException("Negative version");}
        public Kind kind(){return Kind.COMPONENT;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Component(entityId,Math.addExact(version,1),ComponentStatus.valueOf(target));}
    }
    record Principal(IdentifierV3 entityId,long version,PrincipalStatus status) implements RuntimeEntityV3 {
        public Principal {Objects.requireNonNull(entityId);Objects.requireNonNull(status);if(version<0)throw new IllegalArgumentException("Negative version");}
        public Kind kind(){return Kind.PRINCIPAL;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new Principal(entityId,Math.addExact(version,1),PrincipalStatus.valueOf(target));}
    }
    record StepExecution(IdentifierV3 entityId,long version,StepExecutionStatus status) implements RuntimeEntityV3 {
        public StepExecution {Objects.requireNonNull(entityId);Objects.requireNonNull(status);if(version<0)throw new IllegalArgumentException("Negative version");}
        public Kind kind(){return Kind.STEP_EXECUTION;}
        public IdentifierV3 id(){return entityId;}
        public String stateName(){return status.name();}
        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new StepExecution(entityId,Math.addExact(version,1),StepExecutionStatus.valueOf(target));}
    }
    record Control(IdentifierV3 entityId,long version,InterpreterStateV3 data) implements RuntimeEntityV3 {
        public Control {Objects.requireNonNull(entityId);Objects.requireNonNull(data);if(version<0)throw new IllegalArgumentException("Negative version");}
        public Kind kind(){return Kind.CONTROL;} public IdentifierV3 id(){return entityId;} public String stateName(){return null;}
        public RuntimeEntityV3 transition(String target){throw new IllegalStateException("Control has no lifecycle");}
    }
}
