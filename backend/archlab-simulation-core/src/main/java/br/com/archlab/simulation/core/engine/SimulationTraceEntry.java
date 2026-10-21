package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.event.SimulationEvent;
import java.util.Objects;

public record SimulationTraceEntry(long timestampMs, long sequence, SimulationEvent event) {
    public SimulationTraceEntry { Objects.requireNonNull(event); }
}
