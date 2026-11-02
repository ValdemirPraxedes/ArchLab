package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.ExecutionPlanV3.*;
import br.com.archlab.simulation.core.v3.ExecutionPlanV3.Process;
import static br.com.archlab.simulation.core.v3.ArchitectureV3.*;
/** Validates a candidate in architectural context without mutating the stored plan. */
public final class ExecutionPlanValidatorV3 {
    private final TypeCatalogV3 catalog;
    public ExecutionPlanValidatorV3(TypeCatalogV3 catalog){this.catalog=Objects.requireNonNull(catalog);}
    public ValidationResultV3 validate(ArchitectureV3 architecture,ExecutionPlanV3 plan){
        var errors=new ArrayList<ViolationV3>();var steps=new LinkedHashMap<IdentifierV3,Step>();
        var operation=architecture.operations().stream().filter(o->o.id().equals(plan.operationId())).findFirst();
        if(operation.isEmpty())error(errors,"OPERATION_NOT_FOUND","/operationId",plan.operationId());
        var owner=operation.flatMap(o->architecture.components().stream().filter(c->c.id().equals(o.ownerId())).findFirst());
        var type=owner.map(o->catalog.components().get(o.typeId().value())).orElse(null);
        if(operation.isPresent() && owner.isEmpty())error(errors,"OPERATION_OWNER_NOT_FOUND","/operationId",plan.operationId());
        if(owner.isPresent() && type==null)error(errors,"UNSUPPORTED_COMPONENT_TYPE","/operationId",owner.get().typeId());
        for(int i=0;i<plan.steps().size();i++){var step=plan.steps().get(i);if(steps.putIfAbsent(step.id(),step)!=null)error(errors,"DUPLICATE_STEP","/steps/"+i+"/id",step.id());}
        if(!steps.containsKey(plan.entryStepId()))error(errors,"ENTRY_STEP_NOT_FOUND","/entryStepId",plan.entryStepId());
        var forks=new LinkedHashMap<IdentifierV3,Fork>();var branches=new HashSet<IdentifierV3>();
        for(int i=0;i<plan.steps().size();i++){
            var step=plan.steps().get(i);String p="/steps/"+i;
            if(type!=null && !type.capabilities().contains(kind(step)))error(errors,"STEP_CAPABILITY_NOT_SUPPORTED",p+"/kind",step.id());
            for(var destination:orderedDestinations(step))if(!steps.containsKey(destination))error(errors,"STEP_NOT_FOUND",p,destination);
            switch(step){
                case Process process -> {allowed(process.transitions(),Set.of(Outcome.NEXT),Set.of(Outcome.NEXT),p,errors);
                    if(process.resourceProfileId()!=null){var resource=resource(architecture,process.resourceProfileId());
                        if(resource.isEmpty())error(errors,"RESOURCE_NOT_FOUND",p+"/resourceProfileId",process.resourceProfileId());
                        else if(!resource.get().typeId().value().equals("PROCESSING_POOL") || operation.isPresent() && !resource.get().ownerId().equals(operation.get().ownerId()))
                            error(errors,"PROCESS_RESOURCE_OWNER_OR_TYPE",p+"/resourceProfileId",process.resourceProfileId());}}
                case Call call -> {
                    var allowed=call.awaitMode()==AwaitCall.NONE?Set.of(Outcome.DISPATCHED,Outcome.FAILURE):Set.of(Outcome.SUCCESS,Outcome.BUSINESS_REJECTION,Outcome.FAILURE,Outcome.TIMEOUT,Outcome.CANCELLED);
                    allowed(call.transitions(),allowed,call.awaitMode()==AwaitCall.NONE?Set.of(Outcome.DISPATCHED):Set.of(Outcome.SUCCESS),p,errors);
                    var relation=architecture.relations().stream().filter(r->r.id().equals(call.connectionId()) && r instanceof Communication).map(r->(Communication)r).findFirst();
                    if(relation.isEmpty())error(errors,"COMMUNICATION_NOT_FOUND",p+"/connectionId",call.connectionId());
                    var targetOperation=architecture.operations().stream().filter(o->o.id().equals(call.targetOperationId())).findFirst();
                    if(targetOperation.isEmpty())error(errors,"OPERATION_NOT_FOUND",p+"/targetOperationId",call.targetOperationId());
                    if(relation.isPresent()){
                        var source=architecture.endpoints().stream().filter(e->e.id().equals(relation.get().sourceEndpointId())).findFirst();
                        var target=architecture.endpoints().stream().filter(e->e.id().equals(relation.get().targetEndpointId())).findFirst();
                        if(source.isEmpty() || operation.isPresent() && !source.get().ownerId().equals(operation.get().ownerId()))error(errors,"CALL_SOURCE_OWNER",p+"/connectionId",call.connectionId());
                        if(target.isEmpty() || !target.get().operationIds().contains(call.targetOperationId()) || targetOperation.isPresent() && !target.get().ownerId().equals(targetOperation.get().ownerId()))
                            error(errors,"CALL_TARGET_ENDPOINT",p+"/targetOperationId",call.targetOperationId());
                    }
                    policy(architecture,call.policyRef(),p,errors);
                }
                case Publish publish -> {
                    allowed(publish.transitions(),publish.awaitMode()==AwaitPublish.NONE?Set.of(Outcome.DISPATCHED,Outcome.FAILURE):Set.of(Outcome.ACCEPTED,Outcome.SUCCESS,Outcome.REJECTED,Outcome.FAILURE,Outcome.TIMEOUT,Outcome.CANCELLED),
                            publish.awaitMode()==AwaitPublish.NONE?Set.of(Outcome.DISPATCHED):Set.of(Outcome.ACCEPTED,Outcome.SUCCESS),p,errors);
                    var resource=resource(architecture,publish.targetResourceId());
                    if(resource.isEmpty())error(errors,"RESOURCE_NOT_FOUND",p+"/targetResourceId",publish.targetResourceId());
                    else if(!Set.of("MESSAGE_QUEUE","TOPIC").contains(resource.get().typeId().value()))error(errors,"PUBLISH_RESOURCE_TYPE",p+"/targetResourceId",publish.targetResourceId());
                    policy(architecture,publish.policyRef(),p,errors);
                }
                case Fork fork -> {
                    var join=steps.get(fork.joinStepId());
                    if(!(join instanceof Join))error(errors,"FORK_JOIN_TYPE",p+"/joinStepId",fork.joinStepId());
                    if(forks.putIfAbsent(fork.joinStepId(),fork)!=null)error(errors,"JOIN_MULTIPLE_FORKS",p+"/joinStepId",fork.joinStepId());
                    var local=new HashSet<IdentifierV3>();
                    for(int j=0;j<fork.branches().size();j++){var branch=fork.branches().get(j);
                        if(!local.add(branch.id()) || !branches.add(branch.id()))error(errors,"DUPLICATE_BRANCH",p+"/branches/"+j+"/id",branch.id());}
                    if(join instanceof Join j)for(var id:j.requiredBranchIds())if(!local.contains(id))error(errors,"JOIN_BRANCH_NOT_IN_FORK",p+"/joinStepId",id);
                }
                case Join join -> {
                    allowed(join.transitions(),Set.of(Outcome.SATISFIED,Outcome.UNSATISFIABLE,Outcome.TIMEOUT,Outcome.CANCELLED),Set.of(Outcome.SATISFIED),p,errors);
                    if(new HashSet<>(join.requiredBranchIds()).size()!=join.requiredBranchIds().size())error(errors,"DUPLICATE_JOIN_BRANCH",p+"/requiredBranchIds",join.id());
                    if(join.policy()==JoinPolicy.QUORUM && join.quorum()>new HashSet<>(join.requiredBranchIds()).size())error(errors,"UNSATISFIABLE_QUORUM",p+"/quorum",join.id());
                }
                default -> { }
            }
        }
        var reachable=reachable(steps,plan.entryStepId(),null);
        var mainRegion=mainRegion(steps,plan.entryStepId());
        for(int i=0;i<plan.steps().size();i++){
            var step=plan.steps().get(i);String p="/steps/"+i;
            if(!reachable.contains(step.id()))error(errors,"UNREACHABLE_STEP",p,step.id());
            if(step instanceof Join && !forks.containsKey(step.id()))error(errors,"JOIN_WITHOUT_FORK",p,step.id());
            if(mainRegion.contains(step.id()) && (step instanceof Complete c && c.scope()==Scope.BRANCH || step instanceof Fail f && f.scope()==Scope.BRANCH))
                error(errors,"BRANCH_TERMINAL_OUTSIDE_FORK",p,step.id());
            if(step instanceof Fork fork)validateBranches(fork,steps,mainRegion,p,errors);
        }
        // Cycles are legal data; finite positive plan limits bound execution at runtime.
        return new ValidationResultV3(errors);
    }
    private static void validateBranches(Fork fork,Map<IdentifierV3,Step> steps,Set<IdentifierV3> mainRegion,String p,List<ViolationV3> errors){
        var seen=new HashSet<IdentifierV3>();
        for(int i=0;i<fork.branches().size();i++){
            var region=reachable(steps,fork.branches().get(i).entryStepId(),fork.joinStepId());
            for(var id:region){if(!seen.add(id))error(errors,"BRANCH_REGIONS_OVERLAP",p+"/branches/"+i,id);
                if(mainRegion.contains(id))error(errors,"BRANCH_ESCAPES_FORK",p+"/branches/"+i,id);
                var step=steps.get(id);
                if(step instanceof Complete c && c.scope()==Scope.ACTIVATION || step instanceof Fail f && f.scope()==Scope.ACTIVATION)
                    error(errors,"ACTIVATION_TERMINAL_IN_BRANCH",p+"/branches/"+i,id);
            }
        }
    }
    private static Set<IdentifierV3> mainRegion(Map<IdentifierV3,Step> steps,IdentifierV3 entry){
        var visited=new LinkedHashSet<IdentifierV3>();var pending=new ArrayDeque<IdentifierV3>();pending.add(entry);
        while(!pending.isEmpty()){var id=pending.removeFirst();if(!steps.containsKey(id) || !visited.add(id))continue;
            var step=steps.get(id);if(step instanceof Fork f)pending.add(f.joinStepId());else pending.addAll(orderedDestinations(step));}
        return visited;
    }
    private static Set<IdentifierV3> reachable(Map<IdentifierV3,Step> steps,IdentifierV3 entry,IdentifierV3 stop){
        var visited=new LinkedHashSet<IdentifierV3>();var pending=new ArrayDeque<IdentifierV3>();pending.add(entry);
        while(!pending.isEmpty()){var id=pending.removeFirst();if(id.equals(stop) || !steps.containsKey(id) || !visited.add(id))continue;
            pending.addAll(orderedDestinations(steps.get(id)));}
        return visited;
    }
    private static List<IdentifierV3> orderedDestinations(Step step){return step.destinations().stream().distinct().sorted(Comparator.comparing(IdentifierV3::value)).toList();}
    private static Optional<Resource> resource(ArchitectureV3 a,IdentifierV3 id){return a.resources().stream().filter(r->r.id().equals(id)).findFirst();}
    private static void policy(ArchitectureV3 a,IdentifierV3 id,String p,List<ViolationV3> errors){if(id!=null && resource(a,id).filter(r->r.typeId().value().equals("POLICY_INSTANCE")).isEmpty())error(errors,"POLICY_NOT_FOUND",p+"/policyRef",id);}
    private static void allowed(Map<Outcome,IdentifierV3> actual,Set<Outcome> allowed,Set<Outcome> success,String p,List<ViolationV3> errors){
        for(var outcome:Outcome.values())if(actual.containsKey(outcome) && !allowed.contains(outcome))error(errors,"TRANSITION_NOT_ALLOWED",p+"/transitions/"+outcome);
        if(actual.keySet().stream().noneMatch(success::contains))error(errors,"SUCCESS_TRANSITION_REQUIRED",p+"/transitions");
    }
    private static String kind(Step step){return switch(step){case Process p->"PROCESS";case Call c->"CALL";case Publish p->"PUBLISH";case Condition c->"CONDITION";case Fork f->"FORK";case Join j->"JOIN";case Delay d->"DELAY";case Complete c->"COMPLETE";case Fail f->"FAIL";};}
    private static void error(List<ViolationV3> errors,String code,String p,IdentifierV3... ids){errors.add(new ViolationV3(code,p,code,ViolationV3.Severity.ERROR,List.of(ids)));}
}
