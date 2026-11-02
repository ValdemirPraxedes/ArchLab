package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.*;
import br.com.archlab.simulation.core.v3.ArchitectureV3.*;
import br.com.archlab.simulation.core.v3.ExecutionPlanV3.*;
import br.com.archlab.simulation.core.v3.ExecutionPlanV3.Process;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Function;
/** Strict JSON decoder. Invalid syntax/types never reach semantic validation. */
public final class ArchitectureRequestMapperV3 {
    public InitialStateV3 initialState(JsonNode n){String kind=text(n,"kind");return switch(kind){
        case "COMPONENT" -> {object(n,"kind","componentId","available");yield new InitialStateV3.Component(id(n,"componentId"),optionalBoolean(n,"available"));}
        case "RESOURCE" -> {object(n,"kind","resourceId","available");yield new InitialStateV3.Resource(id(n,"resourceId"),optionalBoolean(n,"available"));}
        case "POLICY" -> {object(n,"kind","policyInstanceId","state");yield new InitialStateV3.Policy(id(n,"policyInstanceId"),n.has("state")?text(n,"state"):null);}
        case "QUEUE" -> {object(n,"kind","queueId","initialMessageCount");yield new InitialStateV3.Queue(id(n,"queueId"),n.has("initialMessageCount")?number(n,"initialMessageCount"):null);}
        default -> throw invalid("Unknown initial state kind");};}
    private static Boolean optionalBoolean(JsonNode n,String key){if(!n.has(key))return null;var v=required(n,key);if(!v.isBoolean())throw invalid("Expected boolean");return v.booleanValue();}
    public ArchitectureV3 architecture(JsonNode n) {
        object(n,"id","schemaVersion","components","resources","endpoints","operations","relations","executionPlans");
        return new ArchitectureV3(id(n,"id"),text(n,"schemaVersion"),list(n,"components",this::component),
                list(n,"resources",this::resource),list(n,"endpoints",this::endpoint),list(n,"operations",this::operation),
                list(n,"relations",this::relation),list(n,"executionPlans",this::plan));
    }
    private Component component(JsonNode n){object(n,"id","typeId","displayName","properties");return new Component(id(n,"id"),id(n,"typeId"),text(n,"displayName"),properties(required(n,"properties"),0));}
    private Resource resource(JsonNode n){object(n,"id","typeId","ownerId","properties");return new Resource(id(n,"id"),id(n,"typeId"),id(n,"ownerId"),properties(required(n,"properties"),0));}
    private Endpoint endpoint(JsonNode n){object(n,"id","ownerId","direction","protocolProfileId","operationIds","properties");
        return new Endpoint(id(n,"id"),id(n,"ownerId"),Direction.valueOf(text(n,"direction")),id(n,"protocolProfileId"),
                list(n,"operationIds",this::identifier),optionalProperties(n));}
    private Operation operation(JsonNode n){object(n,"id","ownerId","displayName","inputContractId","outputContractId","execution","requiredCapabilities");
        var b=required(n,"execution");object(b,"kind","referenceId");
        return new Operation(id(n,"id"),id(n,"ownerId"),text(n,"displayName"),optionalId(n,"inputContractId"),optionalId(n,"outputContractId"),
                new Binding(BindingKind.valueOf(text(b,"kind")),id(b,"referenceId")),n.has("requiredCapabilities")?list(n,"requiredCapabilities",this::identifier):List.of());}
    private Relation relation(JsonNode n){String kind=text(n,"kind");
        if(kind.equals("COMMUNICATION")){object(n,"id","kind","sourceEndpointId","targetEndpointId","protocolProfileId","properties");
            return new Communication(id(n,"id"),id(n,"sourceEndpointId"),id(n,"targetEndpointId"),id(n,"protocolProfileId"),optionalProperties(n));}
        object(n,"id","kind","sourceId","targetId","properties");return new Structural(id(n,"id"),StructuralKind.valueOf(kind),id(n,"sourceId"),id(n,"targetId"),optionalProperties(n));}
    public ExecutionPlanV3 plan(JsonNode n){object(n,"id","operationId","entryStepId","limits","steps");var limits=required(n,"limits");object(limits,"maxStepExecutions","maxIterations");
        return new ExecutionPlanV3(id(n,"id"),id(n,"operationId"),id(n,"entryStepId"),new Limits(number(limits,"maxStepExecutions"),number(limits,"maxIterations")),list(n,"steps",this::step));}
    public Step step(JsonNode n){String kind=text(n,"kind");
        return switch(kind){
            case "PROCESS" -> {object(n,"id","kind","durationMs","resourceProfileId","transitions");yield new Process(id(n,"id"),duration(n,"durationMs"),optionalId(n,"resourceProfileId"),transitions(n));}
            case "CALL" -> {object(n,"id","kind","connectionId","targetOperationId","awaitMode","policyRef","transitions");yield new Call(id(n,"id"),id(n,"connectionId"),id(n,"targetOperationId"),AwaitCall.valueOf(text(n,"awaitMode")),optionalId(n,"policyRef"),transitions(n));}
            case "PUBLISH" -> {object(n,"id","kind","targetResourceId","payloadProfileId","awaitMode","policyRef","transitions");yield new Publish(id(n,"id"),id(n,"targetResourceId"),optionalId(n,"payloadProfileId"),AwaitPublish.valueOf(text(n,"awaitMode")),optionalId(n,"policyRef"),transitions(n));}
            case "CONDITION" -> {object(n,"id","kind","expression","onTrue","onFalse","onError");yield new Condition(id(n,"id"),expression(required(n,"expression"),0),id(n,"onTrue"),id(n,"onFalse"),id(n,"onError"));}
            case "FORK" -> {object(n,"id","kind","branches","joinStepId");yield new Fork(id(n,"id"),list(n,"branches",b->{object(b,"id","entryStepId");return new Branch(id(b,"id"),id(b,"entryStepId"));}),id(n,"joinStepId"));}
            case "JOIN" -> {object(n,"id","kind","policy","requiredBranchIds","quorum","remainingPolicy","deadlineMs","transitions");
                Long quorum=n.has("quorum")?number(n,"quorum"):null;if(quorum!=null && quorum>Integer.MAX_VALUE)throw invalid("Quorum exceeds runtime limit");
                yield new Join(id(n,"id"),JoinPolicy.valueOf(text(n,"policy")),list(n,"requiredBranchIds",this::identifier),quorum==null?null:quorum.intValue(),
                        RemainingPolicy.valueOf(text(n,"remainingPolicy")),n.has("deadlineMs")?duration(n,"deadlineMs"):null,transitions(n));}
            case "DELAY" -> {object(n,"id","kind","durationMs","nextStepId");yield new Delay(id(n,"id"),duration(n,"durationMs"),id(n,"nextStepId"));}
            case "COMPLETE" -> {object(n,"id","kind","scope","resultProfileId");yield new Complete(id(n,"id"),Scope.valueOf(text(n,"scope")),id(n,"resultProfileId"));}
            case "FAIL" -> {object(n,"id","kind","scope","errorProfileId");yield new Fail(id(n,"id"),Scope.valueOf(text(n,"scope")),id(n,"errorProfileId"));}
            default -> throw invalid("Unknown step kind");
        };}
    private Expression expression(JsonNode n,int depth){if(depth>64)throw invalid("Condition depth limit");String op=text(n,"op");
        return switch(op){
            case "EXISTS" -> {object(n,"op","operand");yield new Exists(operand(required(n,"operand")));}
            case "AND","OR" -> {object(n,"op","conditions");yield new Junction(Logical.valueOf(op),list(n,"conditions",c->expression(c,depth+1)));}
            case "NOT" -> {object(n,"op","condition");yield new Not(expression(required(n,"condition"),depth+1));}
            default -> {object(n,"op","left","right");yield new Compare(Comparison.valueOf(op),operand(required(n,"left")),operand(required(n,"right")));}
        };}
    private Operand operand(JsonNode n){String kind=text(n,"kind");
        if(kind.equals("REFERENCE")){object(n,"kind","path");return new ExecutionPlanV3.Reference(text(n,"path"));}
        if(!kind.equals("LITERAL"))throw invalid("Unknown operand kind");object(n,"kind","value");var value=n.get("value");if(value==null)throw invalid("Missing literal value");
        if(value.isNull())return new Literal(null);if(value.isTextual())return new Literal(value.asText());if(value.isBoolean())return new Literal(value.booleanValue());
        if(value.isIntegralNumber() && value.canConvertToLong())return new Literal(value.longValue());throw invalid("Invalid literal");}
    private Map<Outcome,IdentifierV3> transitions(JsonNode n){var node=required(n,"transitions");requireObject(node);var result=new EnumMap<Outcome,IdentifierV3>(Outcome.class);
        for(String key:node.propertyNames())result.put(Outcome.valueOf(key),identifier(node.get(key)));return result;}
    public PropertyValueV3 property(JsonNode n){return property(n,0);}
    private PropertyValueV3 property(JsonNode n,int depth){if(depth>64)throw invalid("Property depth limit");String kind=text(n,"kind");return switch(kind){
        case "STRING" -> {object(n,"kind","value");yield new PropertyValueV3.Text(text(n,"value"));}
        case "INTEGER" -> {object(n,"kind","value");yield new PropertyValueV3.IntegerValue(number(n,"value"));}
        case "DECIMAL" -> {object(n,"kind","value");yield new PropertyValueV3.Decimal(text(n,"value"));}
        case "BOOLEAN" -> {object(n,"kind","value");var v=required(n,"value");if(!v.isBoolean())throw invalid("Expected boolean");yield new PropertyValueV3.Flag(v.booleanValue());}
        case "ENUM" -> {object(n,"kind","value");yield new PropertyValueV3.Choice(text(n,"value"));}
        case "REFERENCE" -> {object(n,"kind","targetId","expectedTypeId");yield new PropertyValueV3.Reference(id(n,"targetId"),id(n,"expectedTypeId"));}
        case "LIST" -> {object(n,"kind","values");yield new PropertyValueV3.Values(list(n,"values",v->property(v,depth+1)));}
        case "OBJECT" -> {object(n,"kind","fields");yield new PropertyValueV3.Fields(properties(required(n,"fields"),depth+1));}
        default -> throw invalid("Unknown property kind");};}
    private Map<String,PropertyValueV3> optionalProperties(JsonNode n){return n.has("properties")?properties(required(n,"properties"),0):Map.of();}
    private Map<String,PropertyValueV3> properties(JsonNode n,int depth){requireObject(n);var result=new LinkedHashMap<String,PropertyValueV3>();
        for(String key:n.propertyNames())result.put(key,property(n.get(key),depth));return result;}
    private IdentifierV3 identifier(JsonNode n){if(n==null || !n.isTextual())throw invalid("Expected identifier");return new IdentifierV3(n.asText());}
    private IdentifierV3 id(JsonNode n,String k){return identifier(required(n,k));}
    private IdentifierV3 optionalId(JsonNode n,String k){return n.has(k)?id(n,k):null;}
    private DurationMsV3 duration(JsonNode n,String k){return new DurationMsV3(number(n,k));}
    private static void requireObject(JsonNode n){if(n==null || !n.isObject())throw invalid("Expected object");}
    private static void object(JsonNode n,String... keys){requireObject(n);var allowed=Set.of(keys);for(String key:n.propertyNames())if(!allowed.contains(key))throw invalid("Unknown field: "+key);}
    private static JsonNode required(JsonNode n,String k){if(n==null || !n.isObject())throw invalid("Expected object");var v=n.get(k);if(v==null || v.isNull())throw invalid("Missing field: "+k);return v;}
    private static String text(JsonNode n,String k){var v=required(n,k);if(!v.isTextual())throw invalid("Expected string: "+k);return v.asText();}
    private static long number(JsonNode n,String k){var v=required(n,k);if(!v.isIntegralNumber() || !v.canConvertToLong())throw invalid("Expected int64: "+k);return v.longValue();}
    private static <T> List<T> list(JsonNode n,String k,Function<JsonNode,T> mapper){var v=required(n,k);if(!v.isArray())throw invalid("Expected array: "+k);var result=new ArrayList<T>();for(var item:v)result.add(mapper.apply(item));return List.copyOf(result);}
    private static IllegalArgumentException invalid(String detail){return new IllegalArgumentException(detail);}
}
