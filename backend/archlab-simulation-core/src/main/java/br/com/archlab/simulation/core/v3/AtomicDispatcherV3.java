package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.EffectBatchV3.*;
/** The only publication point is a volatile immutable snapshot after complete validation. */
public final class AtomicDispatcherV3 {
    @FunctionalInterface public interface Handler {DispatchDecisionV3 decide(DeterministicSchedulerV3.Event<SimulationEventV3> event,ReadView view);}
    public record ReadView(IdentifierV3 executionId,RuntimeStateV3 state,long nowMs,long randomState,long dispatchSequence,List<DeterministicSchedulerV3.Event<SimulationEventV3>> pending) {public ReadView {pending=List.copyOf(pending);}}
    public record EntityDelta(IdentifierV3 deltaId,long sequence,long timestampMs,IdentifierV3 sourceTraceRecordId,RuntimeEntityV3 before,RuntimeEntityV3 after) {
        public EntityDelta {Objects.requireNonNull(deltaId);Objects.requireNonNull(sourceTraceRecordId);if(sequence<0 || timestampMs<0 || before==null && after==null)throw new IllegalArgumentException("Invalid entity delta");
            if(before!=null && after!=null && (!before.id().equals(after.id()) || before.kind()!=after.kind() || after.version()!=Math.addExact(before.version(),1)) || before==null && after.version()!=0)throw new IllegalArgumentException("Invalid delta identity/version");}
        public Optional<StateDeltaV3> publicProjection(){var e=after==null?before:after;if(!StateDeltaV3.publicKind(e.kind()))return Optional.empty();
            return Optional.of(new StateDeltaV3(deltaId,sequence,timestampMs,sourceTraceRecordId,e.kind(),e.id(),before==null?StateDeltaV3.Operation.CREATE:after==null?StateDeltaV3.Operation.REMOVE:StateDeltaV3.Operation.UPDATE,
                    before==null?null:before.version(),after==null?null:after.version(),before,after));}
    }
    public record CommitRecord(long dispatchSequence,DeterministicSchedulerV3.Event<SimulationEventV3> event,DispatchDecisionV3 decision,List<EntityDelta> deltas,List<TraceRecordV3> trace) {
        public CommitRecord {Objects.requireNonNull(event);Objects.requireNonNull(decision);deltas=List.copyOf(deltas);trace=List.copyOf(trace);}
    }
    public record Snapshot(IdentifierV3 executionId,RuntimeStateV3 state,DeterministicSchedulerV3.Snapshot<SimulationEventV3> agenda,long randomState,
                           long nextDispatchSequence,long nextTraceSequence,long nextDeltaSequence,Map<IdentifierV3,Claim> claims,List<TraceRecordV3> trace,List<CommitRecord> journal) {
        public Snapshot {Objects.requireNonNull(executionId);Objects.requireNonNull(state);Objects.requireNonNull(agenda);claims=Map.copyOf(claims);trace=List.copyOf(trace);journal=List.copyOf(journal);
            if(nextDispatchSequence<0 || nextTraceSequence<0 || nextDeltaSequence<0)throw new IllegalArgumentException("Negative counters");}
    }
    private final Map<SimulationEventV3.Type,Handler> handlers;
    private volatile Snapshot published;
    private boolean dispatching;
    public AtomicDispatcherV3(IdentifierV3 executionId,RuntimeStateV3 state,DeterministicSchedulerV3.Snapshot<SimulationEventV3> agenda,long seed,Map<SimulationEventV3.Type,Handler> handlers){
        this(new Snapshot(executionId,state,agenda,seed,0,0,0,Map.of(),List.of(),List.of()),handlers);}
    public AtomicDispatcherV3(Snapshot snapshot,Map<SimulationEventV3.Type,Handler> handlers){validateSnapshot(snapshot);this.handlers=Map.copyOf(handlers);this.published=snapshot;}
    public Snapshot snapshot(){return published;}
    public synchronized Optional<CommitRecord> dispatchNext(){return dispatch(null);}
    public synchronized CommitRecord applyRecorded(CommitRecord expected){return dispatch(Objects.requireNonNull(expected)).orElseThrow();}
    private Optional<CommitRecord> dispatch(CommitRecord expected){
        if(dispatching)throw new IllegalStateException("Reentrant dispatch");dispatching=true;
        try{
            var initial=published;var agenda=DeterministicSchedulerV3.restore(initial.agenda());var optional=agenda.poll();if(optional.isEmpty())return Optional.empty();var event=optional.get();
            DispatchDecisionV3 decision;
            if(expected!=null){if(expected.dispatchSequence()!=initial.nextDispatchSequence() || !expected.event().equals(event))throw new IllegalStateException("Journal event/sequence mismatch");decision=expected.decision();}
            else {var handler=handlers.get(event.payload().eventType());if(handler==null)throw new IllegalStateException("No handler for "+event.payload().eventType());
                decision=Objects.requireNonNull(handler.decide(event,new ReadView(initial.executionId(),initial.state(),event.scheduledAtMs(),initial.randomState(),initial.nextDispatchSequence(),agenda.snapshot().pending())));}
            if(decision instanceof DispatchDecisionV3.Apply apply){String stale=staleReason(initial,apply.batch());if(stale!=null)decision=new DispatchDecisionV3.Ignore(stale);}
            var state=initial.state();long random=initial.randomState(),traceSequence=initial.nextTraceSequence(),deltaSequence=initial.nextDeltaSequence();
            var claims=new LinkedHashMap<>(initial.claims());var traces=new ArrayList<TraceRecordV3>();var deltas=new ArrayList<EntityDelta>();
            traces.add(new TraceRecordV3(new IdentifierV3("trace-"+traceSequence),traceSequence,event.scheduledAtMs(),TraceRecordV3.Kind.SCHEDULER_POSITION_CHANGED,initial.executionId(),TraceRecordV3.Context.source(event.eventId()),
                    new TraceRecordV3.SchedulerPosition(TraceRecordV3.SchedulerPositionStage.valueOf(event.stage().name()),event.round(),initial.nextDispatchSequence())));traceSequence=Math.addExact(traceSequence,1);
            if(decision instanceof DispatchDecisionV3.Apply apply)for(var effect:apply.batch().effects()){
                RuntimeEntityV3 before=null,after=null;
                switch(effect){
                    case Create create -> {after=create.entity();var createdId=after.id();
                        boolean used=initial.journal().stream().flatMap(r->r.deltas().stream()).anyMatch(d->(d.after()==null?d.before():d.after()).id().equals(createdId))
                                || deltas.stream().anyMatch(d->(d.after()==null?d.before():d.after()).id().equals(createdId));
                        if(used)throw new IllegalStateException("Entity ID cannot be reused");state=state.create(after);}
                    case Replace replace -> {after=replace.entity();before=state.entities().get(after.id());state=state.replace(replace.expectedVersion(),after);}
                    case Transition transition -> {before=state.entities().get(transition.id());state=state.transition(transition.id(),transition.expectedVersion(),transition.target());after=state.entities().get(transition.id());}
                    case Remove remove -> {before=state.entities().get(remove.id());if(before!=null && before.stateName()!=null && before.kind()!=RuntimeEntityV3.Kind.COMPONENT && before.kind()!=RuntimeEntityV3.Kind.RESOURCE && !StateTransitionsV3.terminal(before.kind(),before.stateName()))throw new IllegalStateException("Cannot remove active entity");state=state.remove(remove.id(),remove.expectedVersion());}
                    case Schedule schedule -> agenda.schedule(schedule.atMs(),schedule.phase(),schedule.payload());
                    case TakeClaim take -> {var old=claims.putIfAbsent(take.claim().key(),take.claim());if(old!=null && !old.equals(take.claim()))throw new IllegalStateException("Conflicting claim");}
                    case AdvanceRandom advance -> {if(random!=advance.expectedState())throw new IllegalStateException("Stale random state");random=advance.nextState();}
                    case Emit emit -> {var context=emit.context()==null?TraceRecordV3.Context.source(event.eventId()):emit.context().withSource(event.eventId());
                        traces.add(new TraceRecordV3(new IdentifierV3("trace-"+traceSequence),traceSequence,event.scheduledAtMs(),emit.kind(),initial.executionId(),context,emit.detail()));traceSequence=Math.addExact(traceSequence,1);}
                }
                if(before!=null || after!=null){var delta=new EntityDelta(new IdentifierV3("delta-"+deltaSequence),deltaSequence,event.scheduledAtMs(),traces.getLast().recordId(),before,after);
                    delta.publicProjection();deltas.add(delta);deltaSequence=Math.addExact(deltaSequence,1);}
            }
            state.requireConsistent();
            var record=new CommitRecord(initial.nextDispatchSequence(),event,decision,deltas,traces);
            if(expected!=null && !expected.equals(record))throw new IllegalStateException("Journal replay differs");
            var allTrace=new ArrayList<>(initial.trace());allTrace.addAll(traces);var journal=new ArrayList<>(initial.journal());journal.add(record);
            var next=new Snapshot(initial.executionId(),state,agenda.snapshot(),random,Math.addExact(initial.nextDispatchSequence(),1),traceSequence,deltaSequence,claims,allTrace,journal);
            published=next;return Optional.of(record);
        } finally {dispatching=false;}
    }
    private static String staleReason(Snapshot state,EffectBatchV3 batch){
        for(var guard:batch.guards())switch(guard){
            case EntityVersion version -> {var e=state.state().entities().get(version.id());if(e==null || e.version()!=version.expectedVersion())return "STALE_ENTITY_VERSION";}
            case EntityState expected -> {var e=state.state().entities().get(expected.id());if(e==null || !Objects.equals(e.stateName(),expected.expectedState()))return "STALE_ENTITY_STATE";}
            case ClaimAbsent claim -> {if(state.claims().containsKey(claim.key()))return "CLAIM_ALREADY_TAKEN";}
        }
        for(var effect:batch.effects())if(effect instanceof TakeClaim take){var old=state.claims().get(take.claim().key());if(old!=null){if(old.equals(take.claim()))return "CLAIM_ALREADY_TAKEN";throw new IllegalStateException("Conflicting claim owner/purpose");}}
        return null;
    }
    private static void validateSnapshot(Snapshot snapshot){
        snapshot.state().requireConsistent();DeterministicSchedulerV3.restore(snapshot.agenda());
        if(snapshot.nextDispatchSequence()!=snapshot.journal().size() || snapshot.nextTraceSequence()!=snapshot.trace().size())throw new IllegalArgumentException("Snapshot counters/history mismatch");
        long deltaSequence=0;var trace=new ArrayList<TraceRecordV3>();var eventIds=new HashSet<IdentifierV3>();var latest=new HashMap<IdentifierV3,RuntimeEntityV3>();
        for(int i=0;i<snapshot.journal().size();i++){var commit=snapshot.journal().get(i);if(commit.dispatchSequence()!=i || !eventIds.add(commit.event().eventId()))throw new IllegalArgumentException("Invalid dispatch history");
            trace.addAll(commit.trace());var traceIds=new HashSet<IdentifierV3>();for(var fact:commit.trace()){if(!commit.event().eventId().equals(fact.context().sourceEventId()))throw new IllegalArgumentException("Trace/event mismatch");traceIds.add(fact.recordId());}
            for(var delta:commit.deltas()){if(delta.sequence()!=deltaSequence || !delta.deltaId().value().equals("delta-"+deltaSequence) || delta.timestampMs()!=commit.event().scheduledAtMs() || !traceIds.contains(delta.sourceTraceRecordId()))throw new IllegalArgumentException("Invalid delta history");
                var entity=delta.after()==null?delta.before():delta.after();if(latest.containsKey(entity.id()) && (delta.before()==null || !Objects.equals(latest.get(entity.id()),delta.before())))throw new IllegalArgumentException("Delta before mismatch or reused ID");latest.put(entity.id(),delta.after());deltaSequence++;}}
        if(deltaSequence!=snapshot.nextDeltaSequence() || !trace.equals(snapshot.trace()))throw new IllegalArgumentException("Snapshot journal mismatch");
        long time=0;for(int i=0;i<trace.size();i++){var fact=trace.get(i);if(fact.sequence()!=i || !fact.recordId().value().equals("trace-"+i) || !fact.executionId().equals(snapshot.executionId()) || fact.timestampMs()<time || fact.timestampMs()>snapshot.agenda().nowMs())throw new IllegalArgumentException("Invalid trace history");time=fact.timestampMs();}
        if(!snapshot.journal().isEmpty() && !snapshot.journal().getLast().event().equals(snapshot.agenda().lastDispatched()))throw new IllegalArgumentException("Dispatch/agenda mismatch");
        for(var entry:snapshot.claims().entrySet())if(!entry.getKey().equals(entry.getValue().key()))throw new IllegalArgumentException("Claim key mismatch");
        for(var entry:latest.entrySet())if(!Objects.equals(snapshot.state().entities().get(entry.getKey()),entry.getValue()))throw new IllegalArgumentException("State/journal mismatch");
    }
    public static Snapshot replay(Snapshot initial,List<CommitRecord> records){var dispatcher=new AtomicDispatcherV3(initial,Map.of());for(var record:records)dispatcher.applyRecorded(record);return dispatcher.snapshot();}
}
