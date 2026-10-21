package br.com.archlab.simulation.core.transport;

import java.util.Objects;

/** Logical message. The connection protocol belongs to Connection, not to this record. */
public record SimulationMessage(String id, String requestId, String exchangeId, String sourceId,
                                String targetId, MessageDirection direction, MessageIntent intent) {
    public SimulationMessage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(exchangeId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(intent);
    }
}
