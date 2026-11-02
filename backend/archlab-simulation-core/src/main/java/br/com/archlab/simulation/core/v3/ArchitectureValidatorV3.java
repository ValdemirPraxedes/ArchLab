package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.ArchitectureV3.*;
/** Stable errors follow input entity order, then lexicographic property order. */
public final class ArchitectureValidatorV3 {
    private final TypeCatalogV3 catalog;
    public ArchitectureValidatorV3(TypeCatalogV3 catalog){this.catalog=Objects.requireNonNull(catalog);}
    public ValidationResultV3 validateInitialState(ArchitectureV3 architecture,List<InitialStateV3> states){
        var errors=new ArrayList<ViolationV3>();var seen=new HashSet<String>();
        for(int i=0;i<states.size();i++){
            var state=Objects.requireNonNull(states.get(i));String p="/initialState/"+i;IdentifierV3 id;String kind;
            switch(state){
                case InitialStateV3.Component c -> {id=c.componentId();kind="COMPONENT";
                    if(architecture.components().stream().noneMatch(x->x.id().equals(c.componentId())))error(errors,"COMPONENT_NOT_FOUND",p+"/componentId",id);}
                case InitialStateV3.Resource r -> {id=r.resourceId();kind="RESOURCE";
                    if(architecture.resources().stream().noneMatch(x->x.id().equals(r.resourceId())))error(errors,"RESOURCE_NOT_FOUND",p+"/resourceId",id);}
                case InitialStateV3.Policy policy -> {id=policy.policyInstanceId();kind="POLICY";
                    if(architecture.resources().stream().noneMatch(x->x.id().equals(policy.policyInstanceId()) && x.typeId().value().equals("POLICY_INSTANCE")))
                        error(errors,"POLICY_NOT_FOUND",p+"/policyInstanceId",id);}
                case InitialStateV3.Queue q -> {id=q.queueId();kind="QUEUE";
                    var queue=architecture.resources().stream().filter(x->x.id().equals(q.queueId()) && x.typeId().value().equals("MESSAGE_QUEUE")).findFirst();
                    if(queue.isEmpty())error(errors,"QUEUE_NOT_FOUND",p+"/queueId",id);
                    else if(q.initialMessageCount()!=null && queue.get().properties().get("capacity") instanceof PropertyValueV3.IntegerValue capacity
                            && q.initialMessageCount()>capacity.value())error(errors,"INITIAL_QUEUE_CAPACITY",p+"/initialMessageCount",id);}
            }
            if(!seen.add(kind+":"+id.value()))error(errors,"DUPLICATE_INITIAL_STATE",p,id);
        }
        return new ValidationResultV3(errors);
    }
    public ValidationResultV3 validate(ArchitectureV3 a) {
        var errors=new ArrayList<ViolationV3>();var all=new LinkedHashMap<IdentifierV3,String>();
        var components=new LinkedHashMap<IdentifierV3,Component>();
        var resources=new LinkedHashMap<IdentifierV3,Resource>();
        var endpoints=new LinkedHashMap<IdentifierV3,Endpoint>();
        var operations=new LinkedHashMap<IdentifierV3,Operation>();
        var plans=new LinkedHashMap<IdentifierV3,ExecutionPlanV3>();
        for(int i=0;i<a.components().size();i++){var x=a.components().get(i);register(all,x.id(),x.typeId().value(),"/components/"+i,errors);components.putIfAbsent(x.id(),x);}
        for(int i=0;i<a.resources().size();i++){var x=a.resources().get(i);register(all,x.id(),x.typeId().value(),"/resources/"+i,errors);resources.putIfAbsent(x.id(),x);}
        for(int i=0;i<a.endpoints().size();i++){var x=a.endpoints().get(i);register(all,x.id(),"ENDPOINT","/endpoints/"+i,errors);endpoints.putIfAbsent(x.id(),x);}
        for(int i=0;i<a.operations().size();i++){var x=a.operations().get(i);register(all,x.id(),"OPERATION","/operations/"+i,errors);operations.putIfAbsent(x.id(),x);}
        for(int i=0;i<a.relations().size();i++)register(all,a.relations().get(i).id(),"RELATION","/relations/"+i,errors);
        for(int i=0;i<a.executionPlans().size();i++){var x=a.executionPlans().get(i);register(all,x.id(),"EXECUTION_PLAN","/executionPlans/"+i,errors);plans.putIfAbsent(x.id(),x);}
        for(int i=0;i<a.components().size();i++) {
            var x=a.components().get(i);String p="/components/"+i;var type=catalog.components().get(x.typeId().value());
            if(type==null)error(errors,"UNSUPPORTED_COMPONENT_TYPE",p+"/typeId",x.typeId());
            else properties(x.properties(),type.properties(),all,p+"/properties",errors);
        }
        for(int i=0;i<a.resources().size();i++) {
            var x=a.resources().get(i);String p="/resources/"+i;var owner=components.get(x.ownerId());
            if(owner==null)error(errors,"OWNER_NOT_FOUND",p+"/ownerId",x.ownerId());
            var type=catalog.resources().get(x.typeId().value());
            if(type==null)error(errors,"UNSUPPORTED_RESOURCE_TYPE",p+"/typeId",x.typeId());
            else { if(owner!=null && !type.ownerTypes().contains(owner.typeId().value()))error(errors,"RESOURCE_OWNER_TYPE",p+"/ownerId",x.ownerId());
                properties(x.properties(),type.properties(),all,p+"/properties",errors); }
        }
        for(int i=0;i<a.operations().size();i++) {
            var x=a.operations().get(i);String p="/operations/"+i;var owner=components.get(x.ownerId());
            if(owner==null)error(errors,"OWNER_NOT_FOUND",p+"/ownerId",x.ownerId());
            var type=owner==null?null:catalog.components().get(owner.typeId().value());
            for(int j=0;j<x.requiredCapabilities().size();j++)if(type!=null && !type.capabilities().contains(x.requiredCapabilities().get(j).value()))
                error(errors,"CAPABILITY_NOT_SUPPORTED",p+"/requiredCapabilities/"+j,x.requiredCapabilities().get(j));
            if(x.execution().kind()==BindingKind.EXECUTION_PLAN){var plan=plans.get(x.execution().referenceId());
                if(plan==null)error(errors,"PLAN_NOT_FOUND",p+"/execution/referenceId",x.execution().referenceId());
                else if(!plan.operationId().equals(x.id()))error(errors,"PLAN_OPERATION_MISMATCH",p+"/execution/referenceId",plan.id());
            } else if(type!=null && !type.nativeBehaviors().contains(x.execution().referenceId().value()))error(errors,"NATIVE_BEHAVIOR_NOT_SUPPORTED",p+"/execution/referenceId",x.execution().referenceId());
        }
        for(int i=0;i<a.endpoints().size();i++) {
            var x=a.endpoints().get(i);String p="/endpoints/"+i;
            if(!components.containsKey(x.ownerId()))error(errors,"OWNER_NOT_FOUND",p+"/ownerId",x.ownerId());
            protocol(x.protocolProfileId(),p+"/protocolProfileId",errors);
            var seen=new HashSet<IdentifierV3>();
            for(int j=0;j<x.operationIds().size();j++){var id=x.operationIds().get(j);var op=operations.get(id);
                if(!seen.add(id))error(errors,"DUPLICATE_OPERATION",p+"/operationIds/"+j,id);
                if(op==null)error(errors,"OPERATION_NOT_FOUND",p+"/operationIds/"+j,id);
                else if(!op.ownerId().equals(x.ownerId()))error(errors,"ENDPOINT_OPERATION_OWNER",p+"/operationIds/"+j,id);
            }
            properties(x.properties(),Map.of(),all,p+"/properties",errors);
        }
        for(int i=0;i<a.relations().size();i++) {
            var x=a.relations().get(i);String p="/relations/"+i;
            switch(x){
                case Communication c -> {
                    var source=endpoints.get(c.sourceEndpointId());var target=endpoints.get(c.targetEndpointId());
                    if(source==null)error(errors,"ENDPOINT_NOT_FOUND",p+"/sourceEndpointId",c.sourceEndpointId());
                    if(target==null)error(errors,"ENDPOINT_NOT_FOUND",p+"/targetEndpointId",c.targetEndpointId());
                    if(source!=null && source.direction()==Direction.INBOUND)error(errors,"SOURCE_DIRECTION",p+"/sourceEndpointId",source.id());
                    if(target!=null && target.direction()==Direction.OUTBOUND)error(errors,"TARGET_DIRECTION",p+"/targetEndpointId",target.id());
                    protocol(c.protocolProfileId(),p+"/protocolProfileId",errors);
                    if(source!=null && !source.protocolProfileId().equals(c.protocolProfileId()) || target!=null && !target.protocolProfileId().equals(c.protocolProfileId()))
                        error(errors,"PROTOCOL_MISMATCH",p+"/protocolProfileId",c.protocolProfileId());
                    properties(c.properties(),Map.of("latencyMs",PropertyValueV3.IntegerValue.class,"timeoutMs",PropertyValueV3.IntegerValue.class),all,p+"/properties",errors);
                }
                case Structural s -> {
                    if(!all.containsKey(s.sourceId()))error(errors,"ENTITY_NOT_FOUND",p+"/sourceId",s.sourceId());
                    if(!all.containsKey(s.targetId()))error(errors,"ENTITY_NOT_FOUND",p+"/targetId",s.targetId());
                    if(s.kind()==StructuralKind.RESOURCE_OWNERSHIP){var resource=resources.get(s.targetId());
                        if(resource==null || !resource.ownerId().equals(s.sourceId()))error(errors,"RESOURCE_OWNERSHIP_MISMATCH",p,s.id());}
                    properties(s.properties(),Map.of(),all,p+"/properties",errors);
                }
            }
        }
        for(int i=0;i<a.executionPlans().size();i++){var x=a.executionPlans().get(i);var op=operations.get(x.operationId());
            if(op==null)error(errors,"OPERATION_NOT_FOUND","/executionPlans/"+i+"/operationId",x.operationId());
            else if(op.execution().kind()!=BindingKind.EXECUTION_PLAN || !op.execution().referenceId().equals(x.id()))
                error(errors,"OPERATION_PLAN_MISMATCH","/executionPlans/"+i+"/operationId",x.operationId());}
        return new ValidationResultV3(errors);
    }
    private void protocol(IdentifierV3 id,String path,List<ViolationV3> errors){if(!catalog.protocols().contains(id.value()))error(errors,"PROTOCOL_NOT_SUPPORTED",path,id);}
    private static void register(Map<IdentifierV3,String> all,IdentifierV3 id,String type,String path,List<ViolationV3> errors){
        if(all.putIfAbsent(id,type)!=null)error(errors,"DUPLICATE_ID",path+"/id",id);
    }
    private static void properties(Map<String,PropertyValueV3> values,Map<String,Class<? extends PropertyValueV3>> rules,
                                   Map<IdentifierV3,String> all,String path,List<ViolationV3> errors){
        for(var entry:new TreeMap<>(values).entrySet()){
            String p=path+"/"+entry.getKey().replace("~","~0").replace("/","~1");var value=entry.getValue();var rule=rules.get(entry.getKey());
            if(rule==null)error(errors,"PROPERTY_NOT_SUPPORTED",p);
            else if(!rule.isInstance(value))error(errors,"PROPERTY_TYPE",p);
            else if(value instanceof PropertyValueV3.IntegerValue n && n.value()<0)error(errors,"PROPERTY_RANGE",p);
            references(value,all,p,errors,0);
        }
    }
    private static void references(PropertyValueV3 value,Map<IdentifierV3,String> all,String p,List<ViolationV3> errors,int depth){
        if(depth>64){error(errors,"PROPERTY_DEPTH_LIMIT",p);return;}
        switch(value){
            case PropertyValueV3.Reference r -> {var type=all.get(r.targetId());if(type==null)error(errors,"REFERENCE_NOT_FOUND",p,r.targetId());
                else if(!type.equals(r.expectedTypeId().value()))error(errors,"REFERENCE_TYPE",p,r.targetId());}
            case PropertyValueV3.Values l -> {for(int i=0;i<l.values().size();i++)references(l.values().get(i),all,p+"/values/"+i,errors,depth+1);}
            case PropertyValueV3.Fields o -> {for(var e:new TreeMap<>(o.fields()).entrySet())references(e.getValue(),all,p+"/fields/"+e.getKey().replace("~","~0").replace("/","~1"),errors,depth+1);}
            default -> { }
        }
    }
    private static void error(List<ViolationV3> errors,String code,String path,IdentifierV3... ids){
        errors.add(new ViolationV3(code,path,code,ViolationV3.Severity.ERROR,List.of(ids)));
    }
}
