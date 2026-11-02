package br.com.archlab.simulation.core.v3;
import java.util.*;
public record ArchitectureV3(IdentifierV3 id,String schemaVersion,List<Component> components,List<Resource> resources,
                             List<Endpoint> endpoints,List<Operation> operations,List<Relation> relations,List<ExecutionPlanV3> executionPlans) {
    public ArchitectureV3 { Objects.requireNonNull(id);ContractV3.requireSchemaVersion(schemaVersion);
        components=List.copyOf(components);resources=List.copyOf(resources);endpoints=List.copyOf(endpoints);
        operations=List.copyOf(operations);relations=List.copyOf(relations);executionPlans=List.copyOf(executionPlans); }
    public record Component(IdentifierV3 id,IdentifierV3 typeId,String displayName,Map<String,PropertyValueV3> properties) {
        public Component { required(id,typeId);name(displayName);properties=Map.copyOf(properties); }
    }
    public record Resource(IdentifierV3 id,IdentifierV3 typeId,IdentifierV3 ownerId,Map<String,PropertyValueV3> properties) {
        public Resource { required(id,typeId,ownerId);properties=Map.copyOf(properties); }
    }
    public enum Direction { INBOUND,OUTBOUND,BIDIRECTIONAL }
    public record Endpoint(IdentifierV3 id,IdentifierV3 ownerId,Direction direction,IdentifierV3 protocolProfileId,
                           List<IdentifierV3> operationIds,Map<String,PropertyValueV3> properties) {
        public Endpoint { required(id,ownerId,protocolProfileId);Objects.requireNonNull(direction);
            operationIds=List.copyOf(operationIds);properties=Map.copyOf(properties); }
    }
    public enum BindingKind { EXECUTION_PLAN,NATIVE_BEHAVIOR }
    public record Binding(BindingKind kind,IdentifierV3 referenceId) { public Binding {Objects.requireNonNull(kind);required(referenceId);} }
    public record Operation(IdentifierV3 id,IdentifierV3 ownerId,String displayName,IdentifierV3 inputContractId,
                            IdentifierV3 outputContractId,Binding execution,List<IdentifierV3> requiredCapabilities) {
        public Operation { required(id,ownerId);name(displayName);Objects.requireNonNull(execution);requiredCapabilities=List.copyOf(requiredCapabilities); }
    }
    public sealed interface Relation {IdentifierV3 id();}
    public record Communication(IdentifierV3 id,IdentifierV3 sourceEndpointId,IdentifierV3 targetEndpointId,
                                IdentifierV3 protocolProfileId,Map<String,PropertyValueV3> properties) implements Relation {
        public Communication {required(id,sourceEndpointId,targetEndpointId,protocolProfileId);properties=Map.copyOf(properties);}
    }
    public enum StructuralKind { HOSTING,CONTAINMENT,MANAGEMENT,POLICY_ATTACHMENT,RESOURCE_OWNERSHIP }
    public record Structural(IdentifierV3 id,StructuralKind kind,IdentifierV3 sourceId,IdentifierV3 targetId,
                             Map<String,PropertyValueV3> properties) implements Relation {
        public Structural {required(id,sourceId,targetId);Objects.requireNonNull(kind);properties=Map.copyOf(properties);}
    }
    private static void required(IdentifierV3... ids){for(var id:ids)Objects.requireNonNull(id);}
    private static void name(String value){if(value==null || value.isEmpty())throw new IllegalArgumentException("Name required");}
}
