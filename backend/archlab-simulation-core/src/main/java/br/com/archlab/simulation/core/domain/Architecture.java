package br.com.archlab.simulation.core.domain;

import java.util.List;
import java.util.Objects;

public record Architecture(List<Component> components, List<Connection> connections) {
    public Architecture {
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
    }
}
