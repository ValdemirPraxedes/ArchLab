package br.com.archlab.simulation.core.transport;

import java.util.Objects;

public record MessageTraceEntry(long timestampMs, long sequence, Action action,
                                String connectionId, SimulationMessage message) {
    public enum Action { SENT, ARRIVED }
    public MessageTraceEntry {
        Objects.requireNonNull(action);
        Objects.requireNonNull(connectionId);
        Objects.requireNonNull(message);
    }
}
