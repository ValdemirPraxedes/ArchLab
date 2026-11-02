package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static br.com.archlab.simulation.core.v3.ExecutionPlanV3.*;
import br.com.archlab.simulation.core.v3.ExecutionPlanV3.Process;
import static br.com.archlab.simulation.core.v3.ArchitectureV3.*;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionPlanValidatorV3Test {
    static IdentifierV3 id(String x){return new IdentifierV3(x);}
    static Map<Outcome,IdentifierV3> next(Outcome outcome,String target){return Map.of(outcome,id(target));}
    static ExecutionPlanV3 plan(){return new ExecutionPlanV3(id("p"),id("op"),id("process"),new Limits(100,10),List.of(
        new Process(id("process"),DurationMsV3.ZERO,null,next(Outcome.NEXT,"call")),
        new Call(id("call"),id("link"),id("db-op"),AwaitCall.RESPONSE,null,next(Outcome.SUCCESS,"publish")),
        new Publish(id("publish"),id("q"),null,AwaitPublish.NONE,null,next(Outcome.DISPATCHED,"condition")),
        new Condition(id("condition"),new Compare(Comparison.EQ,new Literal(1L),new Literal(1L)),id("fork"),id("fork"),id("fork")),
        new Fork(id("fork"),List.of(new Branch(id("b1"),id("delay")),new Branch(id("b2"),id("fail-branch"))),id("join")),
        new Delay(id("delay"),new DurationMsV3(5),id("complete-branch")),
        new Complete(id("complete-branch"),Scope.BRANCH,id("ok")),new Fail(id("fail-branch"),Scope.BRANCH,id("error")),
        new Join(id("join"),JoinPolicy.ALL,List.of(id("b1"),id("b2")),null,RemainingPolicy.CONTINUE,null,next(Outcome.SATISFIED,"done")),
        new Complete(id("done"),Scope.ACTIVATION,id("result"))));}
    static ArchitectureV3 architecture(ExecutionPlanV3 p){return new ArchitectureV3(id("arch"),"3",
        List.of(new Component(id("api"),id("API_SERVICE"),"API",Map.of()),new Component(id("db"),id("DATABASE_GENERIC"),"DB",Map.of()),new Component(id("queue"),id("QUEUE_GENERIC"),"Queue",Map.of())),
        List.of(new Resource(id("q"),id("MESSAGE_QUEUE"),id("queue"),Map.of())),
        List.of(new Endpoint(id("source"),id("api"),Direction.OUTBOUND,id("DATABASE_QUERY"),List.of(),Map.of()),new Endpoint(id("target"),id("db"),Direction.INBOUND,id("DATABASE_QUERY"),List.of(id("db-op")),Map.of())),
        List.of(new Operation(id("op"),id("api"),"op",null,null,new Binding(BindingKind.EXECUTION_PLAN,id("p")),List.of()),new Operation(id("db-op"),id("db"),"db",null,null,new Binding(BindingKind.NATIVE_BEHAVIOR,id("DATABASE_QUERY")),List.of())),
        List.of(new Communication(id("link"),id("source"),id("target"),id("DATABASE_QUERY"),Map.of())),List.of(p));}
    static ExecutionPlanV3 replace(ExecutionPlanV3 p,String stepId,Step replacement){var steps=new ArrayList<>(p.steps());for(int i=0;i<steps.size();i++)if(steps.get(i).id().equals(id(stepId)))steps.set(i,replacement);return new ExecutionPlanV3(p.id(),p.operationId(),p.entryStepId(),p.limits(),steps);}
    static ValidationResultV3 validate(ExecutionPlanV3 p){return new ExecutionPlanValidatorV3(TypeCatalogV3.initial()).validate(architecture(p),p);}
    static void has(ValidationResultV3 result,String code){assertTrue(result.violations().stream().anyMatch(v->v.code().equals(code)),result.toString());}
    @Test void validatesAllNineStepsInContext(){var p=plan();assertTrue(new ArchitectureValidatorV3(TypeCatalogV3.initial()).validate(architecture(p)).valid());assertTrue(validate(p).valid(),validate(p).toString());}
    @ParameterizedTest @EnumSource(JoinPolicy.class) void supportsEveryJoinPolicy(JoinPolicy policy){var p=replace(plan(),"join",new Join(id("join"),policy,List.of(id("b1"),id("b2")),policy==JoinPolicy.QUORUM?2:null,RemainingPolicy.CANCEL_REQUEST,new DurationMsV3(0),next(Outcome.SATISFIED,"done")));assertTrue(validate(p).valid(),validate(p).toString());}
    @Test void rejectsUnsatisfiableQuorumAndForeignBranches(){
        var p=replace(plan(),"join",new Join(id("join"),JoinPolicy.QUORUM,List.of(id("b1"),id("foreign")),3,RemainingPolicy.CONTINUE,null,next(Outcome.SATISFIED,"done")));
        has(validate(p),"UNSATISFIABLE_QUORUM");has(validate(p),"JOIN_BRANCH_NOT_IN_FORK");
    }
    @Test void rejectsDuplicateJoinAndForkBranchIds(){
        var p=replace(plan(),"fork",new Fork(id("fork"),List.of(new Branch(id("b1"),id("delay")),new Branch(id("b1"),id("fail-branch"))),id("join")));has(validate(p),"DUPLICATE_BRANCH");
        p=replace(plan(),"join",new Join(id("join"),JoinPolicy.ALL,List.of(id("b1"),id("b1")),null,RemainingPolicy.CONTINUE,null,next(Outcome.SATISFIED,"done")));has(validate(p),"DUPLICATE_JOIN_BRANCH");
    }
    @Test void rejectsInvalidCallAndPublishTargetsAndPolicies(){
        var p=replace(plan(),"call",new Call(id("call"),id("missing"),id("missing"),AwaitCall.RESPONSE,id("missing"),next(Outcome.SUCCESS,"publish")));
        var r=validate(p);has(r,"COMMUNICATION_NOT_FOUND");has(r,"OPERATION_NOT_FOUND");has(r,"POLICY_NOT_FOUND");
        p=replace(plan(),"publish",new Publish(id("publish"),id("missing"),null,AwaitPublish.NONE,null,next(Outcome.DISPATCHED,"condition")));has(validate(p),"RESOURCE_NOT_FOUND");
    }
    @Test void rejectsMissingStepsWrongTransitionsAndUnreachableNodes(){
        var p=replace(plan(),"process",new Process(id("process"),DurationMsV3.ZERO,id("missing"),next(Outcome.SUCCESS,"missing")));
        var r=validate(p);has(r,"STEP_NOT_FOUND");has(r,"TRANSITION_NOT_ALLOWED");has(r,"SUCCESS_TRANSITION_REQUIRED");has(r,"RESOURCE_NOT_FOUND");has(r,"UNREACHABLE_STEP");
    }
    @Test void rejectsBranchesThatOverlapOrEscapeIntoActivation(){
        var p=replace(plan(),"fork",new Fork(id("fork"),List.of(new Branch(id("b1"),id("delay")),new Branch(id("b2"),id("delay"))),id("join")));has(validate(p),"BRANCH_REGIONS_OVERLAP");
        p=replace(plan(),"delay",new Delay(id("delay"),DurationMsV3.ZERO,id("done")));has(validate(p),"ACTIVATION_TERMINAL_IN_BRANCH");has(validate(p),"BRANCH_ESCAPES_FORK");
    }
    @Test void rejectsWrongJoinTypeAndOrphanJoin(){
        var p=replace(plan(),"fork",new Fork(id("fork"),List.of(new Branch(id("b1"),id("delay")),new Branch(id("b2"),id("fail-branch"))),id("done")));
        has(validate(p),"FORK_JOIN_TYPE");has(validate(p),"JOIN_WITHOUT_FORK");
    }
    @Test void validatesCycleWithoutUnboundedTraversalAndPreservesCandidate(){
        var p=new ExecutionPlanV3(id("p"),id("op"),id("loop"),new Limits(3,1),List.of(new Delay(id("loop"),DurationMsV3.ZERO,id("loop"))));
        assertTrue(validate(p).valid());assertEquals(p,architecture(p).executionPlans().getFirst());
    }
    @Test void rejectsDuplicateIdsMissingEntryAndWrongTerminalScope(){
        var p=new ExecutionPlanV3(id("p"),id("op"),id("missing"),new Limits(1,1),List.of(new Complete(id("same"),Scope.ACTIVATION,id("ok")),new Fail(id("same"),Scope.ACTIVATION,id("error"))));
        has(validate(p),"DUPLICATE_STEP");has(validate(p),"ENTRY_STEP_NOT_FOUND");
        p=new ExecutionPlanV3(id("p"),id("op"),id("branch"),new Limits(1,1),List.of(new Complete(id("branch"),Scope.BRANCH,id("ok"))));has(validate(p),"BRANCH_TERMINAL_OUTSIDE_FORK");
    }
    @Test void rejectsInvalidLimitsAndQuorumShape(){
        assertThrows(IllegalArgumentException.class,()->new Limits(0,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,-1));
        assertThrows(IllegalArgumentException.class,()->new Join(id("j"),JoinPolicy.QUORUM,List.of(id("b")),null,RemainingPolicy.CONTINUE,null,next(Outcome.SATISFIED,"done")));
        assertThrows(IllegalArgumentException.class,()->new Join(id("j"),JoinPolicy.ALL,List.of(id("b")),1,RemainingPolicy.CONTINUE,null,next(Outcome.SATISFIED,"done")));
    }
    @Test void supportsNestedForkRegionsWithoutFalseOverlap(){
        var p=replace(plan(),"delay",new Fork(id("delay"),List.of(new Branch(id("nb1"),id("n1")),new Branch(id("nb2"),id("n2"))),id("nj")));
        var steps=new ArrayList<>(p.steps());steps.add(new Complete(id("n1"),Scope.BRANCH,id("ok")));steps.add(new Fail(id("n2"),Scope.BRANCH,id("error")));
        steps.add(new Join(id("nj"),JoinPolicy.ANY,List.of(id("nb1"),id("nb2")),null,RemainingPolicy.IGNORE_RESULTS,null,next(Outcome.SATISFIED,"complete-branch")));
        p=new ExecutionPlanV3(p.id(),p.operationId(),p.entryStepId(),p.limits(),steps);assertTrue(validate(p).valid(),validate(p).toString());
    }
    @Test void checksActualStepsAgainstOwnerCapabilitiesAndReturnsStableErrors(){
        var p=plan();var a=architecture(p);var components=new ArrayList<>(a.components());components.set(0,new Component(id("api"),id("CLIENT_GENERIC"),"Client",Map.of()));
        var changed=new ArchitectureV3(a.id(),a.schemaVersion(),components,a.resources(),a.endpoints(),a.operations(),a.relations(),a.executionPlans());
        var validator=new ExecutionPlanValidatorV3(TypeCatalogV3.initial());var result=validator.validate(changed,p);has(result,"STEP_CAPABILITY_NOT_SUPPORTED");
        for(int i=0;i<10;i++)assertEquals(result,validator.validate(changed,p));
    }
}
