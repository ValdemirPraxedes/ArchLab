package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record ResponseArrived(String requestId, String sourceId, String targetId) implements SimulationEvent {
    public ResponseArrived {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
    }
}
