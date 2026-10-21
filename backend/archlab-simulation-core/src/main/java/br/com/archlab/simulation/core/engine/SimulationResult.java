package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.transport.MessageTraceEntry;
import java.util.List;

public record SimulationResult(long totalDurationMs, List<SimulationTraceEntry> trace,
                               List<MessageTraceEntry> messageTrace) {
    public SimulationResult {
        trace = List.copyOf(trace);
        messageTrace = List.copyOf(messageTrace);
    }
}
