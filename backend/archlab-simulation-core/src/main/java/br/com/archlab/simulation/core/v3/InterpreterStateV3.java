package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Immutable control plane, committed alongside runtime entities; no state in handler closures. */
public record InterpreterStateV3(String prefix,long nextId,Map<IdentifierV3,ActivationData> activations,
        Map<IdentifierV3,Run> runs,Map<IdentifierV3,CallData> calls,Map<String,Long> counters,
        Map<IdentifierV3,Long> processing,Map<IdentifierV3,Long> communication,Long principalTerminalAt,
        Long principalResponseAt,String principalOutcome,String globalStop) {
    public InterpreterStateV3 {Objects.requireNonNull(prefix);activations=Map.copyOf(activations);runs=Map.copyOf(runs);calls=Map.copyOf(calls);counters=Map.copyOf(counters);processing=Map.copyOf(processing);communication=Map.copyOf(communication);if(nextId<0)throw new IllegalArgumentException("Negative ID counter");}
    public static InterpreterStateV3 empty(String prefix){return new InterpreterStateV3(prefix,0,Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,null,null,null);}
    public record ActivationData(IdentifierV3 operationId,IdentifierV3 returnInteraction,long startedAt) {public ActivationData{Objects.requireNonNull(operationId);if(startedAt<0)throw new IllegalArgumentException("Negative start");}}
    public record Run(IdentifierV3 activation,IdentifierV3 cursor,IdentifierV3 step,long startedAt,long duration,IdentifierV3 resource,IdentifierV3 claim,IdentifierV3 lease) {public Run {Objects.requireNonNull(activation);Objects.requireNonNull(cursor);Objects.requireNonNull(step);}}
    public record CallData(IdentifierV3 callerCursor,IdentifierV3 callerActivation,IdentifierV3 targetOperation,
            IdentifierV3 callee,IdentifierV3 attempt,IdentifierV3 requestMessage,IdentifierV3 responseMessage,
            IdentifierV3 connection,long latency,boolean awaitResponse,boolean root,String outcome) {public CallData {Objects.requireNonNull(targetOperation);Objects.requireNonNull(callee);Objects.requireNonNull(attempt);Objects.requireNonNull(requestMessage);Objects.requireNonNull(responseMessage);if(latency<0)throw new IllegalArgumentException("Negative latency");}
        public CallData outcome(String value){return new CallData(callerCursor,callerActivation,targetOperation,callee,attempt,requestMessage,responseMessage,connection,latency,awaitResponse,root,value);}}
    public static final class Builder {
        final String prefix;long nextId;final Map<IdentifierV3,ActivationData> activations;final Map<IdentifierV3,Run> runs;final Map<IdentifierV3,CallData> calls;final Map<String,Long> counters;final Map<IdentifierV3,Long> processing,communication;
        Long principalTerminalAt,principalResponseAt;String principalOutcome,globalStop;
        public Builder(InterpreterStateV3 s){prefix=s.prefix;nextId=s.nextId;activations=new LinkedHashMap<>(s.activations);runs=new LinkedHashMap<>(s.runs);calls=new LinkedHashMap<>(s.calls);counters=new LinkedHashMap<>(s.counters);processing=new LinkedHashMap<>(s.processing);communication=new LinkedHashMap<>(s.communication);principalTerminalAt=s.principalTerminalAt;principalResponseAt=s.principalResponseAt;principalOutcome=s.principalOutcome;globalStop=s.globalStop;}
        IdentifierV3 id(String kind){var id=new IdentifierV3(prefix+kind+"-"+nextId);nextId=Math.addExact(nextId,1);return id;}
        long increment(String key){long value=Math.addExact(counters.getOrDefault(key,0L),1);counters.put(key,value);return value;}
        void add(Map<IdentifierV3,Long> target,IdentifierV3 key,long duration){target.put(key,Math.addExact(target.getOrDefault(key,0L),duration));}
        InterpreterStateV3 freeze(){return new InterpreterStateV3(prefix,nextId,activations,runs,calls,counters,processing,communication,principalTerminalAt,principalResponseAt,principalOutcome,globalStop);}
    }
}
