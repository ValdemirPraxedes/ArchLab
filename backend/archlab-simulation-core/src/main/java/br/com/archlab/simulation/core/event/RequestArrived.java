package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record RequestArrived(String requestId, String sourceId, String targetId) implements SimulationEvent {
    public RequestArrived {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
    }
}
