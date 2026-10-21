package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.transport.MessageTraceEntry;
import java.util.List;

/** Immutable execution output with separate event, message and state-transition histories. */
public record SimulationResult(long totalDurationMs, List<SimulationTraceEntry> trace,
                               List<MessageTraceEntry> messageTrace, ExecutionStateSnapshot initialState,
                               ExecutionStateSnapshot finalState, List<StateTransitionTrace> stateTransitions,
                               long apiWaitingDurationMs) {
    public SimulationResult {
        trace = List.copyOf(trace);
        messageTrace = List.copyOf(messageTrace);
        stateTransitions = List.copyOf(stateTransitions);
        if (totalDurationMs < 0 || apiWaitingDurationMs < 0)
            throw new IllegalArgumentException("Negative duration");
    }
}
