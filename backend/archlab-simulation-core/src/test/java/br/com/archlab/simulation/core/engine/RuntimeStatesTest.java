package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeStatesTest {
    private static Architecture architecture(long latencyClient, long latencyDb,
                                             long before, long after, long database) {
        return new Architecture(List.of(new ClientComponent("client"), new ApiComponent("api", before, after),
                new DatabaseComponent("db", database)),
                List.of(new Connection("c1", "client", "api", Protocol.REST, latencyClient),
                        new Connection("c2", "api", "db", Protocol.TCP, latencyDb)));
    }

    @Test void exactWaitingWindowAndSeparatedFinalStates() {
        SimulationResult result = new SimulationEngine().execute(architecture(20, 10, 30, 20, 150));
        assertEquals(260, result.totalDurationMs());
        assertEquals(170, result.apiWaitingDurationMs());
        assertEquals(Map.of("client", ComponentRuntimeState.COMPLETED,
                "api", ComponentRuntimeState.COMPLETED, "db", ComponentRuntimeState.COMPLETED),
                result.finalState().components());
        assertEquals(Map.of("request-1", RequestRuntimeState.COMPLETED), result.finalState().requests());
        assertEquals(Map.of("exchange-api-1", ExchangeRuntimeState.COMPLETED,
                "exchange-db-1", ExchangeRuntimeState.COMPLETED), result.finalState().exchanges());
        assertEquals(ComponentRuntimeState.IDLE, result.initialState().components().get("api"));
        assertTrue(result.initialState().requests().isEmpty());
        assertTrue(result.initialState().exchanges().isEmpty());
    }

    @Test void tracesEveryTransitionWithPreviousStateAndCause() {
        SimulationResult result = new SimulationEngine().execute(architecture(20, 10, 30, 20, 150));
        List<StateTransitionTrace> transitions = result.stateTransitions();
        assertFalse(transitions.isEmpty());
        for (int i = 0; i < transitions.size(); i++) {
            assertEquals(i, transitions.get(i).sequence());
            assertTrue(transitions.get(i).timestampMs() >= 0);
            if (i > 0) assertTrue(transitions.get(i).timestampMs() >= transitions.get(i - 1).timestampMs());
            assertNotEquals(transitions.get(i).previousState(), transitions.get(i).newState());
            assertFalse(transitions.get(i).cause().isBlank());
        }
        assertTrue(transitions.stream().anyMatch(t -> t.entityId().equals("api")
                && t.previousState().equals("PROCESSING") && t.newState().equals("WAITING")
                && t.timestampMs() == 50));
        assertTrue(transitions.stream().anyMatch(t -> t.entityId().equals("api")
                && t.previousState().equals("WAITING") && t.newState().equals("PROCESSING")
                && t.timestampMs() == 220));
    }

    @Test void zeroDurationRemainsDeterministicAndSnapshotsImmutable() {
        Architecture architecture = architecture(0, 0, 0, 0, 0);
        SimulationResult a = new SimulationEngine().execute(architecture);
        SimulationResult b = new SimulationEngine().execute(architecture);
        assertEquals(a, b);
        assertEquals(0, a.apiWaitingDurationMs());
        assertThrows(UnsupportedOperationException.class,
                () -> a.finalState().components().put("new", ComponentRuntimeState.IDLE));
        assertThrows(UnsupportedOperationException.class, () -> a.stateTransitions().clear());
    }

    @Test void rejectsInvalidStateTransitions() {
        ExecutionState state = new ExecutionState(architecture(0, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class, () ->
                state.component("api", ComponentRuntimeState.WAITING, ComponentRuntimeState.COMPLETED, 0, "wrong"));
        assertThrows(IllegalStateException.class, () ->
                state.request("unknown", RequestRuntimeState.CREATED, RequestRuntimeState.IN_PROGRESS, 0, "wrong"));
        state.createRequest("r");
        assertThrows(IllegalStateException.class, () -> state.createRequest("r"));
        state.request("r", RequestRuntimeState.CREATED, RequestRuntimeState.IN_PROGRESS, 0, "started");
        assertThrows(IllegalStateException.class, () ->
                state.request("r", RequestRuntimeState.CREATED, RequestRuntimeState.COMPLETED, 0, "wrong"));
    }

    @Test void executionsDoNotShareState() {
        SimulationEngine engine = new SimulationEngine();
        SimulationResult first = engine.execute(architecture(20, 10, 30, 20, 150));
        SimulationResult second = engine.execute(architecture(1, 1, 1, 1, 1));
        assertEquals(260, first.totalDurationMs());
        assertEquals(7, second.totalDurationMs());
        assertEquals(3, first.finalState().components().size());
        assertEquals(3, second.finalState().components().size());
        assertNotSame(first.finalState(), second.finalState());
    }
}
