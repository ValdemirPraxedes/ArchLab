package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record RequestSent(String requestId, String sourceId, String targetId) implements SimulationEvent {
    public RequestSent {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
    }
}
