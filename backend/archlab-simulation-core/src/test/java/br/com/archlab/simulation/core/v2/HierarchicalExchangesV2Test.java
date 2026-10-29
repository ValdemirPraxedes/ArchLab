package br.com.archlab.simulation.core.v2;
import br.com.archlab.simulation.core.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HierarchicalExchangesV2Test {
    private NestedExecutionResultV2 run(long latency) {
        var components = List.<ComponentV2>of(new ClientV2("c", "C"), new ApiV2("a", "A", 10, 5),
                new ApiV2("b", "B", 7, 3), new DatabaseV2("d", "D", 20));
        var connections = List.of(
                new ConnectionV2("ca", "c", "a", latency, new HttpRestIntegrationV2(null, null)),
                new ConnectionV2("ab", "a", "b", latency, new HttpRestIntegrationV2(null, null)),
                new ConnectionV2("bd", "b", "d", latency, new DatabaseQueryIntegrationV2(null, null)));
        var scenario = new ScenarioV2("c", "ca", List.of(
                new ApiExecutionV2("a", "ab"), new ApiExecutionV2("b", "bd")));
        return new SimulationEngine().execute(new ArchitectureV2(components, connections), scenario);
    }
    @Test void parentIdsReflectCallHierarchyAndCompleteInReverseOrder() {
        var r = run(2); assertEquals(3, r.exchanges().size());
        assertNull(r.exchanges().get(0).parentExchangeId());
        assertEquals(r.exchanges().get(0).exchangeId(), r.exchanges().get(1).parentExchangeId());
        assertEquals(r.exchanges().get(1).exchangeId(), r.exchanges().get(2).parentExchangeId());
        assertTrue(r.exchanges().get(2).completedAtMs() <= r.exchanges().get(1).completedAtMs());
        assertTrue(r.exchanges().get(1).completedAtMs() <= r.exchanges().get(0).completedAtMs());
        assertEquals(r.totalDurationMs(), r.exchanges().get(0).completedAtMs());
    }
    @Test void messagesCorrelateTwoDirectionsWithSentAndArrival() {
        var r=run(2); assertEquals(12, r.messages().size());
        for (var e:r.exchanges()) {
            var m=r.messages().stream().filter(x->x.exchangeId().equals(e.exchangeId())).toList();
            assertEquals(4, m.size());
            assertEquals(2, m.stream().map(CorrelatedMessageV2::messageId).distinct().count());
        }
    }
    @Test void nestedApisWaitAndResumeIndependently() {
        var r=run(2);
        for (var id:List.of("a","b")) {
            assertTrue(r.componentTransitions().stream().anyMatch(x->x.componentId().equals(id)
                    && x.from()==ComponentRuntimeStateV2.PROCESSING && x.to()==ComponentRuntimeStateV2.WAITING));
            assertTrue(r.componentTransitions().stream().anyMatch(x->x.componentId().equals(id)
                    && x.from()==ComponentRuntimeStateV2.WAITING && x.to()==ComponentRuntimeStateV2.PROCESSING));
        }
    }
    @Test void rerunsAreDeterministicAndCollectionsImmutable() {
        var first=run(0); var second=run(0);
        assertEquals(first.exchanges(),second.exchanges());
        assertEquals(first.messages(),second.messages());
        assertThrows(UnsupportedOperationException.class,()->first.exchanges().clear());
        assertThrows(UnsupportedOperationException.class,()->first.messages().clear());
    }
}
