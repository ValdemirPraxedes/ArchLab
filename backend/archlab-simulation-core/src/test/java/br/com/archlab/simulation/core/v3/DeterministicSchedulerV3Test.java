package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.archlab.simulation.core.v3.DeterministicSchedulerV3.*;
class DeterministicSchedulerV3Test {
    @Test void ordersFivePhasesAndStableSequenceAtEqualTimestamp(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(5,Phase.TERMINATION_CHECK,"termination");s.schedule(5,Phase.DEADLINE,"deadline");
        s.schedule(5,Phase.CONTINUATION,"continuation");s.schedule(5,Phase.DELIVERY_OR_COMPLETION,"delivery1");s.schedule(5,Phase.DELIVERY_OR_COMPLETION,"delivery2");s.schedule(5,Phase.ENVIRONMENT_CHANGE,"environment");
        var result=new ArrayList<String>();assertEquals(Stop.DRAINED,s.run(100,null,e->result.add(e.payload())).stop());
        assertEquals(List.of("environment","delivery1","delivery2","continuation","deadline","termination"),result);assertEquals(5,s.nowMs());
    }
    @Test void zeroDurationCausalChildrenRunInNextRoundBeforeDeadlines(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(0,Phase.CONTINUATION,"parent");s.schedule(0,Phase.CONTINUATION,"peer");s.schedule(0,Phase.DEADLINE,"deadline");
        var events=new ArrayList<Event<String>>();s.run(100,null,e->{events.add(e);if(e.payload().equals("parent"))s.schedule(0,Phase.DELIVERY_OR_COMPLETION,"child");});
        assertEquals(List.of("parent","peer","child","deadline"),events.stream().map(Event::payload).toList());assertEquals(1,events.get(2).round());
        assertEquals(events.getFirst().eventId(),events.get(2).causationEventId());assertEquals(Epoch.BEFORE_DEADLINE,events.get(2).causalEpoch());
    }
    @Test void deadlinesCannotBeUndoneByZeroLatencyEffects(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(10,Phase.DEADLINE,"deadline1");s.schedule(10,Phase.DEADLINE,"deadline2");s.schedule(10,Phase.TERMINATION_CHECK,"termination");
        var events=new ArrayList<Event<String>>();s.run(100,null,e->{events.add(e);if(e.payload().equals("deadline1"))s.schedule(10,Phase.DELIVERY_OR_COMPLETION,"effect");});
        assertEquals(List.of("deadline1","deadline2","effect","termination"),events.stream().map(Event::payload).toList());assertEquals(Epoch.AFTER_DEADLINE,events.get(2).causalEpoch());assertEquals(Stage.AFTER_DEADLINES,events.get(2).stage());
    }
    @Test void nestedAfterDeadlineCausalityKeepsRoundAndEpoch(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(0,Phase.DEADLINE,"deadline");var events=new ArrayList<Event<String>>();
        s.run(100,null,e->{events.add(e);switch(e.payload()){case "deadline"->s.schedule(0,Phase.CONTINUATION,"continuation");case "continuation"->s.schedule(0,Phase.DELIVERY_OR_COMPLETION,"delivery");default->{}}});
        assertEquals(1,events.get(2).round());assertEquals(Epoch.AFTER_DEADLINE,events.get(2).causalEpoch());
    }
    @Test void horizonIsInclusiveAndRetainsFutureWorkForResume(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(5,Phase.CONTINUATION,"now");s.schedule(11,Phase.CONTINUATION,"future");var results=new ArrayList<String>();
        var stopped=s.run(100,5L,e->results.add(e.payload()));assertEquals(Stop.HORIZON,stopped.stop());assertEquals(List.of("now"),results);assertEquals(5,s.nowMs());assertEquals(1,s.pendingCount());
        assertEquals(Stop.DRAINED,s.run(100,null,e->results.add(e.payload())).stop());assertEquals(List.of("now","future"),results);assertEquals(11,s.nowMs());
    }
    @Test void horizonWithoutEventDoesNotInheritOldStage(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(1,Phase.TERMINATION_CHECK,"old");s.schedule(20,Phase.CONTINUATION,"future");s.run(10,10L,e->{});
        assertEquals(10,s.nowMs());assertDoesNotThrow(()->s.schedule(10,Phase.ENVIRONMENT_CHANGE,"new tick"));
    }
    @Test void limitStopsZeroDurationCycleWithoutDroppingPendingWork(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(0,Phase.CONTINUATION,"loop");
        var r=s.run(7,null,e->s.schedule(0,Phase.CONTINUATION,"loop"));assertEquals(Stop.EVENT_LIMIT,r.stop());assertEquals(7,r.dispatched());assertEquals(1,s.pendingCount());assertEquals(0,s.nowMs());
    }
    @Test void snapshotRestoreReproducesFutureEventsIncludingCountersAndCausality(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(0,Phase.CONTINUATION,"start");s.schedule(5,Phase.DEADLINE,"later");s.poll();s.schedule(0,Phase.DELIVERY_OR_COMPLETION,"child");
        var snapshot=s.snapshot();var restored=DeterministicSchedulerV3.restore(snapshot);assertEquals(snapshot,restored.snapshot());
        var one=new ArrayList<Event<String>>();var two=new ArrayList<Event<String>>();s.run(100,null,one::add);restored.run(100,null,two::add);assertEquals(one,two);
        assertEquals(s.schedule(6,Phase.CONTINUATION,"next"),restored.schedule(6,Phase.CONTINUATION,"next"));assertThrows(UnsupportedOperationException.class,()->snapshot.pending().clear());
    }
    @Test void invalidSchedulingAndCounterOverflowDoNotMutateAgenda(){
        var s=new DeterministicSchedulerV3<String>();s.schedule(1,Phase.CONTINUATION,"x");s.poll();var before=s.snapshot();
        assertThrows(IllegalArgumentException.class,()->s.schedule(0,Phase.CONTINUATION,"past"));assertThrows(IllegalArgumentException.class,()->s.schedule(1,Phase.ENVIRONMENT_CHANGE,"late environment"));assertEquals(before,s.snapshot());
        var overflow=DeterministicSchedulerV3.restore(new Snapshot<String>(0,Long.MAX_VALUE,null,List.of()));assertThrows(ArithmeticException.class,()->overflow.schedule(0,Phase.CONTINUATION,"x"));assertEquals(0,overflow.pendingCount());
        s.schedule(1,Phase.TERMINATION_CHECK,"end");s.poll();before=s.snapshot();assertThrows(IllegalArgumentException.class,()->s.schedule(1,Phase.CONTINUATION,"late"));assertEquals(before,s.snapshot());
    }
    @Test void rejectsCorruptSnapshotsAndNegativeInputs(){
        var s=new DeterministicSchedulerV3<String>();var e=s.schedule(0,Phase.CONTINUATION,"x");
        assertThrows(IllegalArgumentException.class,()->DeterministicSchedulerV3.restore(new Snapshot<>(0,1,null,List.of(e,e))));
        assertThrows(IllegalArgumentException.class,()->DeterministicSchedulerV3.restore(new Snapshot<>(1,1,null,List.of(e))));
        assertThrows(IllegalArgumentException.class,()->s.schedule(-1,Phase.CONTINUATION,"x"));assertThrows(IllegalArgumentException.class,()->s.run(0,null,x->{}));
        assertThrows(IllegalArgumentException.class,()->new Event<>(new IdentifierV3("x"),0,Phase.DEADLINE,0,Epoch.BEFORE_DEADLINE,Stage.ENVIRONMENT,0,null,"x"));
    }
    @Test void sameSeedAndRestartProduceIdenticalRandomStream(){
        var a=new DeterministicRandomV3(42);var b=new DeterministicRandomV3(42);
        for(int i=0;i<100;i++)assertEquals(a.nextLong(),b.nextLong());
        var restart=new DeterministicRandomV3(a.state());for(int i=0;i<1000;i++){var x=a.nextLong(17);assertEquals(x,restart.nextLong(17));assertTrue(x>=0 && x<17);}
        assertThrows(IllegalArgumentException.class,()->a.nextLong(0));
        assertNotEquals(new DeterministicRandomV3(42).nextLong(),new DeterministicRandomV3(43).nextLong());
    }
}
