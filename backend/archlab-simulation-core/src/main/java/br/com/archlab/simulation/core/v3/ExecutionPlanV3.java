package br.com.archlab.simulation.core.v3;
import java.util.*;
public record ExecutionPlanV3(IdentifierV3 id, IdentifierV3 operationId, IdentifierV3 entryStepId,
                              Limits limits, List<Step> steps) {
    public ExecutionPlanV3 { Objects.requireNonNull(id); Objects.requireNonNull(operationId);
        Objects.requireNonNull(entryStepId); Objects.requireNonNull(limits); steps=List.copyOf(steps);
        if(steps.isEmpty()) throw new IllegalArgumentException("Plan requires steps"); }
    public record Limits(long maxStepExecutions,long maxIterations) {
        public Limits { if(maxStepExecutions<1 || maxIterations<1) throw new IllegalArgumentException("Positive limits required"); }
    }
    public enum Outcome { NEXT,DISPATCHED,SUCCESS,BUSINESS_REJECTION,ACCEPTED,REJECTED,FAILURE,TIMEOUT,CANCELLED,TRUE,FALSE,ERROR,SATISFIED,UNSATISFIABLE }
    public enum AwaitCall { NONE,RESPONSE }
    public enum AwaitPublish { NONE,BROKER_ACK,CONSUMER_ACK }
    public enum JoinPolicy { ALL,ANY,FIRST_SUCCESS,QUORUM }
    public enum RemainingPolicy { CONTINUE,CANCEL_REQUEST,IGNORE_RESULTS }
    public enum Scope { BRANCH,ACTIVATION }
    public sealed interface Step {
        IdentifierV3 id();
        default List<IdentifierV3> destinations() { return List.of(); }
    }
    private static Map<Outcome,IdentifierV3> transitions(Map<Outcome,IdentifierV3> values) {
        var result=Map.copyOf(values); if(result.isEmpty()) throw new IllegalArgumentException("Transitions required"); return result;
    }
    public record Process(IdentifierV3 id,DurationMsV3 durationMs,IdentifierV3 resourceProfileId,
                          Map<Outcome,IdentifierV3> transitions) implements Step {
        public Process { Objects.requireNonNull(id); Objects.requireNonNull(durationMs); transitions=ExecutionPlanV3.transitions(transitions); }
        public List<IdentifierV3> destinations(){return List.copyOf(transitions.values());}
    }
    public record Call(IdentifierV3 id,IdentifierV3 connectionId,IdentifierV3 targetOperationId,AwaitCall awaitMode,
                       IdentifierV3 policyRef,Map<Outcome,IdentifierV3> transitions) implements Step {
        public Call { Objects.requireNonNull(id); Objects.requireNonNull(connectionId); Objects.requireNonNull(targetOperationId);
            Objects.requireNonNull(awaitMode); transitions=ExecutionPlanV3.transitions(transitions); }
        public List<IdentifierV3> destinations(){return List.copyOf(transitions.values());}
    }
    public record Publish(IdentifierV3 id,IdentifierV3 targetResourceId,IdentifierV3 payloadProfileId,AwaitPublish awaitMode,
                          IdentifierV3 policyRef,Map<Outcome,IdentifierV3> transitions) implements Step {
        public Publish { Objects.requireNonNull(id); Objects.requireNonNull(targetResourceId); Objects.requireNonNull(awaitMode);
            transitions=ExecutionPlanV3.transitions(transitions); }
        public List<IdentifierV3> destinations(){return List.copyOf(transitions.values());}
    }
    public record Condition(IdentifierV3 id,Expression expression,IdentifierV3 onTrue,IdentifierV3 onFalse,IdentifierV3 onError) implements Step {
        public Condition { Objects.requireNonNull(id); Objects.requireNonNull(expression); Objects.requireNonNull(onTrue);
            Objects.requireNonNull(onFalse); Objects.requireNonNull(onError); }
        public List<IdentifierV3> destinations(){return List.of(onTrue,onFalse,onError);}
    }
    public record Branch(IdentifierV3 id,IdentifierV3 entryStepId) {
        public Branch { Objects.requireNonNull(id); Objects.requireNonNull(entryStepId); }
    }
    public record Fork(IdentifierV3 id,List<Branch> branches,IdentifierV3 joinStepId) implements Step {
        public Fork { Objects.requireNonNull(id); branches=List.copyOf(branches); Objects.requireNonNull(joinStepId);
            if(branches.size()<2) throw new IllegalArgumentException("Fork requires two branches"); }
        public List<IdentifierV3> destinations(){var result=new ArrayList<>(branches.stream().map(Branch::entryStepId).toList());result.add(joinStepId);return List.copyOf(result);}
    }
    public record Join(IdentifierV3 id,JoinPolicy policy,List<IdentifierV3> requiredBranchIds,Integer quorum,
                       RemainingPolicy remainingPolicy,DurationMsV3 deadlineMs,Map<Outcome,IdentifierV3> transitions) implements Step {
        public Join { Objects.requireNonNull(id); Objects.requireNonNull(policy); requiredBranchIds=List.copyOf(requiredBranchIds);
            Objects.requireNonNull(remainingPolicy); transitions=ExecutionPlanV3.transitions(transitions);
            if(requiredBranchIds.isEmpty() || (policy==JoinPolicy.QUORUM ? quorum==null || quorum<1 : quorum!=null))
                throw new IllegalArgumentException("Invalid join policy/quorum"); }
        public List<IdentifierV3> destinations(){return List.copyOf(transitions.values());}
    }
    public record Delay(IdentifierV3 id,DurationMsV3 durationMs,IdentifierV3 nextStepId) implements Step {
        public Delay { Objects.requireNonNull(id); Objects.requireNonNull(durationMs); Objects.requireNonNull(nextStepId); }
        public List<IdentifierV3> destinations(){return List.of(nextStepId);}
    }
    public record Complete(IdentifierV3 id,Scope scope,IdentifierV3 resultProfileId) implements Step {
        public Complete { Objects.requireNonNull(id); Objects.requireNonNull(scope); Objects.requireNonNull(resultProfileId); }
    }
    public record Fail(IdentifierV3 id,Scope scope,IdentifierV3 errorProfileId) implements Step {
        public Fail { Objects.requireNonNull(id); Objects.requireNonNull(scope); Objects.requireNonNull(errorProfileId); }
    }
    public sealed interface Operand { }
    public record Literal(Object value) implements Operand {
        public Literal { if(value!=null && !(value instanceof String || value instanceof Boolean || value instanceof Long))
            throw new IllegalArgumentException("Literal must be string, boolean, long or null"); }
    }
    public record Reference(String path) implements Operand {
        public Reference { if(path==null || path.isEmpty()) throw new IllegalArgumentException("Reference path required"); }
    }
    public enum Comparison { EQ,NE,GT,GTE,LT,LTE }
    public sealed interface Expression { }
    public record Compare(Comparison op,Operand left,Operand right) implements Expression {
        public Compare { Objects.requireNonNull(op);Objects.requireNonNull(left);Objects.requireNonNull(right); }
    }
    public record Exists(Operand operand) implements Expression { public Exists {Objects.requireNonNull(operand);} }
    public enum Logical { AND,OR }
    public record Junction(Logical op,List<Expression> conditions) implements Expression {
        public Junction { Objects.requireNonNull(op);conditions=List.copyOf(conditions);if(conditions.size()<2)throw new IllegalArgumentException("Two conditions required"); }
    }
    public record Not(Expression condition) implements Expression { public Not {Objects.requireNonNull(condition);} }
}
