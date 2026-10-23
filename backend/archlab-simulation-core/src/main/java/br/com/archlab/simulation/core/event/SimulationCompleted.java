package br.com.archlab.simulation.core.event;

import java.util.Objects;

public record SimulationCompleted(String requestId) implements SimulationEvent {
    public SimulationCompleted {
        Objects.requireNonNull(requestId);
    }
}
