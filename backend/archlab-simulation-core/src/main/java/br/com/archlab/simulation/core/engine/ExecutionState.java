package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.Architecture;
import java.util.*;

/** All runtime states are local to one simulation execution. */
final class ExecutionState {
    private final Map<String, ComponentRuntimeState> components = new LinkedHashMap<>();
    private final Map<String, RequestRuntimeState> requests = new LinkedHashMap<>();
    private final Map<String, ExchangeRuntimeState> exchanges = new LinkedHashMap<>();
    private final List<StateTransitionTrace> transitions = new ArrayList<>();
    private long nextSequence;

    ExecutionState(Architecture architecture) {
        architecture.components().forEach(c -> components.put(c.id(), ComponentRuntimeState.IDLE));
    }

    void createRequest(String id) {
        if (requests.putIfAbsent(id, RequestRuntimeState.CREATED) != null)
            throw new IllegalStateException("Request already exists: " + id);
    }

    void createExchange(String id) {
        if (exchanges.putIfAbsent(id, ExchangeRuntimeState.CREATED) != null)
            throw new IllegalStateException("Exchange already exists: " + id);
    }

    ComponentRuntimeState component(String id) { return required(components, id); }
    RequestRuntimeState request(String id) { return required(requests, id); }
    ExchangeRuntimeState exchange(String id) { return required(exchanges, id); }

    void component(String id, ComponentRuntimeState expected, ComponentRuntimeState next, long time, String cause) {
        transition(components, StateTransitionTrace.EntityType.COMPONENT, id, expected, next, time, cause);
    }

    void request(String id, RequestRuntimeState expected, RequestRuntimeState next, long time, String cause) {
        transition(requests, StateTransitionTrace.EntityType.REQUEST, id, expected, next, time, cause);
    }

    void exchange(String id, ExchangeRuntimeState expected, ExchangeRuntimeState next, long time, String cause) {
        transition(exchanges, StateTransitionTrace.EntityType.EXCHANGE, id, expected, next, time, cause);
    }

    private static <T> T required(Map<String, T> map, String id) {
        T value = map.get(id);
        if (value == null) throw new IllegalStateException("Unknown runtime entity: " + id);
        return value;
    }

    private <T> void transition(Map<String, T> map, StateTransitionTrace.EntityType type,
                                String id, T expected, T next, long time, String cause) {
        T current = required(map, id);
        if (current != expected || expected == next) {
            throw new IllegalStateException("Invalid transition for " + id + ": " + current + " -> " + next);
        }
        if (nextSequence == Long.MAX_VALUE) throw new ArithmeticException("State transition sequence overflow");
        map.put(id, next);
        transitions.add(new StateTransitionTrace(time, nextSequence++, type, id, current.toString(), next.toString(), cause));
    }

    ExecutionStateSnapshot snapshot() { return new ExecutionStateSnapshot(components, requests, exchanges); }
    List<StateTransitionTrace> trace() { return List.copyOf(transitions); }
}
