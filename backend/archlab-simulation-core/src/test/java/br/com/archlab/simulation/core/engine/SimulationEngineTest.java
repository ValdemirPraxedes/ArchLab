package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.validation.InvalidArchitectureException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {
    private final SimulationEngine engine = new SimulationEngine();

    private Architecture architecture(long clientLatency, long dbLatency, long pre, long post, long processing) {
        return new Architecture(
                List.of(new ClientComponent("client"), new ApiComponent("api", pre, post),
                        new DatabaseComponent("db", processing)),
                List.of(new Connection("c1", "client", "api", Protocol.REST, clientLatency),
                        new Connection("c2", "api", "db", Protocol.TCP, dbLatency)));
    }

    @Test void producesDeterministic260msScenario() {
        Architecture a = architecture(20, 10, 30, 20, 150);
        SimulationResult first = engine.execute(a);
        assertEquals(260, first.totalDurationMs());
        assertEquals(first, engine.execute(a));
        assertTrue(first.trace().getFirst().event() instanceof RequestSent);
        assertTrue(first.trace().getLast().event() instanceof SimulationCompleted);
    }

    @Test void zeroDurations() {
        SimulationResult result = engine.execute(architecture(0, 0, 0, 0, 0));
        assertEquals(0, result.totalDurationMs());
        assertTrue(result.trace().stream().allMatch(e -> e.timestampMs() == 0));
    }

    @Test void rejectsInvalidArchitecture() {
        assertThrows(InvalidArchitectureException.class,
                () -> engine.execute(architecture(20, 10, -1, 20, 150)));
    }

    @Test void executionIsIsolated() {
        assertEquals(260, engine.execute(architecture(20, 10, 30, 20, 150)).totalDurationMs());
        assertEquals(7, engine.execute(architecture(1, 1, 1, 1, 1)).totalDurationMs());
    }

    @Test void rejectsOverflow() {
        assertThrows(ArithmeticException.class,
                () -> engine.execute(architecture(Long.MAX_VALUE, 1, 1, 0, 0)));
    }
}
