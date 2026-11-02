package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Immutable control plane, committed alongside runtime entities; no state in handler closures. */
public record InterpreterStateV3(String prefix,long nextId,Map<IdentifierV3,ActivationData> activations,
        Map<IdentifierV3,Run> runs,Map<IdentifierV3,CallData> calls,Map<IdentifierV3,CallData> attemptCalls,Map<IdentifierV3,GroupData> groups,Map<IdentifierV3,BranchData> branches,Map<IdentifierV3,PublicationData> publications,Map<IdentifierV3,DeliveryData> deliveries,Map<String,Long> counters,
        Map<IdentifierV3,Long> processing,Map<IdentifierV3,Long> communication,Long principalTerminalAt,
        Long principalResponseAt,String principalOutcome,String globalStop) {
    public InterpreterStateV3 {Objects.requireNonNull(prefix);activations=Map.copyOf(activations);runs=Map.copyOf(runs);calls=Map.copyOf(calls);attemptCalls=Map.copyOf(attemptCalls);groups=Map.copyOf(groups);branches=Map.copyOf(branches);publications=Map.copyOf(publications);deliveries=Map.copyOf(deliveries);counters=Map.copyOf(counters);processing=Map.copyOf(processing);communication=Map.copyOf(communication);if(nextId<0)throw new IllegalArgumentException("Negative ID counter");}
    public static InterpreterStateV3 empty(String prefix){return new InterpreterStateV3(prefix,0,Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,null,null,null);}
    public record ActivationData(IdentifierV3 operationId,IdentifierV3 returnInteraction,long startedAt,IdentifierV3 returnGroup,IdentifierV3 branch,IdentifierV3 returnAttempt) {public ActivationData(IdentifierV3 operationId,IdentifierV3 returnInteraction,long startedAt){this(operationId,returnInteraction,startedAt,null,null,null);}public ActivationData{Objects.requireNonNull(operationId);if(startedAt<0)throw new IllegalArgumentException("Negative start");}}
    public record Run(IdentifierV3 activation,IdentifierV3 cursor,IdentifierV3 step,long startedAt,long duration,IdentifierV3 resource,IdentifierV3 claim,IdentifierV3 lease) {public Run {Objects.requireNonNull(activation);Objects.requireNonNull(cursor);Objects.requireNonNull(step);}}
    public record CallData(IdentifierV3 callerCursor,IdentifierV3 callerActivation,IdentifierV3 targetOperation,
            IdentifierV3 callee,IdentifierV3 attempt,IdentifierV3 requestMessage,IdentifierV3 responseMessage,
            IdentifierV3 connection,long latency,boolean awaitResponse,boolean root,String outcome,IdentifierV3 consumerEntry,IdentifierV3 deliveryAttempt,IdentifierV3 consumerEndpoint,IdentifierV3 policy,long ordinal) {public CallData(IdentifierV3 callerCursor,IdentifierV3 callerActivation,IdentifierV3 targetOperation,IdentifierV3 callee,IdentifierV3 attempt,IdentifierV3 requestMessage,IdentifierV3 responseMessage,IdentifierV3 connection,long latency,boolean awaitResponse,boolean root,String outcome){this(callerCursor,callerActivation,targetOperation,callee,attempt,requestMessage,responseMessage,connection,latency,awaitResponse,root,outcome,null,null,null,null,1);}public CallData {Objects.requireNonNull(targetOperation);Objects.requireNonNull(callee);Objects.requireNonNull(attempt);Objects.requireNonNull(requestMessage);Objects.requireNonNull(responseMessage);if(latency<0)throw new IllegalArgumentException("Negative latency");}
        public CallData outcome(String value){return new CallData(callerCursor,callerActivation,targetOperation,callee,attempt,requestMessage,responseMessage,connection,latency,awaitResponse,root,value,consumerEntry,deliveryAttempt,consumerEndpoint,policy,ordinal);}}
    public record BranchData(IdentifierV3 group,IdentifierV3 activation,IdentifierV3 definition){}
    public record GroupData(IdentifierV3 activation,IdentifierV3 cursor,IdentifierV3 forkRun,IdentifierV3 joinStep,Map<IdentifierV3,IdentifierV3> branches,Map<IdentifierV3,String> outcomes,String releasedOutcome,long startedAt){
        public GroupData {branches=Map.copyOf(branches);outcomes=Map.copyOf(outcomes);}
        public GroupData result(IdentifierV3 id,String value){var next=new LinkedHashMap<>(outcomes);next.put(id,value);return new GroupData(activation,cursor,forkRun,joinStep,branches,next,releasedOutcome,startedAt);}
        public GroupData release(String outcome){return new GroupData(activation,cursor,forkRun,joinStep,branches,outcomes,outcome,startedAt);}
    }
    public record PublicationData(IdentifierV3 activation,IdentifierV3 cursor,IdentifierV3 run,IdentifierV3 resource,IdentifierV3 message,ExecutionPlanV3.AwaitPublish awaitMode,IdentifierV3 interaction,IdentifierV3 attempt,List<IdentifierV3> entries,Set<IdentifierV3> acknowledged){
        public PublicationData {entries=List.copyOf(entries);acknowledged=Set.copyOf(acknowledged);}
        public PublicationData entries(List<IdentifierV3> values){return new PublicationData(activation,cursor,run,resource,message,awaitMode,interaction,attempt,values,acknowledged);}
        public PublicationData ack(IdentifierV3 value){var values=new HashSet<>(acknowledged);values.add(value);return new PublicationData(activation,cursor,run,resource,message,awaitMode,interaction,attempt,entries,values);}
    }
    public record DeliveryData(IdentifierV3 queue,IdentifierV3 publication,IdentifierV3 message,long ordinal,long attempts,IdentifierV3 lastDelivery,IdentifierV3 interaction,long readyAt,boolean enqueued){
        public DeliveryData markEnqueued(){return new DeliveryData(queue,publication,message,ordinal,attempts,lastDelivery,interaction,readyAt,true);}
        public DeliveryData attempt(IdentifierV3 delivery,IdentifierV3 call){return new DeliveryData(queue,publication,message,ordinal,attempts+1,delivery,call,readyAt,enqueued);}
        public DeliveryData ready(long at){return new DeliveryData(queue,publication,message,ordinal,attempts,lastDelivery,interaction,at,enqueued);}
    }
    public static final class Builder {
        final String prefix;long nextId;final Map<IdentifierV3,ActivationData> activations;final Map<IdentifierV3,Run> runs;final Map<IdentifierV3,CallData> calls;final Map<IdentifierV3,CallData> attemptCalls;final Map<IdentifierV3,GroupData> groups;final Map<IdentifierV3,BranchData> branches;final Map<IdentifierV3,PublicationData> publications;final Map<IdentifierV3,DeliveryData> deliveries;final Map<String,Long> counters;final Map<IdentifierV3,Long> processing,communication;
        Long principalTerminalAt,principalResponseAt;String principalOutcome,globalStop;
        public Builder(InterpreterStateV3 s){prefix=s.prefix;nextId=s.nextId;activations=new LinkedHashMap<>(s.activations);runs=new LinkedHashMap<>(s.runs);calls=new LinkedHashMap<>(s.calls);attemptCalls=new LinkedHashMap<>(s.attemptCalls);groups=new LinkedHashMap<>(s.groups);branches=new LinkedHashMap<>(s.branches);publications=new LinkedHashMap<>(s.publications);deliveries=new LinkedHashMap<>(s.deliveries);counters=new LinkedHashMap<>(s.counters);processing=new LinkedHashMap<>(s.processing);communication=new LinkedHashMap<>(s.communication);principalTerminalAt=s.principalTerminalAt;principalResponseAt=s.principalResponseAt;principalOutcome=s.principalOutcome;globalStop=s.globalStop;}
        void call(IdentifierV3 interaction,CallData data){calls.put(interaction,data);attemptCalls.put(data.attempt(),data);}
        IdentifierV3 id(String kind){var id=new IdentifierV3(prefix+kind+"-"+nextId);nextId=Math.addExact(nextId,1);return id;}
        long increment(String key){long value=Math.addExact(counters.getOrDefault(key,0L),1);counters.put(key,value);return value;}
        void add(Map<IdentifierV3,Long> target,IdentifierV3 key,long duration){target.put(key,Math.addExact(target.getOrDefault(key,0L),duration));}
        InterpreterStateV3 freeze(){return new InterpreterStateV3(prefix,nextId,activations,runs,calls,attemptCalls,groups,branches,publications,deliveries,counters,processing,communication,principalTerminalAt,principalResponseAt,principalOutcome,globalStop);}
    }
}
