package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationTraceEntry;
import java.util.List;

/** BE-05 event trace; hierarchical exchange/state accounting is introduced by BE-06. */
public record NestedExecutionResultV2(long totalDurationMs, String requestId,
                                      List<SimulationTraceEntry> events) {
    public NestedExecutionResultV2 {
        if (totalDurationMs < 0) throw new IllegalArgumentException("Negative time");
        events = List.copyOf(events);
    }
}
