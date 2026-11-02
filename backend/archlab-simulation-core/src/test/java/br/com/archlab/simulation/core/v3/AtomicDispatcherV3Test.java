package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.archlab.simulation.core.v3.EffectBatchV3.*;
import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.*;
class AtomicDispatcherV3Test {
    static IdentifierV3 id(String x){return new IdentifierV3(x);}
    static RuntimeStateV3 initialState(){return RuntimeStateV3.empty().create(new Component(id("c"),0,ComponentStatus.AVAILABLE)).create(new Activation(id("a"),id("c"),ActivationStatus.READY,null,0));}
    static AtomicDispatcherV3 dispatcher(AtomicDispatcherV3.Handler handler){var scheduler=new DeterministicSchedulerV3<SimulationEventV3>();scheduler.schedule(0,DeterministicSchedulerV3.Phase.CONTINUATION,new SimulationEventV3.ExecutionStartDue(id("root")));
        return new AtomicDispatcherV3(id("execution"),initialState(),scheduler.snapshot(),42,Map.of(SimulationEventV3.Type.EXECUTION_START_DUE,handler));}
    static DispatchDecisionV3 apply(Effect... effects){return new DispatchDecisionV3.Apply(EffectBatchV3.of(effects));}
    @Test void commitsStateAgendaClaimsRandomAndTraceInOnePublicationAndReplaysExactly(){
        var random=new DeterministicRandomV3(42);random.nextLong();long nextRandom=random.state();
        var dispatcher=dispatcher((event,view)->apply(new TakeClaim(new Claim(id("token"),id("a"),"completion")),new Transition(id("a"),0,"RUNNING"),
                new AdvanceRandom(42,nextRandom),new Schedule(5,DeterministicSchedulerV3.Phase.CONTINUATION,new SimulationEventV3.ExecutionStartDue(id("root"))),
                new Emit(TraceRecordV3.Kind.EXECUTION_STARTED,null,new TraceRecordV3.Execution("STARTED",null))));
        var initial=dispatcher.snapshot();var record=dispatcher.dispatchNext().orElseThrow();var after=dispatcher.snapshot();
        assertNotSame(initial,after);assertEquals("READY",initial.state().entities().get(id("a")).stateName());assertEquals("RUNNING",after.state().entities().get(id("a")).stateName());
        assertEquals(1,after.agenda().pending().size());assertTrue(initial.claims().isEmpty());assertEquals(1,after.claims().size());assertEquals(nextRandom,after.randomState());
        assertEquals(2,after.trace().size());assertEquals(1,record.deltas().size());assertTrue(record.deltas().getFirst().publicProjection().isPresent());
        assertEquals(after.trace().getFirst().recordId(),record.deltas().getFirst().sourceTraceRecordId());
        assertEquals(after,AtomicDispatcherV3.replay(initial,after.journal()));
        var resumed=new AtomicDispatcherV3(after,Map.of(SimulationEventV3.Type.EXECUTION_START_DUE,(e,v)->apply(new Transition(id("a"),1,"COMPLETED"))));
        resumed.dispatchNext();assertEquals("COMPLETED",resumed.snapshot().state().entities().get(id("a")).stateName());
    }
    @Test void invalidLateEffectRollsBackEveryCandidateAndLeavesEventPending(){
        var dispatcher=dispatcher((e,v)->apply(new TakeClaim(new Claim(id("token"),id("a"),"once")),new AdvanceRandom(42,123),
                new Schedule(1,DeterministicSchedulerV3.Phase.CONTINUATION,new SimulationEventV3.ActivationStartDue(id("a"))),new Transition(id("a"),0,"COMPLETED")));
        var initial=dispatcher.snapshot();assertThrows(IllegalStateException.class,dispatcher::dispatchNext);assertSame(initial,dispatcher.snapshot());
        assertEquals(1,dispatcher.snapshot().agenda().pending().size());assertTrue(dispatcher.snapshot().claims().isEmpty());assertTrue(dispatcher.snapshot().trace().isEmpty());assertEquals(42,dispatcher.snapshot().randomState());
    }
    @Test void finalInvariantFailureRollsBackPreviouslyValidEffects(){
        var dispatcher=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING"),new Create(new Resource(id("r"),"AVAILABLE",1L,1L,0))));
        var initial=dispatcher.snapshot();assertThrows(IllegalStateException.class,dispatcher::dispatchNext);assertSame(initial,dispatcher.snapshot());
    }
    @Test void staleVersionGuardConsumesEventWithoutApplyingEffects(){
        var dispatcher=dispatcher((e,v)->new DispatchDecisionV3.Apply(new EffectBatchV3(List.of(new EntityVersion(id("a"),99)),List.of(new Transition(id("a"),0,"RUNNING")))));
        var state=dispatcher.snapshot().state();var record=dispatcher.dispatchNext().orElseThrow();assertInstanceOf(DispatchDecisionV3.Ignore.class,record.decision());
        assertSame(state,dispatcher.snapshot().state());assertTrue(record.deltas().isEmpty());assertEquals(1,record.trace().size());assertTrue(dispatcher.snapshot().agenda().pending().isEmpty());
    }
    @Test void sameClaimSuppressesDuplicateBatchAndConflictingClaimFailsAtomically(){
        var dispatcher=dispatcher((e,v)->apply(new TakeClaim(new Claim(id("token"),id("a"),"once")),new Transition(id("a"),0,"RUNNING"),
                new Schedule(1,DeterministicSchedulerV3.Phase.CONTINUATION,new SimulationEventV3.ExecutionStartDue(id("root")))));
        dispatcher.dispatchNext();var afterFirst=dispatcher.snapshot();var duplicate=dispatcher.dispatchNext().orElseThrow();assertInstanceOf(DispatchDecisionV3.Ignore.class,duplicate.decision());
        assertEquals(afterFirst.state(),dispatcher.snapshot().state());assertEquals(1,dispatcher.snapshot().claims().size());assertTrue(dispatcher.snapshot().agenda().pending().isEmpty());
        var conflict=new AtomicDispatcherV3(afterFirst,Map.of(SimulationEventV3.Type.EXECUTION_START_DUE,(e,v)->apply(new TakeClaim(new Claim(id("token"),id("other"),"once")))));
        var before=conflict.snapshot();assertThrows(IllegalStateException.class,conflict::dispatchNext);assertSame(before,conflict.snapshot());
    }
    @Test void handlerFailureMissingHandlerAndReentrancyNeverPublish(){
        var failure=dispatcher((e,v)->{throw new IllegalArgumentException("dispatch failed");});var initial=failure.snapshot();assertThrows(IllegalArgumentException.class,failure::dispatchNext);assertSame(initial,failure.snapshot());
        var missing=new AtomicDispatcherV3(initial,Map.of());assertThrows(IllegalStateException.class,missing::dispatchNext);assertEquals(initial,missing.snapshot());
        var holder=new AtomicDispatcherV3[1];holder[0]=dispatcher((e,v)->{holder[0].dispatchNext();return apply();});var before=holder[0].snapshot();assertThrows(IllegalStateException.class,holder[0]::dispatchNext);assertSame(before,holder[0].snapshot());
    }
    @Test void malformedTraceAndCausalSchedulingFailuresRollBack(){
        var invalidTrace=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING"),new Emit(TraceRecordV3.Kind.EXECUTION_STARTED,null,new TraceRecordV3.Execution("ROOT_STARTED",null))));
        var before=invalidTrace.snapshot();assertThrows(IllegalArgumentException.class,invalidTrace::dispatchNext);assertSame(before,invalidTrace.snapshot());
        var invalidPhase=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING"),new Schedule(0,DeterministicSchedulerV3.Phase.ENVIRONMENT_CHANGE,new SimulationEventV3.ActivationStartDue(id("a")))));
        before=invalidPhase.snapshot();assertThrows(IllegalArgumentException.class,invalidPhase::dispatchNext);assertSame(before,invalidPhase.snapshot());
    }
    @Test void replayRejectsWrongEventOrForgedDeltaBeforePublication(){
        var dispatcher=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING")));var initial=dispatcher.snapshot();var record=dispatcher.dispatchNext().orElseThrow();
        var wrong=new AtomicDispatcherV3.CommitRecord(1,record.event(),record.decision(),record.deltas(),record.trace());var replay=new AtomicDispatcherV3(initial,Map.of());
        assertThrows(IllegalStateException.class,()->replay.applyRecorded(wrong));assertEquals(initial,replay.snapshot());
        var forged=new AtomicDispatcherV3.CommitRecord(0,record.event(),record.decision(),List.of(),record.trace());assertThrows(IllegalStateException.class,()->replay.applyRecorded(forged));assertEquals(initial,replay.snapshot());
    }
    @Test void removesOnlyUnreferencedEntitiesAndKeepsPublicDeltaVersionsExact(){
        var dispatcher=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING"),new Transition(id("a"),1,"COMPLETED"),new Remove(id("a"),2)));
        var record=dispatcher.dispatchNext().orElseThrow();assertFalse(dispatcher.snapshot().state().entities().containsKey(id("a")));assertEquals(3,record.deltas().size());
        var removal=record.deltas().getLast().publicProjection().orElseThrow();assertEquals(StateDeltaV3.Operation.REMOVE,removal.operation());assertNull(removal.nextVersion());assertEquals(2L,removal.expectedVersion());
        var active=dispatcher((e,v)->apply(new Remove(id("a"),0)));var before=active.snapshot();assertThrows(IllegalStateException.class,active::dispatchNext);assertSame(before,active.snapshot());
        var referenced=dispatcher((e,v)->apply(new Remove(id("c"),0)));before=referenced.snapshot();assertThrows(IllegalStateException.class,referenced::dispatchNext);assertSame(before,referenced.snapshot());
    }
    @Test void rejectsCorruptSnapshotCountersAndPublicDeltaTypes(){
        var dispatcher=dispatcher((e,v)->apply());var snapshot=dispatcher.snapshot();
        var corrupt=new AtomicDispatcherV3.Snapshot(snapshot.executionId(),snapshot.state(),snapshot.agenda(),42,1,0,0,Map.of(),List.of(),List.of());assertThrows(IllegalArgumentException.class,()->new AtomicDispatcherV3(corrupt,Map.of()));
        assertThrows(IllegalArgumentException.class,()->new StateDeltaV3(id("d"),0,0,id("t"),Kind.COMPONENT,id("c"),StateDeltaV3.Operation.CREATE,null,0L,null,new Component(id("c"),0,ComponentStatus.AVAILABLE)));
        var a=new Activation(id("a"),id("c"),ActivationStatus.READY,null,0);assertThrows(IllegalArgumentException.class,()->new StateDeltaV3(id("d"),0,0,id("t"),Kind.ACTIVATION,id("a"),StateDeltaV3.Operation.UPDATE,0L,0L,a,a));
        assertThrows(IllegalArgumentException.class,()->new TraceRecordV3.StateChange("ACTIVATION",id("a"),"","RUNNING","reason",0,1));
    }
    @Test void restoreRejectsStateThatDisagreesWithCommittedJournal(){
        var dispatcher=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING")));dispatcher.dispatchNext();var saved=dispatcher.snapshot();
        var changed=saved.state().transition(id("a"),1,"WAITING");var corrupt=new AtomicDispatcherV3.Snapshot(saved.executionId(),changed,saved.agenda(),saved.randomState(),saved.nextDispatchSequence(),saved.nextTraceSequence(),saved.nextDeltaSequence(),saved.claims(),saved.trace(),saved.journal());
        assertThrows(IllegalArgumentException.class,()->new AtomicDispatcherV3(corrupt,Map.of()));
    }
    @Test void orderedCreateReplaceAndWorkResolutionProduceCorrectPublicAndInternalDeltas(){
        var dispatcher=dispatcher((e,v)->apply(new Create(new Resource(id("r"),"AVAILABLE",1L,0L,0)),new Replace(0,new Resource(id("r"),"EXHAUSTED",1L,0L,1)),
                new Create(new WorkItem(id("w"),0,id("a"),"BACKGROUND",WorkItemStatus.PENDING,"GLOBAL")),new Transition(id("w"),0,"RESOLVED")));
        var record=dispatcher.dispatchNext().orElseThrow();assertEquals(4,record.deltas().size());
        assertEquals(StateDeltaV3.Operation.CREATE,record.deltas().getFirst().publicProjection().orElseThrow().operation());assertEquals(0L,record.deltas().getFirst().publicProjection().orElseThrow().nextVersion());
        assertEquals(StateDeltaV3.Operation.UPDATE,record.deltas().get(1).publicProjection().orElseThrow().operation());assertTrue(record.deltas().getLast().publicProjection().isEmpty());assertEquals("RESOLVED",dispatcher.snapshot().state().entities().get(id("w")).stateName());
    }
    @Test void rejectsEntityIdReuseAfterRemovalBeforePublishing(){
        var dispatcher=dispatcher((e,v)->apply(new Transition(id("a"),0,"RUNNING"),new Transition(id("a"),1,"COMPLETED"),new Remove(id("a"),2),new Create(new Activation(id("a"),id("c"),ActivationStatus.READY,null,0))));
        var before=dispatcher.snapshot();assertThrows(IllegalStateException.class,dispatcher::dispatchNext);assertSame(before,dispatcher.snapshot());
    }
    @Test void stateAndClaimGuardsProtectOtherwiseValidEffects(){
        var stale=dispatcher((e,v)->new DispatchDecisionV3.Apply(new EffectBatchV3(List.of(new EntityState(id("a"),"WAITING")),List.of(new Transition(id("a"),0,"RUNNING")))));
        assertInstanceOf(DispatchDecisionV3.Ignore.class,stale.dispatchNext().orElseThrow().decision());assertEquals("READY",stale.snapshot().state().entities().get(id("a")).stateName());
        var first=dispatcher((e,v)->apply(new TakeClaim(new Claim(id("key"),id("a"),"once")),new Schedule(1,DeterministicSchedulerV3.Phase.CONTINUATION,new SimulationEventV3.ExecutionStartDue(id("root")))));first.dispatchNext();
        var next=new AtomicDispatcherV3(first.snapshot(),Map.of(SimulationEventV3.Type.EXECUTION_START_DUE,(e,v)->new DispatchDecisionV3.Apply(new EffectBatchV3(List.of(new ClaimAbsent(id("key"))),List.of(new Transition(id("a"),0,"RUNNING"))))));
        assertInstanceOf(DispatchDecisionV3.Ignore.class,next.dispatchNext().orElseThrow().decision());assertEquals("READY",next.snapshot().state().entities().get(id("a")).stateName());
    }
}
