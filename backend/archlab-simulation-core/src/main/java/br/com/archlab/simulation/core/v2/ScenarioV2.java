package br.com.archlab.simulation.core.v2;

import java.util.List;
import java.util.Objects;

/**
 * Immutable execution selection, separate from the full architecture.
 * May contain semantic mistakes that are reported by ScenarioValidatorV2.
 */
public record ScenarioV2(String entryComponentId, String entryConnectionId,
                         List<ApiExecutionV2> apiExecutions) {
    public ScenarioV2 {
        apiExecutions = List.copyOf(Objects.requireNonNull(apiExecutions, "apiExecutions"));
    }
}
