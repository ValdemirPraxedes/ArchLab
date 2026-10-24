package br.com.archlab.simulation.core.v2;

import java.util.List;
import java.util.Objects;

/**
 * Candidate graph snapshot. It may contain duplicated IDs, disconnected nodes or other
 * violations: graph validation is deferred to BE-03. Its collections cannot be mutated.
 */
public record ArchitectureV2(List<ComponentV2> components, List<ConnectionV2> connections) {
    public ArchitectureV2 {
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
    }
}
