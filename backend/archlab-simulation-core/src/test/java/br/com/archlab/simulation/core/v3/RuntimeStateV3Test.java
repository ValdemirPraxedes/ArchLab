package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.*;
import br.com.archlab.simulation.core.v3.RuntimeEntityV3.Timer;
class RuntimeStateV3Test {
    static IdentifierV3 id(String x){return new IdentifierV3(x);}
    @Test void lifecycleTransitionsAreVersionedAndTerminalStatesCannotReopen(){
        RuntimeEntityV3 a=new Activation(id("a"),id("c"),ActivationStatus.READY,null,0);
        var running=a.transition("RUNNING");var waiting=running.transition("WAITING");var done=waiting.transition("COMPLETED");
        assertEquals(0,a.version());assertEquals("READY",a.stateName());assertEquals(3,done.version());
        assertThrows(IllegalStateException.class,()->done.transition("RUNNING"));assertThrows(IllegalStateException.class,()->a.transition("COMPLETED"));
    }
    @Test void coversNativeLifecyclesAndQueueRedelivery(){
        var entities=List.<RuntimeEntityV3>of(new Interaction(id("i"),InteractionStatus.CREATED,List.of(),0),new Attempt(id("attempt"),id("i"),AttemptStatus.SCHEDULED,0),
                new ParallelGroup(id("p"),ParallelGroupStatus.CREATED,List.of(),0),new QueueEntry(id("q"),id("resource"),id("m"),QueueEntryStatus.READY,0),
                new Publication(id("pub"),id("m"),PublicationStatus.CREATED,0),new DeliveryAttempt(id("d"),id("q"),DeliveryAttemptStatus.SCHEDULED,0),
                new Timer(id("timer"),0,id("a"),DurationMsV3.ZERO,TimerStatus.SCHEDULED,"deadline"),new Lease(id("lease"),0,id("r"),id("claim"),LeaseStatus.HELD,1),
                new WaitClaim(id("claim"),0,id("r"),id("a"),0,WaitClaimStatus.WAITING),new WorkItem(id("w"),0,id("a"),"CALL",WorkItemStatus.PENDING,"GLOBAL"),
                new Component(id("c"),0,ComponentStatus.AVAILABLE),new Principal(id("root"),0,PrincipalStatus.IN_PROGRESS),new StepExecution(id("s"),0,StepExecutionStatus.READY));
        String[][] paths={{"IN_FLIGHT","AWAITING_RESULT","COMPLETED"},{"IN_FLIGHT","TIMED_OUT"},{"RUNNING","JOIN_SATISFIED","COMPLETED"},{"IN_DELIVERY","READY","IN_DELIVERY","ACKNOWLEDGED"},
                {"IN_TRANSIT","ACCEPTED"},{"IN_TRANSIT","DELIVERED","AWAITING_ACK","ACKNOWLEDGED"},{"CONSUMED"},{"RELEASED"},{"ACQUIRED"},{"RESOLVED"},{"UNAVAILABLE","AVAILABLE"},{"RESPONDED"},{"RUNNING","FAILED"}};
        for(int i=0;i<entities.size();i++){var entity=entities.get(i);for(var target:paths[i])entity=entity.transition(target);assertEquals(paths[i].length,entity.version());assertEquals(0,entities.get(i).version());}
    }
    @Test void staleVersionsAndIllegalReplacementsLeavePublishedStateUnchanged(){
        var c=new Component(id("c"),0,ComponentStatus.AVAILABLE);var initial=RuntimeStateV3.empty().create(c);
        var next=initial.transition(id("c"),0,"UNAVAILABLE");assertEquals("AVAILABLE",initial.entities().get(id("c")).stateName());assertEquals("UNAVAILABLE",next.entities().get(id("c")).stateName());
        assertThrows(IllegalStateException.class,()->next.transition(id("c"),0,"AVAILABLE"));assertThrows(IllegalStateException.class,()->initial.create(c));
        assertThrows(IllegalStateException.class,()->initial.replace(0,new Component(id("c"),2,ComponentStatus.UNAVAILABLE)));
        assertThrows(UnsupportedOperationException.class,()->initial.entities().clear());assertEquals(c,initial.entities().get(id("c")));
    }
    @Test void validatesResourceClaimsLeasesAndReferencesOnCandidateState(){
        var c=new Component(id("c"),0,ComponentStatus.AVAILABLE);var resource=new Resource(id("r"),"AVAILABLE",1L,0L,0);
        var claim=new WaitClaim(id("claim"),0,id("r"),id("c"),0,WaitClaimStatus.WAITING);
        var initial=RuntimeStateV3.empty().create(c).create(resource).create(claim);initial.requireConsistent();
        var candidate=initial.transition(id("claim"),0,"ACQUIRED").create(new Lease(id("lease"),0,id("r"),id("claim"),LeaseStatus.HELD,1));
        assertThrows(IllegalStateException.class,candidate::requireConsistent);
        var committed=candidate.replace(0,new Resource(id("r"),"EXHAUSTED",1L,1L,1));committed.requireConsistent();initial.requireConsistent();
        var corrupt=committed.create(new Lease(id("duplicate"),0,id("r"),id("claim"),LeaseStatus.HELD,1));assertThrows(IllegalStateException.class,corrupt::requireConsistent);
        assertThrows(IllegalArgumentException.class,()->new Resource(id("bad"),"AVAILABLE",1L,2L,0));
        var orphan=RuntimeStateV3.empty().create(new Activation(id("a"),id("missing"),ActivationStatus.READY,null,0));assertThrows(IllegalStateException.class,orphan::requireConsistent);
    }
    @Test void entityVersionsRejectOverflowAndSnapshotsCopyMutableInputs(){
        var c=new Component(id("c"),Long.MAX_VALUE,ComponentStatus.AVAILABLE);assertThrows(ArithmeticException.class,()->c.transition("UNAVAILABLE"));
        var ids=new ArrayList<IdentifierV3>();var interaction=new Interaction(id("i"),InteractionStatus.CREATED,ids,0);ids.add(id("later"));assertTrue(interaction.attemptIds().isEmpty());
        assertFalse(StateTransitionsV3.allowed(Kind.PRINCIPAL,"RESPONDED","IN_PROGRESS"));assertFalse(StateTransitionsV3.allowed(Kind.POLICY_STATE,"CLOSED","OPEN"));
    }
}
