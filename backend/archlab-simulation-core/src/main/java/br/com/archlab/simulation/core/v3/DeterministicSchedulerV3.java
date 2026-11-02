package br.com.archlab.simulation.core.v3;
import java.util.*;
import java.util.function.Consumer;
/** Single-threaded virtual agenda. Payloads must be immutable domain values. */
public final class DeterministicSchedulerV3<T> {
    public enum Phase {
        ENVIRONMENT_CHANGE(0),DELIVERY_OR_COMPLETION(10),CONTINUATION(20),DEADLINE(30),TERMINATION_CHECK(40);
        private final int value; Phase(int value){this.value=value;} public int value(){return value;}
    }
    public enum Epoch { BEFORE_DEADLINE,AFTER_DEADLINE }
    public enum Stage { ENVIRONMENT,BEFORE_DEADLINES,DEADLINES,AFTER_DEADLINES,TERMINATION }
    public record Event<T>(IdentifierV3 eventId,long scheduledAtMs,Phase phase,long sequence,Epoch causalEpoch,
                           Stage stage,long round,IdentifierV3 causationEventId,T payload) {
        public Event {Objects.requireNonNull(eventId);Objects.requireNonNull(phase);Objects.requireNonNull(causalEpoch);Objects.requireNonNull(stage);Objects.requireNonNull(payload);
            if(scheduledAtMs<0 || sequence<0 || round<0)throw new IllegalArgumentException("Negative scheduler position");
            boolean legal=switch(stage){
                case ENVIRONMENT -> phase==Phase.ENVIRONMENT_CHANGE && causalEpoch==Epoch.BEFORE_DEADLINE;
                case BEFORE_DEADLINES -> (phase==Phase.DELIVERY_OR_COMPLETION || phase==Phase.CONTINUATION) && causalEpoch==Epoch.BEFORE_DEADLINE;
                case DEADLINES -> phase==Phase.DEADLINE && causalEpoch==Epoch.BEFORE_DEADLINE;
                case AFTER_DEADLINES -> (phase==Phase.DELIVERY_OR_COMPLETION || phase==Phase.CONTINUATION || phase==Phase.DEADLINE) && causalEpoch==Epoch.AFTER_DEADLINE;
                case TERMINATION -> phase==Phase.TERMINATION_CHECK;
            };
            if(!legal)throw new IllegalArgumentException("Inconsistent phase/stage/epoch");
        }
    }
    public record Snapshot<T>(long nowMs,long nextSequence,Event<T> lastDispatched,List<Event<T>> pending) {
        public Snapshot {pending=List.copyOf(pending);if(nowMs<0 || nextSequence<0)throw new IllegalArgumentException("Negative scheduler state");}
    }
    public enum Stop { DRAINED,HORIZON,EVENT_LIMIT }
    public record RunResult(Stop stop,long dispatched,long nowMs) { }
    private final PriorityQueue<Event<T>> agenda=new PriorityQueue<>(DeterministicSchedulerV3::compare);
    private long nowMs,nextSequence;
    private Event<T> lastDispatched;
    public long nowMs(){return nowMs;}
    public Optional<Event<T>> peek(){return Optional.ofNullable(agenda.peek());}
    public int pendingCount(){return agenda.size();}
    public Event<T> schedule(long atMs,Phase phase,T payload){
        Objects.requireNonNull(phase);Objects.requireNonNull(payload);
        if(atMs<nowMs)throw new IllegalArgumentException("Cannot schedule into the past");
        long next=Math.addExact(nextSequence,1);Stage stage=baseStage(phase);Epoch epoch=Epoch.BEFORE_DEADLINE;long round=0;
        if(lastDispatched!=null && atMs==lastDispatched.scheduledAtMs()){
            var cause=lastDispatched;
            if(phase==Phase.ENVIRONMENT_CHANGE && cause.stage()!=Stage.ENVIRONMENT)throw new IllegalArgumentException("Environment barrier already passed");
            if(cause.stage()==Stage.TERMINATION && phase!=Phase.TERMINATION_CHECK)throw new IllegalArgumentException("Termination barrier already passed");
            if(cause.stage()==Stage.DEADLINES || cause.stage()==Stage.AFTER_DEADLINES || cause.causalEpoch()==Epoch.AFTER_DEADLINE){
                epoch=Epoch.AFTER_DEADLINE;
                if(phase!=Phase.TERMINATION_CHECK && phase!=Phase.ENVIRONMENT_CHANGE)
                    stage=cause.stage()==Stage.DEADLINES && phase==Phase.DEADLINE?Stage.DEADLINES:Stage.AFTER_DEADLINES;
                // Deadlines created by a deadline remain in the deadline barrier, then release their children.
                if(stage==Stage.DEADLINES)epoch=Epoch.BEFORE_DEADLINE;
            }
            if(stage==cause.stage())round=phase.value()<cause.phase().value()?Math.addExact(cause.round(),1):cause.round();
        }
        var event=new Event<>(new IdentifierV3("event-"+nextSequence),atMs,phase,nextSequence,epoch,stage,round,
                lastDispatched==null?null:lastDispatched.eventId(),payload);
        if(lastDispatched!=null && compare(event,lastDispatched)<0)throw new IllegalArgumentException("Causal scheduler regression");
        agenda.add(event);nextSequence=next;return event;
    }
    public Optional<Event<T>> poll(){var event=agenda.poll();if(event==null)return Optional.empty();
        if(event.scheduledAtMs()<nowMs)throw new IllegalStateException("Corrupt agenda");
        nowMs=event.scheduledAtMs();lastDispatched=event;return Optional.of(event);}
    public RunResult run(long maxEvents,Long horizonAtMs,Consumer<Event<T>> handler){
        if(maxEvents<1)throw new IllegalArgumentException("Positive event limit required");
        if(horizonAtMs!=null && horizonAtMs<nowMs)throw new IllegalArgumentException("Horizon precedes clock");
        Objects.requireNonNull(handler);long count=0;
        while(!agenda.isEmpty() && count<maxEvents){
            if(horizonAtMs!=null && agenda.peek().scheduledAtMs()>horizonAtMs){nowMs=horizonAtMs;return new RunResult(Stop.HORIZON,count,nowMs);}
            handler.accept(poll().orElseThrow());count++;
        }
        return new RunResult(agenda.isEmpty()?Stop.DRAINED:Stop.EVENT_LIMIT,count,nowMs);
    }
    public Snapshot<T> snapshot(){var pending=new ArrayList<>(agenda);pending.sort(DeterministicSchedulerV3::compare);return new Snapshot<>(nowMs,nextSequence,lastDispatched,pending);}
    public static <T> DeterministicSchedulerV3<T> restore(Snapshot<T> snapshot){
        Objects.requireNonNull(snapshot);var scheduler=new DeterministicSchedulerV3<T>();var sequences=new HashSet<Long>();var ids=new HashSet<IdentifierV3>();
        var last=snapshot.lastDispatched();
        if(last!=null && (last.scheduledAtMs()>snapshot.nowMs() || last.sequence()>=snapshot.nextSequence()
                || !last.eventId().value().equals("event-"+last.sequence())))throw new IllegalArgumentException("Invalid dispatched position");
        for(var e:snapshot.pending()){
            if(e.scheduledAtMs()<snapshot.nowMs() || e.sequence()>=snapshot.nextSequence() || !sequences.add(e.sequence()) || !ids.add(e.eventId())
                    || !e.eventId().value().equals("event-"+e.sequence()) || last!=null && (e.sequence()==last.sequence() || compare(e,last)<0))
                throw new IllegalArgumentException("Invalid agenda snapshot");
            scheduler.agenda.add(e);
        }
        scheduler.nowMs=snapshot.nowMs();scheduler.nextSequence=snapshot.nextSequence();scheduler.lastDispatched=last;return scheduler;
    }
    private static Stage baseStage(Phase phase){return switch(phase){case ENVIRONMENT_CHANGE->Stage.ENVIRONMENT;case DELIVERY_OR_COMPLETION,CONTINUATION->Stage.BEFORE_DEADLINES;case DEADLINE->Stage.DEADLINES;case TERMINATION_CHECK->Stage.TERMINATION;};}
    private static int compare(Event<?> a,Event<?> b){
        int c=Long.compare(a.scheduledAtMs(),b.scheduledAtMs());if(c!=0)return c;
        c=a.stage().compareTo(b.stage());if(c!=0)return c;c=Long.compare(a.round(),b.round());if(c!=0)return c;
        c=Integer.compare(a.phase().value(),b.phase().value());return c!=0?c:Long.compare(a.sequence(),b.sequence());
    }
}
