package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationEngine;
import br.com.archlab.simulation.core.event.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NestedCallRuntimeV2Test {
    private final SimulationEngine engine = new SimulationEngine();
    private static ClientV2 client(String id) { return new ClientV2(id, id); }
    private static ApiV2 api(String id, long pre, long post) { return new ApiV2(id, id, pre, post); }
    private static DatabaseV2 db(String id, long time) { return new DatabaseV2(id, id, time); }
    private static ConnectionV2 http(String id, String source, String target, long latency) {
        return new ConnectionV2(id, source, target, latency, new HttpRestIntegrationV2(null, null));
    }
    private static ConnectionV2 query(String id, String source, String target, long latency) {
        return new ConnectionV2(id, source, target, latency, new DatabaseQueryIntegrationV2(null, null));
    }
    private static ScenarioV2 choose(ApiExecutionV2... configs) { return new ScenarioV2("c", "ca", List.of(configs)); }

    @Test void singleApiRespondsDirectlyWithPreAndPostProcessing() {
        var a = new ArchitectureV2(List.of(client("c"), api("a", 30, 20)),
                List.of(http("ca", "c", "a", 10)));
        var result = engine.execute(a, choose());
        assertEquals(70, result.totalDurationMs());
        assertEquals("request-1", result.requestId());
        assertEquals(1, result.events().stream().filter(e -> e.event() instanceof ResponseArrived).count());
    }

    @Test void apiToApiReturnsToItsCallerThenToClient() {
        var a = new ArchitectureV2(List.of(client("c"), api("a", 10, 5), api("b", 7, 3)),
                List.of(http("ca", "c", "a", 2), http("ab", "a", "b", 4)));
        var result = engine.execute(a, choose(new ApiExecutionV2("a", "ab")));
        assertEquals(2 * (2 + 4) + 10 + 5 + 7 + 3, result.totalDurationMs());
        assertTrue(result.events().stream().anyMatch(e -> e.event() instanceof ResponseArrived r
                && r.sourceId().equals("b") && r.targetId().equals("a")));
        assertTrue(result.events().stream().anyMatch(e -> e.event() instanceof ResponseArrived r
                && r.sourceId().equals("a") && r.targetId().equals("c")));
    }

    @Test void chainOfApisAndDatabaseUsesSynchronousNestedWaits() {
        var a = new ArchitectureV2(List.of(client("c"), api("a", 10, 20), api("b", 30, 40), db("d", 50)),
                List.of(http("ca", "c", "a", 2), http("ab", "a", "b", 3), query("bd", "b", "d", 4)));
        var result = engine.execute(a, choose(new ApiExecutionV2("a", "ab"), new ApiExecutionV2("b", "bd")));
        assertEquals(10 + 20 + 30 + 40 + 50 + 2 * (2 + 3 + 4), result.totalDurationMs());
        long last = -1;
        for (var entry : result.events()) {
            assertTrue(entry.timestampMs() >= last);
            last = entry.timestampMs();
        }
        assertEquals(3, result.events().stream().filter(e -> e.event() instanceof ResponseArrived).count());
    }

    @Test void allZeroDurationsCompleteDeterministically() {
        var a = new ArchitectureV2(List.of(client("c"), api("a", 0, 0), api("b", 0, 0)),
                List.of(http("ca", "c", "a", 0), http("ab", "a", "b", 0)));
        var scenario = choose(new ApiExecutionV2("a", "ab"));
        var first = engine.execute(a, scenario);
        var second = engine.execute(a, scenario);
        assertEquals(0, first.totalDurationMs());
        assertEquals(first.events(), second.events());
        assertTrue(first.events().stream().allMatch(e -> e.timestampMs() == 0));
    }

    @Test void ignoresInactiveComponentsAndUnselectedDependencies() {
        var a = new ArchitectureV2(List.of(client("c"), client("other"), api("a", 1, 1),
                        api("inactive", 900, 900), db("d", 900)),
                List.of(http("ca", "c", "a", 1), query("id", "inactive", "d", 5)));
        var result = engine.execute(a, choose(new ApiExecutionV2("inactive", "id")));
        assertEquals(4, result.totalDurationMs());
        assertFalse(result.events().stream().anyMatch(e -> e.event() instanceof ProcessingStarted p
                && p.componentId().equals("inactive")));
    }

    @Test void rejectsInvalidArchitectureOrScenarioBeforeExecution() {
        var a = new ArchitectureV2(List.of(client("c"), api("a", 1, 1)), List.of(http("ca", "c", "a", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> engine.execute(a, choose(new ApiExecutionV2("a", "unknown"))));
    }
}
