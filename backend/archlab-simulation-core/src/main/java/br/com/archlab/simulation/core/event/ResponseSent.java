package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record ResponseSent(String requestId, String sourceId, String targetId) implements SimulationEvent {
    public ResponseSent {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
    }
}
