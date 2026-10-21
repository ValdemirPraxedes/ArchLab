package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record ProcessingCompleted(String requestId, String componentId) implements SimulationEvent {
    public ProcessingCompleted {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(componentId);
    }
}
