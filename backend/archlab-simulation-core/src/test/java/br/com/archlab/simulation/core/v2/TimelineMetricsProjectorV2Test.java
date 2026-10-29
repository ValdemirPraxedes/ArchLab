package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TimelineMetricsProjectorV2Test {
    private NestedExecutionResultV2 execute(boolean nested, long latency, long processing) {
        var components=new ArrayList<ComponentV2>();
        components.add(new ClientV2("client","Client"));
        components.add(new ApiV2("a","A",processing,processing));
        var connections=new ArrayList<ConnectionV2>();
        connections.add(new ConnectionV2("ca","client","a",latency,new HttpRestIntegrationV2(null,null)));
        var chosen=new ArrayList<ApiExecutionV2>();
        if (nested) {
            components.add(new ApiV2("b","B",processing,processing));
            components.add(new DatabaseV2("d","DB",processing));
            connections.add(new ConnectionV2("ab","a","b",latency,new HttpRestIntegrationV2(null,null)));
            connections.add(new ConnectionV2("bd","b","d",latency,new DatabaseQueryIntegrationV2(null,null)));
            chosen.add(new ApiExecutionV2("a","ab"));
            chosen.add(new ApiExecutionV2("b","bd"));
        }
        // Unused components must not contribute metrics.
        components.add(new ApiV2("inactive","Inactive",999,999));
        return new SimulationEngine().execute(new ArchitectureV2(components,connections),
                new ScenarioV2("client","ca",chosen));
    }

    @Test void nestedChainReconcilesExclusiveIntervalsWithoutAddingOverlappingWaits() {
        var r=execute(true,2,10);
        assertEquals(62,r.totalDurationMs()); // 5*10 processing + 6*2 communication
        assertEquals(6,r.timeline().stream().filter(x->x.kind().equals("COMMUNICATION")).count());
        assertEquals(5,r.timeline().stream().filter(x->x.kind().equals("PROCESSING")).count());
        assertEquals(62,r.timeline().stream().mapToLong(TimelineActivityV2::durationMs).sum());
        assertEquals(62,r.metrics().totalDurationMs());
        assertEquals(3,r.metrics().communicationByConnection().size());
        assertEquals(3,r.metrics().processingByComponent().size());
        assertEquals(2,r.metrics().waitingByComponent().size());
        assertEquals(3,r.metrics().exchanges().size());
        assertTrue(r.metrics().exchanges().get(0).durationMs()>r.metrics().exchanges().get(1).durationMs());
        assertTrue(r.metrics().waitingByComponent().stream().mapToLong(WaitingMetricV2::durationMs).sum()
                > r.metrics().waitingByComponent().get(1).durationMs());
        assertFalse(r.metrics().processingByComponent().stream().anyMatch(x->x.componentId().equals("inactive")));
        assertEquals(1,r.timeline().stream().filter(x->x instanceof ProcessingActivityV2 p
                && p.phase().equals("DATABASE_PROCESSING")).count());
    }

    @Test void directApiHasTwoProcessingPhasesAndTwoTransportTraversals() {
        var r=execute(false,3,5);
        assertEquals(16,r.totalDurationMs());
        assertEquals(4,r.timeline().size());
        assertEquals(0,r.metrics().waitingByComponent().size());
        assertEquals(2,r.metrics().communicationByConnection().getFirst().traversals());
        assertEquals(6,r.metrics().communicationByConnection().getFirst().durationMs());
        assertEquals(10,r.metrics().processingByComponent().getFirst().durationMs());
        assertTrue(r.timeline().stream().filter(x->x instanceof ProcessingActivityV2)
                .map(x->((ProcessingActivityV2)x).phase()).toList()
                .containsAll(List.of("API_PRE_PROCESSING","API_POST_PROCESSING")));
    }

    @Test void zeroDurationsPreserveActivitiesAndDeterministicProjection() {
        var first=execute(true,0,0);
        var second=execute(true,0,0);
        assertEquals(0,first.totalDurationMs());
        assertEquals(11,first.timeline().size());
        assertEquals(first.timeline(),second.timeline());
        assertEquals(first.metrics(),second.metrics());
        assertTrue(first.timeline().stream().allMatch(x->x.durationMs()==0));
    }

    @Test void tracksNestedInclusiveDurationAndExactWaitingExchanges() {
        var r=execute(true,2,10);
        var waits=r.metrics().waitingByComponent();
        assertEquals("a",waits.get(0).componentId());
        assertEquals(r.exchanges().get(1).exchangeId(),waits.get(0).exchangeId());
        assertEquals("b",waits.get(1).componentId());
        assertEquals(r.exchanges().get(2).exchangeId(),waits.get(1).exchangeId());
        for(var e:r.metrics().exchanges())
            assertEquals(e.endMs()-e.startMs(),e.durationMs());
        assertEquals(r.totalDurationMs(),r.metrics().exchanges().getFirst().durationMs());
    }

    @Test void historiesAndMetricListsAreImmutable() {
        var r=execute(true,1,2);
        assertThrows(UnsupportedOperationException.class,()->r.timeline().clear());
        assertThrows(UnsupportedOperationException.class,()->r.metrics().waitingByComponent().clear());
        assertThrows(UnsupportedOperationException.class,()->r.metrics().communicationByConnection().clear());
    }
}
