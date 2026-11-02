package br.com.archlab.simulation.core.v3;
import java.util.*;
public record SimulationRequestV3(ArchitectureV3 architecture,Scenario scenario,Options options) {
    public SimulationRequestV3 {Objects.requireNonNull(architecture);Objects.requireNonNull(scenario);Objects.requireNonNull(options);}
    public record Entry(IdentifierV3 componentId,IdentifierV3 operationId,IdentifierV3 connectionId,IdentifierV3 inputProfileId) {
        public Entry {Objects.requireNonNull(componentId);Objects.requireNonNull(operationId);}
    }
    public record Scenario(IdentifierV3 id,Entry entry,List<InitialStateV3> initialStates,Map<String,PropertyValueV3> parameters) {
        public Scenario {Objects.requireNonNull(id);Objects.requireNonNull(entry);initialStates=List.copyOf(initialStates);parameters=Map.copyOf(parameters);}
    }
    public enum CompletionMode {DRAIN_ALL,UNTIL_HORIZON}
    public record Options(long seed,CompletionMode completionMode,Long horizonAtMs) {
        public Options {Objects.requireNonNull(completionMode);if(completionMode==CompletionMode.UNTIL_HORIZON?(horizonAtMs==null || horizonAtMs<0):horizonAtMs!=null)throw new IllegalArgumentException("Invalid horizon/mode");}
    }
}
