package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record ProcessingStarted(String requestId, String componentId) implements SimulationEvent {
    public ProcessingStarted {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(componentId);
    }
}
