package br.com.archlab.simulation.core.engine;

import java.util.List;

public record SimulationResult(long totalDurationMs, List<SimulationTraceEntry> trace) {
    public SimulationResult { trace = List.copyOf(trace); }
}
