package br.com.archlab.simulation.core.v2;
import br.com.archlab.simulation.core.engine.SimulationTraceEntry;
import java.util.List;
/** Immutable per-execution histories. */
public record NestedExecutionResultV2(long totalDurationMs, String requestId,
        List<SimulationTraceEntry> events, List<ExchangeTraceV2> exchanges,
        List<CorrelatedMessageV2> messages, List<ComponentTransitionV2> componentTransitions) {
    public NestedExecutionResultV2 {
        if (totalDurationMs < 0) throw new IllegalArgumentException("Negative duration");
        events = List.copyOf(events); exchanges = List.copyOf(exchanges);
        messages = List.copyOf(messages); componentTransitions = List.copyOf(componentTransitions);
    }
    public NestedExecutionResultV2(long totalDurationMs, String requestId, List<SimulationTraceEntry> events) {
        this(totalDurationMs, requestId, events, List.of(), List.of(), List.of());
    }
}
