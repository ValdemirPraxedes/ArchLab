package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.*;import tools.jackson.databind.JsonNode;import java.util.*;
/** Strict transport decoding, separate from semantic admission. */
public final class SimulationRequestMapperV3 {
    private final ArchitectureRequestMapperV3 architecture=new ArchitectureRequestMapperV3();
    public SimulationRequestV3 map(JsonNode input){object(input,"architecture","scenario","options");var scenario=required(input,"scenario");object(scenario,"id","entry","initialStates","parameters");var entry=required(scenario,"entry");object(entry,"componentId","operationId","connectionId","inputProfileId");var states=required(scenario,"initialStates");if(!states.isArray())throw invalid();var initial=new ArrayList<InitialStateV3>();for(var state:states)initial.add(architecture.initialState(state));var parameters=required(scenario,"parameters");object(parameters,parameters.propertyNames().toArray(String[]::new));var props=new LinkedHashMap<String,PropertyValueV3>();for(var key:parameters.propertyNames())props.put(key,architecture.property(parameters.get(key)));var options=required(input,"options");object(options,"seed","completionMode","horizonAtMs");
        return new SimulationRequestV3(architecture.architecture(required(input,"architecture")),new SimulationRequestV3.Scenario(id(scenario,"id"),new SimulationRequestV3.Entry(id(entry,"componentId"),id(entry,"operationId"),optionalId(entry,"connectionId"),optionalId(entry,"inputProfileId")),initial,props),new SimulationRequestV3.Options(number(options,"seed"),SimulationRequestV3.CompletionMode.valueOf(text(options,"completionMode")),options.has("horizonAtMs")?number(options,"horizonAtMs"):null));}
    public record Candidate(ArchitectureV3 architecture,ExecutionPlanV3 plan){}
    public Candidate candidate(JsonNode input){object(input,"architecture","executionPlan");return new Candidate(architecture.architecture(required(input,"architecture")),architecture.plan(required(input,"executionPlan")));}
    private static IdentifierV3 optionalId(JsonNode n,String k){return n.has(k)?id(n,k):null;}private static IdentifierV3 id(JsonNode n,String k){return new IdentifierV3(text(n,k));}
    private static String text(JsonNode n,String k){var v=required(n,k);if(!v.isTextual())throw invalid();return v.asText();}
    private static long number(JsonNode n,String k){var v=required(n,k);if(!v.isIntegralNumber() || !v.canConvertToLong())throw invalid();return v.longValue();}
    private static JsonNode required(JsonNode n,String k){if(n==null || !n.isObject())throw invalid();var value=n.get(k);if(value==null || value.isNull())throw invalid();return value;}
    private static void object(JsonNode n,String... keys){if(n==null || !n.isObject())throw invalid();var allowed=Set.of(keys);for(var k:n.propertyNames())if(!allowed.contains(k))throw invalid();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid v3 request structure");}
}
